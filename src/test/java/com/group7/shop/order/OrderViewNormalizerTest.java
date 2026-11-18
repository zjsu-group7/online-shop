package com.group7.shop.order;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.OrderItemView;
import com.group7.shop.dto.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单视图整形与状态码归一的纯单元测试（不起 Spring 上下文）。
 *
 * <p>守的是联调时最容易出岔子的两件事：状态码两套命名必须都能认、
 * 订单域少回字段时出参不能出现空白（小计/种数/文案都要有兜底）。
 */
class OrderViewNormalizerTest {

    private static OrderItemView item(String unitPrice, int quantity, BigDecimal subtotal, Boolean available) {
        return new OrderItemView(1L, "联调商品A", null, new BigDecimal(unitPrice), quantity, subtotal, available);
    }

    private static SellerOrderView view(String status, int itemCount, List<OrderItemView> items) {
        return new SellerOrderView("NO20261118001", 1L, "buyer01", "13800001111", status, null,
                new BigDecimal("99.00"), itemCount, null, null, List.of("CONFIRM"), null, items);
    }

    @Test
    @DisplayName("状态码：本域码与订单域枚举名都能归一")
    void normalizeAcceptsBothNaming() {
        assertEquals("PENDING", OrderStatusCodes.normalize("CUSTOMER_SUBMITTED"));
        assertEquals("CONFIRMED", OrderStatusCodes.normalize("seller_confirmed"));
        assertEquals("PACKED", OrderStatusCodes.normalize("READY_TO_SHIP"));
        assertEquals("SHIPPED", OrderStatusCodes.normalize("SHIPPED"));
        assertEquals("CANCELLED", OrderStatusCodes.normalize(" cancelled "));
        assertNull(OrderStatusCodes.normalize("NOT_A_STATUS"));
        assertNull(OrderStatusCodes.normalize(null));
    }

    @Test
    @DisplayName("状态文案：别名取中文，未知码原样返回不出现空白")
    void labelOfWorksWithAlias() {
        assertEquals("客户下单", OrderStatusCodes.labelOf("CUSTOMER_SUBMITTED"));
        assertEquals("备货完成", OrderStatusCodes.labelOf("READY_TO_SHIP"));
        assertEquals("UNKNOWN", OrderStatusCodes.labelOf("UNKNOWN"));
    }

    @Test
    @DisplayName("出参整形：statusText 缺失时按状态码补齐")
    void fillsStatusText() {
        SellerOrderView normalized = OrderViewNormalizer.normalize(
                view("SELLER_CONFIRMED", 0, List.of()));
        assertEquals("商家确认", normalized.statusText());
        assertEquals(List.of("CONFIRM"), normalized.allowedActions());
    }

    @Test
    @DisplayName("出参整形：itemCount 为 0 时按明细行数兜底（种数，不是件数）")
    void fillsItemCount() {
        SellerOrderView normalized = OrderViewNormalizer.normalize(
                view("PENDING", 0, List.of(
                        item("99.00", 2, null, null),
                        item("9.90", 1, null, null))));
        assertEquals(2, normalized.itemCount());
    }

    @Test
    @DisplayName("出参整形：小计缺失时按单价×数量补齐；available 未提供保持 null")
    void fillsSubtotal() {
        SellerOrderView normalized = OrderViewNormalizer.normalize(
                view("PENDING", 1, List.of(item("99.00", 3, null, null))));
        OrderItemView first = normalized.items().get(0);
        assertEquals(0, new BigDecimal("297.00").compareTo(first.subtotal()));
        assertNull(first.available(), "订单域没表态时不应替它判定无货");
    }

    @Test
    @DisplayName("出参整形：给定的小计不被覆盖")
    void keepsGivenSubtotal() {
        SellerOrderView normalized = OrderViewNormalizer.normalize(
                view("PENDING", 1, List.of(item("99.00", 3, new BigDecimal("280.00"), Boolean.TRUE))));
        assertEquals(0, new BigDecimal("280.00").compareTo(normalized.items().get(0).subtotal()));
        assertEquals(Boolean.TRUE, normalized.items().get(0).available());
    }

    @Test
    @DisplayName("分页参数：缺省值、边界与越界")
    void pageBounds() {
        assertEquals(1, OrderViewNormalizer.requirePage(null));
        assertEquals(3, OrderViewNormalizer.requirePage(3));
        assertEquals(10, OrderViewNormalizer.requireSize(null));
        assertEquals(100, OrderViewNormalizer.requireSize(100));
        assertThrows(BusinessException.class, () -> OrderViewNormalizer.requirePage(0));
        assertThrows(BusinessException.class, () -> OrderViewNormalizer.requireSize(0));
        assertThrows(BusinessException.class, () -> OrderViewNormalizer.requireSize(101));
    }

    @Test
    @DisplayName("订单号：空值、超长被拒，首尾空白被去掉")
    void orderNoRules() {
        assertEquals("NO1", OrderViewNormalizer.requireOrderNo("  NO1  "));
        assertThrows(BusinessException.class, () -> OrderViewNormalizer.requireOrderNo(" "));
        assertThrows(BusinessException.class, () -> OrderViewNormalizer.requireOrderNo("x".repeat(65)));
    }

    @Test
    @DisplayName("空页兜底：null 转成第 1 页空列表，前端不用判空")
    void emptyPage() {
        PageResult<SellerOrderView> result = OrderViewNormalizer.normalize((PageResult<SellerOrderView>) null);
        assertEquals(1, result.page());
        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
    }
}
