package com.group7.shop.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 订单视图，卖家后台与买家订单页共用（挑战需求 B.1）。
 *
 * <p>字段分三组：
 * <ul>
 *   <li>标识与时序：{@code orderNo}（对外唯一订单号）、{@code createdAt}、{@code updatedAt}；</li>
 *   <li>归属：{@code customerId / customerName / customerPhone}，买家侧即本人的信息
 *       （买家接口绝不返回他人订单，见 {@code AccountGuard}）；</li>
 *   <li>状态与金额：{@code status} 机器码 + {@code statusText} 展示文案，
 *       {@code allowedActions} 为当前状态允许的推进动作（前端据此渲染按钮，
 *       卖家端按钮显隐的唯一依据，避免前端自写状态判断而漂移）。</li>
 * </ul>
 *
 * @param status          PENDING / CONFIRMED / PACKED / SHIPPED / COMPLETED / CANCELLED
 * @param allowedActions  允许的 {@link com.group7.shop.order.OrderAction} 名称列表，终态为空列表
 * @param cancelReason    取消原因，未取消时为 {@code null}
 * @param items           订单明细，按录入顺序；列表接口同样返回，便于展示「共 N 件」
 */
public record OrderView(
        String orderNo,
        Long customerId,
        String customerName,
        String customerPhone,
        String status,
        String statusText,
        BigDecimal totalAmount,
        int itemCount,
        Instant createdAt,
        Instant updatedAt,
        List<String> allowedActions,
        String cancelReason,
        List<OrderItemView> items
) {
}
