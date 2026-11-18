package com.group7.shop.order;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.OrderItemView;
import com.group7.shop.dto.OrderView;
import com.group7.shop.dto.PageResult;
import com.group7.shop.entity.Customer;
import com.group7.shop.entity.Order;
import com.group7.shop.entity.OrderItem;
import com.group7.shop.entity.OrderStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.repository.CustomerRepository;
import com.group7.shop.repository.OrderItemRepository;
import com.group7.shop.repository.OrderRepository;
import com.group7.shop.repository.ProductRepository;
import com.group7.shop.service.OrderService;
import com.group7.shop.service.ProductAvailability;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单核心域（周琳皓）→ 订单管理域契约 {@link OrderGateway}（李雨轩）的适配器。
 *
 * <p>两组域并行开发，口径不同，由本类统一桥接：
 * <ul>
 *   <li>订单核心域：{@link OrderStatus} 枚举 + 数字 id，结算/列表用精简读模型；</li>
 *   <li>订单管理域契约：状态码字符串（PENDING/CONFIRMED/PACKED/SHIPPED/COMPLETED/CANCELLED）
 *       + 订单号，读模型为富化的 {@link OrderView}。</li>
 * </ul>
 *
 * <p>本类不复制订单核心域的状态机与库存逻辑：推进/取消一律委托给 {@link OrderService}，
 * 只负责「订单号 → id 解析」「状态码/枚举互转」「富视图组装（补客户姓名/电话、状态文案、
 * 可执行动作、取消原因、明细可购标注）」。
 */
@Component
public class OrderGatewayAdapter implements OrderGateway {

    private final OrderService orderService;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CustomerRepository customers;
    private final ProductRepository products;

    public OrderGatewayAdapter(OrderService orderService, OrderRepository orders,
                               OrderItemRepository orderItems, CustomerRepository customers,
                               ProductRepository products) {
        this.orderService = orderService;
        this.orders = orders;
        this.orderItems = orderItems;
        this.customers = customers;
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderView> sellerOrders(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Order> result = (status == null)
                ? orders.findAll(pageable)
                : orders.findByStatus(toStatus(status), pageable);
        return toPageResult(result, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderView> buyerOrders(Long customerId, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return toPageResult(orders.findByCustomerId(customerId, pageable), page, size);
    }

    @Override
    @Transactional
    public OrderView advance(String orderNo, OrderAction action) {
        Order order = requireOrder(orderNo);
        orderService.advanceBySeller(order.getId(), toNextStatus(action));
        return view(refetch(order.getId()));
    }

    @Override
    @Transactional
    public OrderView cancelBySeller(String orderNo, String reason) {
        Order order = requireOrder(orderNo);
        orderService.cancelBySeller(order.getId(), reason);
        return view(refetch(order.getId()));
    }

    @Override
    @Transactional
    public OrderView cancelByBuyer(Long customerId, String orderNo, String reason) {
        Order order = requireOrder(orderNo);
        orderService.cancelByCustomer(customerId, order.getId(), reason);
        return view(refetch(order.getId()));
    }

    /* ==================== 内部工具 ==================== */

    private Order requireOrder(String orderNo) {
        return orders.findByOrderNo(orderNo)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
    }

    private Order refetch(Long id) {
        return orders.findById(id).orElseThrow(() -> BusinessException.notFound("订单不存在"));
    }

    private PageResult<OrderView> toPageResult(Page<Order> page, int pageNum, int size) {
        List<OrderView> items = page.getContent().stream().map(this::view).toList();
        return new PageResult<>(pageNum, size, page.getTotalElements(), items);
    }

    private OrderView view(Order order) {
        List<OrderItemView> itemViews = orderItems.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .map(this::itemView).toList();
        Customer customer = customers.findById(order.getCustomerId()).orElse(null);
        return new OrderView(
                order.getOrderNo(),
                order.getCustomerId(),
                customer == null ? null : customer.getUsername(),
                customer == null ? null : customer.getPhone(),
                statusCode(order.getStatus()),
                null, // statusText 由 OrderViewNormalizer 补齐
                order.getTotalAmount(),
                itemViews.size(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                allowedActions(order.getStatus()),
                order.getCancelReason(),
                itemViews
        );
    }

    private OrderItemView itemView(OrderItem item) {
        Product product = products.findById(item.getProductId()).orElse(null);
        boolean available = product != null && ProductAvailability.available(product);
        return new OrderItemView(
                item.getProductId(),
                item.getProductName(),
                product == null ? null : product.getImage(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                available
        );
    }

    private static String statusCode(OrderStatus status) {
        return switch (status) {
            case CUSTOMER_SUBMITTED -> OrderStatusCodes.PENDING;
            case SELLER_CONFIRMED -> OrderStatusCodes.CONFIRMED;
            case READY_TO_SHIP -> OrderStatusCodes.PACKED;
            case SHIPPED -> OrderStatusCodes.SHIPPED;
            case COMPLETED -> OrderStatusCodes.COMPLETED;
            case CANCELLED -> OrderStatusCodes.CANCELLED;
        };
    }

    private static OrderStatus toStatus(String code) {
        return switch (code) {
            case OrderStatusCodes.PENDING -> OrderStatus.CUSTOMER_SUBMITTED;
            case OrderStatusCodes.CONFIRMED -> OrderStatus.SELLER_CONFIRMED;
            case OrderStatusCodes.PACKED -> OrderStatus.READY_TO_SHIP;
            case OrderStatusCodes.SHIPPED -> OrderStatus.SHIPPED;
            case OrderStatusCodes.COMPLETED -> OrderStatus.COMPLETED;
            case OrderStatusCodes.CANCELLED -> OrderStatus.CANCELLED;
            default -> throw BusinessException.badRequest("状态取值不合法");
        };
    }

    private static OrderStatus toNextStatus(OrderAction action) {
        return switch (action) {
            case CONFIRM -> OrderStatus.SELLER_CONFIRMED;
            case PACK -> OrderStatus.READY_TO_SHIP;
            case SHIP -> OrderStatus.SHIPPED;
            case COMPLETE -> OrderStatus.COMPLETED;
        };
    }

    private static List<String> allowedActions(OrderStatus status) {
        return switch (status) {
            case CUSTOMER_SUBMITTED -> List.of("CONFIRM");
            case SELLER_CONFIRMED -> List.of("PACK");
            case READY_TO_SHIP -> List.of("SHIP");
            case SHIPPED -> List.of("COMPLETE");
            default -> List.of();
        };
    }
}
