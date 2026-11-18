package com.group7.shop.dto;

import com.group7.shop.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 订单核心域（周琳皓）的结算/买家订单读模型：用于 {@code /api/customer/purchase-orders}
 * 结算下单与「我的订单」列表。字段取订单核心域自身的口径（id + 枚举状态 + 精简明细）。
 *
 * <p>与 {@link OrderView}（订单管理域统一契约，卖家后台与买家订单页共用）区分：
 * 本类服务于「从购物车结算」这条链路，状态直接暴露 {@link OrderStatus} 枚举；
 * 管理域的 {@link OrderView} 面向买卖家后台，暴露状态码 + 展示文案 + 可执行动作。
 */
public record PurchaseOrderView(Long id, String orderNo, OrderStatus status, BigDecimal totalAmount,
                                Instant createdAt, List<Item> items) {
    public record Item(Long productId, String productName, BigDecimal unitPrice, Integer quantity) {}
}
