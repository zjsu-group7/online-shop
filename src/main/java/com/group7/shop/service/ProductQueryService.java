package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.PageResult;
import com.group7.shop.dto.ProductSummaryView;
import com.group7.shop.dto.ProductView;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductImage;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.ProductImageRepository;
import com.group7.shop.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Challenge A 的公开浏览查询；不承担发布、库存或状态流转。 */
@Service
public class ProductQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;

    public ProductQueryService(ProductRepository productRepository,
                               ProductImageRepository imageRepository) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<ProductSummaryView> products(String q,
                                                   Long categoryId,
                                                   int page,
                                                   int size,
                                                   String sort) {
        validatePage(page, size);
        if (categoryId != null && categoryId <= 0) {
            throw BusinessException.badRequest("categoryId 必须为正整数");
        }

        String keyword = normalizeKeyword(q);
        PageRequest pageable = PageRequest.of(page - 1, size, parseSort(sort));
        Page<Product> result = productRepository.findAll(specification(keyword, categoryId), pageable);

        List<ProductSummaryView> items = result.getContent().stream()
                .map(ProductSummaryView::from)
                .toList();
        return new PageResult<>(page, size, result.getTotalElements(), items);
    }

    @Transactional(readOnly = true)
    public ProductView product(Long id) {
        if (id == null || id <= 0) {
            throw BusinessException.badRequest("商品 ID 必须为正整数");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw BusinessException.notFound("商品已下架或暂不可售");
        }
        return toView(product);
    }

    private Specification<Product> specification(String keyword, Long categoryId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), ProductStatus.ON_SALE));

            if (keyword != null) {
                String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("description")), pattern, '\\')
                ));
            }
            if (categoryId != null) {
                predicates.add(builder.equal(root.get("categoryId"), categoryId));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String keyword = q.trim();
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw BusinessException.badRequest("搜索关键词不能超过 " + MAX_KEYWORD_LENGTH + " 个字符");
        }
        return keyword;
    }

    private void validatePage(int page, int size) {
        if (page < 1) {
            throw BusinessException.badRequest("page 必须从 1 开始");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw BusinessException.badRequest("size 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
    }

    private Sort parseSort(String rawSort) {
        String value = rawSort == null || rawSort.isBlank()
                ? "publishedAt,desc"
                : rawSort.trim();
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw invalidSort();
        }

        String field = parts[0].trim().toLowerCase(Locale.ROOT);
        String directionText = parts[1].trim().toLowerCase(Locale.ROOT);
        Sort.Direction direction;
        if ("asc".equals(directionText)) {
            direction = Sort.Direction.ASC;
        } else if ("desc".equals(directionText)) {
            direction = Sort.Direction.DESC;
        } else {
            throw invalidSort();
        }

        if ("publishedat".equals(field) || "createdat".equals(field)) {
            return Sort.by(direction, "createdAt").and(Sort.by(direction, "id"));
        }
        if ("price".equals(field)) {
            return Sort.by(direction, "price")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                    .and(Sort.by(Sort.Direction.DESC, "id"));
        }
        throw invalidSort();
    }

    private BusinessException invalidSort() {
        return BusinessException.badRequest(
                "sort 仅支持 publishedAt,asc、publishedAt,desc、price,asc、price,desc");
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private ProductView toView(Product product) {
        List<String> images = imageRepository.findByProductIdOrderBySortAscIdAsc(product.getId())
                .stream()
                .map(ProductImage::getUrl)
                .toList();
        if (images.isEmpty()) {
            return ProductView.from(product);
        }
        return ProductView.from(product, images);
    }
}
