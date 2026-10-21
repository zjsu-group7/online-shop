package com.group7.shop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 修改库存入参：非负整数，0 表示无货（在售商品会自动下架）。 */
public record UpdateStockRequest(
        @NotNull(message = "库存必填")
        @Min(value = 0, message = "库存不能为负数")
        Integer stock
) {
}
