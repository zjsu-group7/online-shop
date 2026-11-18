package com.group7.shop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 加入购物车请求（华天遥，挑战需求 B / FR-B7）。quantity 缺省为 1。 */
public record AddCartRequest(
        @NotNull(message = "商品 id 必填")
        Long productId,

        @Min(value = 1, message = "加购数量至少为 1")
        Integer quantity
) {
}
