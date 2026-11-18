package com.group7.shop.dto;

import java.math.BigDecimal;

/**
 * 订单明细项（挑战需求 B.1 订单升级 / B.2 商品预选升级）。
 *
 * <p>{@code unitPrice} 是**下单时的价格快照**，不随商品调价变化；
 * {@code subtotal} = 单价 × 数量，由订单核心域计算后落库，本层只透传。
 *
 * @param available 商品当前是否仍可售；下架或缺货时为 {@code false}，
 *                  前端据此标注「已下架 / 无货」，购物车与订单页共用该口径
 */
public record OrderItemView(
        Long productId,
        String productName,
        String coverImage,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        boolean available
) {
}
