package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.CheckoutResult;
import com.group7.shop.dto.PurchaseOrderView;
import com.group7.shop.entity.CartItem;
import com.group7.shop.entity.Customer;
import com.group7.shop.entity.Order;
import com.group7.shop.entity.OrderItem;
import com.group7.shop.entity.OrderStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.CartItemRepository;
import com.group7.shop.repository.OrderItemRepository;
import com.group7.shop.repository.OrderRepository;
import com.group7.shop.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CartItemRepository cart;
    private final ProductRepository products;

    public OrderService(OrderRepository orders, OrderItemRepository orderItems,
                        CartItemRepository cart, ProductRepository products) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.cart = cart;
        this.products = products;
    }

    @Transactional
    public CheckoutResult checkout(Customer customer, List<Long> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty() || selectedIds.stream().anyMatch(id -> id == null)) {
            throw BusinessException.badRequest("请选择购物车商品");
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(selectedIds));
        List<CartItem> selected = cart.findByCustomerIdAndIdIn(customer.getId(), ids);
        if (selected.size() != ids.size()) {
            throw BusinessException.badRequest("勾选中包含不存在或非本人的购物车条目");
        }
        List<CheckoutResult.RejectedCartItem> rejected = new ArrayList<>();
        List<CartItem> accepted = new ArrayList<>();
        List<Product> locked = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : ids) {
            CartItem item = selected.stream().filter(candidate -> candidate.getId().equals(id)).findFirst().orElseThrow();
            Product product = products.findByIdForUpdate(item.getProductId()).orElse(null);
            if (product == null || product.getStatus() != ProductStatus.ON_SALE ||
                    product.availableStock() < item.getQuantity()) {
                rejected.add(new CheckoutResult.RejectedCartItem(id, "商品已下架或库存不足"));
                continue;
            }
            product.lockStock(item.getQuantity());
            accepted.add(item);
            locked.add(product);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        if (accepted.isEmpty()) {
            return new CheckoutResult(null, rejected);
        }
        Order order = orders.save(new Order(UUID.randomUUID().toString().replace("-", ""),
                customer.getId(), total, customer.getDefaultTradePlace()));
        for (int i = 0; i < accepted.size(); i++) {
            CartItem item = accepted.get(i);
            Product product = locked.get(i);
            orderItems.save(new OrderItem(order.getId(), product.getId(), product.getName(),
                    product.getPrice(), item.getQuantity()));
        }
        cart.deleteAll(accepted);
        return new CheckoutResult(view(order), rejected);
    }

    @Transactional
    public PurchaseOrderView cancelByCustomer(Long customerId, Long orderId) {
        return cancelByCustomer(customerId, orderId, null);
    }

    @Transactional
    public PurchaseOrderView cancelByCustomer(Long customerId, Long orderId, String reason) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> BusinessException.notFound("订单不存在"));
        if (!order.getCustomerId().equals(customerId)) {
            throw BusinessException.notFound("订单不存在");
        }
        if (order.getStatus().ordinal() >= OrderStatus.SHIPPED.ordinal()) {
            throw BusinessException.badRequest("发货后不能取消订单");
        }
        return cancel(order, reason);
    }

    @Transactional
    public PurchaseOrderView cancelBySeller(Long orderId) {
        return cancelBySeller(orderId, null);
    }

    @Transactional
    public PurchaseOrderView cancelBySeller(Long orderId, String reason) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> BusinessException.notFound("订单不存在"));
        if (order.getStatus().isTerminal()) {
            throw BusinessException.badRequest("订单已结束");
        }
        return cancel(order, reason);
    }

    private PurchaseOrderView cancel(Order order, String reason) {
        for (OrderItem item : orderItems.findByOrderIdOrderByIdAsc(order.getId())) {
            Product product = products.findByIdForUpdate(item.getProductId()).orElseThrow();
            product.unlockStock(item.getQuantity());
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            order.setCancelReason(reason.trim());
        }
        return view(order);
    }

    @Transactional
    public PurchaseOrderView advanceBySeller(Long orderId, OrderStatus next) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> BusinessException.notFound("订单不存在"));
        OrderStatus current = order.getStatus();
        if (current.isTerminal() || next == null || next == OrderStatus.CANCELLED ||
                next.ordinal() != current.ordinal() + 1) {
            throw BusinessException.badRequest("订单状态不可回退或跳级");
        }
        if (next == OrderStatus.COMPLETED) {
            for (OrderItem item : orderItems.findByOrderIdOrderByIdAsc(orderId)) {
                Product product = products.findByIdForUpdate(item.getProductId()).orElseThrow();
                product.commitStock(item.getQuantity());
                if (product.getStock() == 0 && product.getStatus() == ProductStatus.ON_SALE) {
                    product.closeOutOfStock();
                }
            }
        }
        order.setStatus(next);
        order.setUpdatedAt(Instant.now());
        return view(order);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderView> listCustomerOrders(Long customerId, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw BusinessException.badRequest("页码或每页数量不合法");
        }
        return orders.findByCustomerId(customerId, PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).map(this::view);
    }

    private PurchaseOrderView view(Order order) {
        List<PurchaseOrderView.Item> items = orderItems.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .map(item -> new PurchaseOrderView.Item(item.getProductId(), item.getProductName(),
                        item.getUnitPrice(), item.getQuantity())).toList();
        return new PurchaseOrderView(order.getId(), order.getOrderNo(), order.getStatus(),
                order.getTotalAmount(), order.getCreatedAt(), items);
    }
}
