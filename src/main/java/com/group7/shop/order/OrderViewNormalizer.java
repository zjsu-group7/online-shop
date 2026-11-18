package com.group7.shop.order;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.OrderItemView;
import com.group7.shop.dto.OrderView;
import com.group7.shop.dto.PageResult;

import java.util.List;

/**
 * 订单视图的出参整形（卖家端与买家端共用）。
 *
 * <p>订单核心域实现 {@link OrderGateway} 时可能只回状态码、不回文案，
 * 这里统一补齐 {@code statusText}，并兜住 {@code allowedActions / items} 的空值，
 * 让前端只处理一种形状的 JSON。参数校验也集中在此，两个服务共用一套边界值。
 */
public final class OrderViewNormalizer {

    /** 单页上限，避免一次拉全表。 */
    public static final int MAX_PAGE_SIZE = 100;

    private OrderViewNormalizer() {
    }

    /* ==================== 入参校验 ==================== */

    /** 页码：缺省 1，必须 ≥ 1。 */
    public static int requirePage(Integer page) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            throw BusinessException.badRequest("页码从 1 开始");
        }
        return page;
    }

    /** 每页条数：缺省 10，取值 1～100。 */
    public static int requireSize(Integer size) {
        if (size == null) {
            return 10;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw BusinessException.badRequest("每页条数取值范围 1～" + MAX_PAGE_SIZE);
        }
        return size;
    }

    /** 订单号：必填，长度 ≤ 64（与订单表字段一致）。 */
    public static String requireOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw BusinessException.badRequest("订单号必填");
        }
        String trimmed = orderNo.trim();
        if (trimmed.length() > 64) {
            throw BusinessException.badRequest("订单号不合法");
        }
        return trimmed;
    }

    /* ==================== 出参整形 ==================== */

    public static OrderView normalize(OrderView view) {
        if (view == null) {
            return null;
        }
        String statusText = view.statusText();
        if (statusText == null || statusText.isBlank()) {
            statusText = OrderStatusCodes.labelOf(view.status());
        }
        List<String> actions = view.allowedActions() == null ? List.of() : List.copyOf(view.allowedActions());
        List<OrderItemView> items = view.items() == null ? List.of() : List.copyOf(view.items());
        int itemCount = view.itemCount() > 0 ? view.itemCount() : items.size();
        return new OrderView(
                view.orderNo(),
                view.customerId(),
                view.customerName(),
                view.customerPhone(),
                view.status(),
                statusText,
                view.totalAmount(),
                itemCount,
                view.createdAt(),
                view.updatedAt(),
                actions,
                view.cancelReason(),
                items
        );
    }

    public static PageResult<OrderView> normalize(PageResult<OrderView> page) {
        if (page == null) {
            return new PageResult<>(1, 0, 0, List.of());
        }
        List<OrderView> items = page.items() == null
                ? List.of()
                : page.items().stream().map(OrderViewNormalizer::normalize).toList();
        return new PageResult<>(page.page(), page.size(), page.total(), items);
    }
}
