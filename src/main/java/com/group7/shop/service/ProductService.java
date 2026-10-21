package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.ProductHistoryView;
import com.group7.shop.dto.ProductView;
import com.group7.shop.dto.PublishProductRequest;
import com.group7.shop.entity.FrozenBy;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductImage;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.ProductImageRepository;
import com.group7.shop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ProductService {
    /** 单件商品最多图片数。 */
    private static final int MAX_IMAGES = 9;

    private static final List<ProductStatus> ACTIVE_STATUSES =
            List.of(ProductStatus.ON_SALE, ProductStatus.FROZEN);

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductImageStorage imageStorage;
    private final ProductLifecycleListener lifecycleListener;
    private final CategoryService categoryService;
    private final RichTextSanitizer sanitizer;

    public ProductService(ProductRepository productRepository,
                          ProductImageRepository imageRepository,
                          ProductImageStorage imageStorage,
                          ProductLifecycleListener lifecycleListener,
                          CategoryService categoryService,
                          RichTextSanitizer sanitizer) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.imageStorage = imageStorage;
        this.lifecycleListener = lifecycleListener;
        this.categoryService = categoryService;
        this.sanitizer = sanitizer;
    }

    /** 过渡接口：多商品后由列表接口取代，这里返回最新一件在售商品。 */
    @Transactional(readOnly = true)
    public ProductView currentBuyerProduct() {
        return productRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_STATUSES)
                .stream()
                .findFirst()
                .map(this::toView)
                .orElse(null);
    }

    /**
     * 迭代 2：允许同时发布多个商品，不再限制「同一时间仅一件在售」。
     * 类别必须是二级类别；库存不能为负；描述按白名单过滤。
     */
    @Transactional
    public ProductView publish(PublishProductRequest request) {
        categoryService.requireSecondLevel(request.categoryId());

        int stock = request.stock() == null ? 0 : request.stock();
        if (stock < 0) {
            throw BusinessException.badRequest("库存不能为负数");
        }

        List<String> inputs = new ArrayList<>();
        if (request.images() != null) {
            inputs.addAll(request.images());
        }
        if (inputs.isEmpty() && request.image() != null && !request.image().isBlank()) {
            inputs.add(request.image());
        }
        if (inputs.size() > MAX_IMAGES) {
            throw BusinessException.badRequest("商品图片最多 " + MAX_IMAGES + " 张");
        }

        List<String> urls = new ArrayList<>();
        for (String input : inputs) {
            urls.add(imageStorage.storeOrPlaceholder(input));
        }
        if (urls.isEmpty()) {
            urls.add("/placeholder.png");
        }

        Product product = new Product(
                request.name().trim(),
                sanitizer.clean(request.description()),
                request.price(),
                urls.get(0),
                request.categoryId(),
                stock
        );
        product.setStatus(ProductStatus.ON_SALE);
        Product saved = productRepository.save(product);
        for (int i = 0; i < urls.size(); i++) {
            imageRepository.save(new ProductImage(saved.getId(), urls.get(i), i));
        }
        return ProductView.from(saved, urls);
    }

    @Transactional(readOnly = true)
    public List<ProductView> sellerProducts() {
        return productRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public ProductView freezeManually(Long id) {
        Product product = getForUpdate(id);
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw BusinessException.conflict("仅在售商品可以手动冻结");
        }
        product.freezeManually();
        return ProductView.from(productRepository.save(product), imageUrls(product.getId()));
    }

    @Transactional
    public ProductView unfreezeManually(Long id) {
        Product product = getForUpdate(id);
        if (product.getStatus() != ProductStatus.FROZEN) {
            throw BusinessException.conflict("仅已冻结商品可以解冻");
        }
        if (product.getFrozenBy() == FrozenBy.AUTO) {
            throw BusinessException.badRequest("交易中的商品不能手动解冻");
        }
        if (product.getFrozenBy() != FrozenBy.MANUAL) {
            throw BusinessException.conflict("商品冻结来源不正确，无法手动解冻");
        }
        if (product.getStock() <= 0) {
            throw BusinessException.badRequest("库存为 0，请先补货再解冻");
        }
        product.unfreezeManually();
        return ProductView.from(productRepository.save(product), imageUrls(product.getId()));
    }

    /**
     * 修改库存（唯一入口，规则域负责人：华天遥）。
     * 交易冻结（AUTO）期间拒绝；在售商品库存改为 0 时自动下架，并联动作废剩余排队意向。
     */
    @Transactional
    public ProductView updateStock(Long id, int stock) {
        if (stock < 0) {
            throw BusinessException.badRequest("库存不能为负数");
        }
        Product product = getForUpdate(id);
        if (product.getStatus() == ProductStatus.FROZEN && product.getFrozenBy() == FrozenBy.AUTO) {
            throw BusinessException.badRequest("交易中不可修改库存，请先标记交易结果");
        }
        product.changeStock(stock);
        if (product.getStatus() == ProductStatus.ON_SALE && stock == 0) {
            product.closeOutOfStock();
            Product saved = productRepository.save(product);
            lifecycleListener.onProductOffShelf(saved.getId());
            return ProductView.from(saved);
        }
        return ProductView.from(productRepository.save(product));
    }

    /**
     * 重新上架：仅已下架商品可用，库存为 0 时拒绝（规则域负责人：华天遥）。
     * 重新上架不改变库存，也不恢复历史意向。
     */
    @Transactional
    public ProductView reshelf(Long id) {
        Product product = getForUpdate(id);
        if (product.getStatus() != ProductStatus.OFF_SHELF) {
            throw BusinessException.conflict("仅已下架商品可以重新上架");
        }
        if (product.getStock() <= 0) {
            throw BusinessException.badRequest("库存为 0，请先补货再上架");
        }
        product.reopen();
        return ProductView.from(productRepository.save(product));
    }

    @Transactional
    public ProductView offshelf(Long id) {
        Product product = getForUpdate(id);
        if (product.getStatus() == ProductStatus.FROZEN && product.getFrozenBy() == FrozenBy.AUTO) {
            throw BusinessException.badRequest("交易中须先标记结果");
        }
        if (product.getStatus() == ProductStatus.OFF_SHELF) {
            throw BusinessException.conflict("商品已下架");
        }
        product.closeManually();
        Product saved = productRepository.save(product);
        lifecycleListener.onProductOffShelf(saved.getId());
        return ProductView.from(saved, imageUrls(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<ProductHistoryView> history() {
        return productRepository.findHistoryProducts().stream()
                .map(ProductHistoryView::from)
                .sorted(Comparator.comparing(
                        ProductHistoryView::historyAt,
                        Comparator.nullsLast(Comparator.<Instant>naturalOrder())
                ).reversed())
                .toList();
    }

    private ProductView toView(Product product) {
        return ProductView.from(product, imageUrls(product.getId()));
    }

    private List<String> imageUrls(Long productId) {
        return imageRepository.findByProductIdOrderBySortAscIdAsc(productId)
                .stream()
                .map(ProductImage::getUrl)
                .toList();
    }

    private Product getForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
    }
}
