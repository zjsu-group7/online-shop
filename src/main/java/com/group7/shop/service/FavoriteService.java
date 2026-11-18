package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.FavoriteItemView;
import com.group7.shop.dto.FavoriteListView;
import com.group7.shop.dto.ProductView;
import com.group7.shop.entity.Favorite;
import com.group7.shop.entity.Product;
import com.group7.shop.repository.FavoriteRepository;
import com.group7.shop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收藏（华天遥负责，挑战需求 B / FR-B6；表结构牵头：李婉婷）。
 *
 * <p>规则口径：收藏/取消收藏二态标记，同一商品去重（重复收藏幂等返回，不报错）；
 * 收藏与购物车互不影响，同一商品可同时在两处；下架/无货标注与购物车同一口径（实时计算）。</p>
 */
@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           ProductRepository productRepository) {
        this.favoriteRepository = favoriteRepository;
        this.productRepository = productRepository;
    }

    /** 收藏：已收藏时幂等返回原条目（不报错、不产生重复）。 */
    @Transactional
    public FavoriteItemView favorite(Long customerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));

        Favorite favorite = favoriteRepository
                .findByCustomerIdAndProductId(customerId, productId)
                .orElseGet(() -> favoriteRepository.save(new Favorite(customerId, productId)));

        return FavoriteItemView.of(favorite.getId(), favorite.getCreatedAt(), product);
    }

    /** 取消收藏：按商品 id；未收藏时幂等成功（前端无需预检查）。 */
    @Transactional
    public void unfavorite(Long customerId, Long productId) {
        favoriteRepository.findByCustomerIdAndProductId(customerId, productId)
                .ifPresent(favoriteRepository::delete);
    }

    /** 是否已收藏（商品详情页收藏按钮态）。 */
    @Transactional(readOnly = true)
    public boolean isFavorite(Long customerId, Long productId) {
        return favoriteRepository.existsByCustomerIdAndProductId(customerId, productId);
    }

    /** 收藏列表（倒序：最近收藏在前），标注口径与购物车一致。 */
    @Transactional(readOnly = true)
    public FavoriteListView list(Long customerId) {
        List<Favorite> favorites = favoriteRepository.findByCustomerIdOrderByIdDesc(customerId);
        List<Long> productIds = favorites.stream().map(Favorite::getProductId).distinct().toList();
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<FavoriteItemView> items = favorites.stream()
                .map(f -> FavoriteItemView.of(f.getId(), f.getCreatedAt(), products.get(f.getProductId())))
                .toList();
        return new FavoriteListView(items);
    }

    /** 商品简要视图（收藏页头部等场景复用）。 */
    @Transactional(readOnly = true)
    public ProductView productView(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
        return ProductView.from(product);
    }
}
