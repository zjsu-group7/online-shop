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

    @Transactional
    public ProductView publish(PublishProductRequest request) {
        if (productRepository.existsByStatusIn(ACTIVE_STATUSES)) {
            throw BusinessException.badRequest("已有商品在售或交易中");
        }
        String imageUrl = imageStorage.storeOrPlaceholder(request.image());
        Product product = new Product(
                request.name().trim(),
                request.description() == null ? "" : request.description().trim(),
                request.price(),
                imageUrl
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
        product.unfreezeManually();
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
