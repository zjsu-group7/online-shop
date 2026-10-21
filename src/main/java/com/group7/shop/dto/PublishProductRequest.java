package com.group7.shop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record PublishProductRequest(
        @NotBlank(message = "商品名称必填")
        @Size(max = 100, message = "商品名称不能超过100字")
        String name,

        /** 富文本 HTML，服务端按白名单过滤后存储。 */
        String description,

        @NotNull(message = "商品价格必填")
        @DecimalMin(value = "0.01", message = "商品价格必须大于0")
        @Digits(integer = 8, fraction = 2, message = "商品价格最多8位整数和2位小数")
        BigDecimal price,

        @NotNull(message = "商品类别必填")
        Long categoryId,

        /** 初始库存，缺省为 0；库存为 0 时需补货后才能上架售卖。 */
        @Min(value = 0, message = "库存不能为负数")
        Integer stock,

        /** 多图，最多 9 张，元素为 Base64 data URI。 */
        List<String> images,

        /** 兼容旧版单图字段；images 为空时生效。 */
        String image
) {
    /** 兼容迭代 1 的四参调用。 */
    public PublishProductRequest(String name, String description, BigDecimal price, String image) {
        this(name, description, price, null, 0, null, image);
    }
}