package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.config.CustomerAuthInterceptor;
import com.group7.shop.dto.AddCartRequest;
import com.group7.shop.dto.CartItemView;
import com.group7.shop.dto.CartView;
import com.group7.shop.dto.TransferFavoriteRequest;
import com.group7.shop.dto.TransferFavoriteResponse;
import com.group7.shop.dto.UpdateCartQuantityRequest;
import com.group7.shop.entity.Customer;
import com.group7.shop.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车接口（华天遥负责，挑战需求 B / FR-B7、FR-B9）。
 *
 * <pre>
 * POST   /api/customer/cart             加购（重复加入同一商品数量累加）
 * GET    /api/customer/cart             购物车列表（实时标注 已下架/无货）
 * PUT    /api/customer/cart/{itemId}    修改数量（>=1）
 * DELETE /api/customer/cart/{itemId}    删除单条
 * DELETE /api/customer/cart             一键清空
 * POST   /api/customer/cart/to-favorites  勾选多件转收藏（转移，UC-06）
 * </pre>
 *
 * 全部由 {@link CustomerAuthInterceptor} 校验 X-Token，customerId 一律取自登录态，
 * 不接受任何形式的 customerId 入参，杜绝越权操作他人购物车。
 */
@RestController
@RequestMapping("/api/customer/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public Result<CartItemView> add(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                    @Valid @RequestBody AddCartRequest request) {
        return Result.success("已加入购物车",
                cartService.add(customer.getId(), request));
    }

    @GetMapping
    public Result<CartView> list(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer) {
        return Result.success(cartService.list(customer.getId()));
    }

    @PutMapping("/{itemId}")
    public Result<CartItemView> updateQuantity(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                               @PathVariable Long itemId,
                                               @Valid @RequestBody UpdateCartQuantityRequest request) {
        return Result.success("数量已更新",
                cartService.updateQuantity(customer.getId(), itemId, request));
    }

    @DeleteMapping("/{itemId}")
    public Result<Void> remove(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                               @PathVariable Long itemId) {
        cartService.remove(customer.getId(), itemId);
        return Result.success("已从购物车移除", null);
    }

    @DeleteMapping
    public Result<Void> clear(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer) {
        cartService.clear(customer.getId());
        return Result.success("购物车已清空", null);
    }

    @PostMapping("/to-favorites")
    public Result<TransferFavoriteResponse> transferToFavorites(
            @RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
            @Valid @RequestBody TransferFavoriteRequest request) {
        return Result.success("已转为收藏",
                cartService.transferToFavorites(customer.getId(), request));
    }
}
