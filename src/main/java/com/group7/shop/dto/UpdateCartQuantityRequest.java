package com.group7.shop.dto;

import jakarta.validation.constraints.Min;

/** 修改购物车数量请求（华天遥，挑战需求 B / FR-B7）。 */
public record UpdateCartQuantityRequest(
        @Min(value = 1, message = "数量至少为 1，删除请使用删除接口")
        Integer quantity
) {
}
