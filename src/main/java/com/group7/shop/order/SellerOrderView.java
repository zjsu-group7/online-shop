package com.group7.shop.order;

import com.group7.shop.dto.OrderItemView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 卖家/买家订单视图（挑战需求 B.1）。
 *
 * <p>类名带 {@code Seller} 前缀是为了与订单核心域的 {@code dto.OrderView}
 * （下单结果视图，字段为本域的子集）区分：两者同名同包会让合并直接编译失败。
 * 本域视图字段更全，是前端卖家订单页与买家订单页的展示口径。
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
 *                        （订单核心域用 CUSTOMER_SUBMITTED / SELLER_CONFIRMED / READY_TO_SHIP 等同义枚举，
 *                        两者都由 {@link OrderStatusCodes#normalize} 归一，出参以网关下发的为准）
 * @param allowedActions  允许的 {@link com.group7.shop.order.OrderAction} 名称列表，终态为空列表
 * @param itemCount       明细**行数**（商品种数，不是件数），前端显示"共 N 种商品"
 * @param cancelReason    取消原因，未取消时为 {@code null}
 * @param items           订单明细，按录入顺序；列表接口同样返回
 */
public record SellerOrderView(
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
