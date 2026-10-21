package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.ProductView;
import com.group7.shop.dto.ProductHistoryView;
import com.group7.shop.dto.PublishProductRequest;
import com.group7.shop.entity.FrozenBy;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class ProductService {
    private static final List<ProductStatus> ACTIVE_STATUSES =
            List.of(ProductStatus.ON_SALE, ProductStatus.FROZEN);

    private final ProductRepository productRepository;
    private final ProductImageStorage imageStorage;
    private final ProductLifecycleListener lifecycleListener;

    public ProductService(ProductRepository productRepository,
                          ProductImageStorage imageStorage,
                          ProductLifecycleListener lifecycleListener) {
        this.productRepository = productRepository;
        this.imageStorage = imageStorage;
        this.lifecycleListener = lifecycleListener;
    }

    @Transactional(readOnly = true)
    public ProductView currentBuyerProduct() {
        return productRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_STATUSES)
                .stream()
                .findFirst()
                .map(ProductView::from)
                .orElse(null);
    }

    /** 发布商品：挑战需求 A 起允许多商品并存（每件商品独立管理状态，规则域负责人：华天遥）。 */
    @Transactional
    public ProductView publish(PublishProductRequest request) {
        String imageUrl = imageStorage.storeOrPlaceholder(request.image());
        int stock = request.stock() == null ? 0 : request.stock();
        Product product = new Product(
                request.name().trim(),
                request.description() == null ? "" : request.description().trim(),
                request.price(),
                imageUrl,
                stock
        );
        product.setStatus(ProductStatus.ON_SALE);
        return ProductView.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductView> sellerProducts() {
        return productRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(ProductView::from)
                .toList();
    }

    @Transactional
    public ProductView freezeManually(Long id) {
        Product product = getForUpdate(id);
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw BusinessException.conflict("仅在售商品可以手动冻结");
        }
        product.freezeManually();
        return ProductView.from(productRepository.save(product));
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
        return ProductView.from(productRepository.save(product));
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
        return ProductView.from(saved);
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

    private Product getForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
    }
}
