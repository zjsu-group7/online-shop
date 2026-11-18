package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.config.CustomerAuthInterceptor;
import com.group7.shop.dto.FavoriteListView;
import com.group7.shop.dto.FavoriteItemView;
import com.group7.shop.entity.Customer;
import com.group7.shop.service.FavoriteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 收藏接口（华天遥负责，挑战需求 B / FR-B6）。
 *
 * <pre>
 * POST   /api/customer/favorites              收藏（幂等去重，重复收藏不报错）
 * GET    /api/customer/favorites              收藏列表（实时标注 已下架/无货）
 * DELETE /api/customer/favorites/{productId}  取消收藏（幂等）
 * </pre>
 */
@RestController
@RequestMapping("/api/customer/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping
    public Result<FavoriteItemView> favorite(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                             @RequestBody Map<String, Long> body) {
        Long productId = body == null ? null : body.get("productId");
        if (productId == null) {
            return Result.failure(400, "商品 id 必填");
        }
        return Result.success("已收藏", favoriteService.favorite(customer.getId(), productId));
    }

    @GetMapping
    public Result<FavoriteListView> list(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer) {
        return Result.success(favoriteService.list(customer.getId()));
    }

    /** 是否已收藏（商品详情页收藏按钮态）。 */
    @GetMapping("/{productId}")
    public Result<Map<String, Boolean>> isFavorite(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                                   @PathVariable Long productId) {
        return Result.success(Map.of("favorited", favoriteService.isFavorite(customer.getId(), productId)));
    }

    @DeleteMapping("/{productId}")
    public Result<Void> unfavorite(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                   @PathVariable Long productId) {
        favoriteService.unfavorite(customer.getId(), productId);
        return Result.success("已取消收藏", null);
    }
}
