package com.group7.shop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PublishProductRequest(
        @NotBlank(message = "商品名称必填")
        @Size(max = 100, message = "商品名称不能超过100字")
        String name,

        @Size(max = 2000, message = "商品描述不能超过2000字")
        String description,

        @NotNull(message = "商品价格必填")
        @DecimalMin(value = "0.01", message = "商品价格必须大于0")
        @Digits(integer = 8, fraction = 2, message = "商品价格最多8位整数和2位小数")
        BigDecimal price,

        String image,

        /** 初始库存，缺省为 0；库存为 0 时需补货后才能上架售卖。 */
        @Min(value = 0, message = "库存不能为负数")
        Integer stock
) {
    /** 兼容迭代 1 的四参调用。 */
    public PublishProductRequest(String name, String description, BigDecimal price, String image) {
        this(name, description, price, image, 0);
    }
}
