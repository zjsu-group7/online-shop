package com.group7.shop.dto;

import java.math.BigDecimal;

/**
 * 订单明细项（挑战需求 B.1 订单升级 / B.2 商品预选升级）。
 *
 * <p>{@code unitPrice} 是**下单时的价格快照**，不随商品调价变化；
 * {@code subtotal} = 单价 × 数量，由订单核心域计算后落库，本层只透传，
 * 若订单域未给出则由 {@code OrderViewNormalizer} 用单价×数量补齐。
 *
 * @param available 商品当前是否仍可售：下架或缺货为 {@code false}，前端据此标注「无货」。
 *                  用包装类型而非 {@code boolean}，是为了让「订单域没提供该信息」与
 *                  「确定不可售」区分开——{@code null} 表示未知，前端不标注，
 *                  避免漏填一个字段就把整单商品显示成缺货。
 */
public record OrderItemView(
        Long productId,
        String productName,
        String coverImage,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        Boolean available
) {
}
