package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.entity.CartItem;
import com.group7.shop.entity.Customer;
import com.group7.shop.entity.Order;
import com.group7.shop.entity.OrderItem;
import com.group7.shop.entity.OrderStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.repository.CartItemRepository;
import com.group7.shop.repository.OrderItemRepository;
import com.group7.shop.repository.OrderRepository;
import com.group7.shop.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.http.HttpStatus;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final CartItemRepository cart = mock(CartItemRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final OrderService service = new OrderService(orders, orderItems, cart, products);

    @Test
    void partialShortageKeepsAvailableItemAndSnapshotsPrice() {
        Customer customer = new Customer("buyer", "salt", "hash", "13800000000", null);
        ReflectionTestUtils.setField(customer, "id", 1L);
        CartItem available = new CartItem(1L, 10L, 2);
        CartItem shortage = new CartItem(1L, 20L, 3);
        ReflectionTestUtils.setField(available, "id", 11L);
        ReflectionTestUtils.setField(shortage, "id", 22L);
        Product first = new Product("可买", "", new BigDecimal("9.50"), null, 2);
        Product second = new Product("缺货", "", new BigDecimal("20.00"), null, 2);
        ReflectionTestUtils.setField(first, "id", 10L);
        ReflectionTestUtils.setField(second, "id", 20L);
        when(cart.findByCustomerIdAndIdIn(1L, List.of(11L, 22L))).thenReturn(List.of(available, shortage));
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(first));
        when(products.findByIdForUpdate(20L)).thenReturn(Optional.of(second));
        when(orders.save(any(Order.class))).thenAnswer(call -> {
            Order order = call.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 1L);
            return order;
        });
        when(orderItems.findByOrderIdOrderByIdAsc(1L)).thenReturn(List.of());

        var result = service.checkout(customer, List.of(11L, 22L));

        assertThat(result.order().totalAmount()).isEqualByComparingTo("19.00");
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).cartItemId()).isEqualTo(22L);
        assertThat(first.getLockedStock()).isEqualTo(2);
        assertThat(second.getLockedStock()).isZero();
        ArgumentCaptor<OrderItem> snapshot = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItems).save(snapshot.capture());
        assertThat(snapshot.getValue().getUnitPrice()).isEqualByComparingTo("9.50");
        assertThat(snapshot.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    void cancelReleasesLockAndStateCannotSkip() {
        Order order = new Order("number", 1L, new BigDecimal("9.50"), null);
        ReflectionTestUtils.setField(order, "id", 1L);
        Product product = new Product("商品", "", new BigDecimal("9.50"), null, 2);
        product.lockStock(1);
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderItems.findByOrderIdOrderByIdAsc(1L))
                .thenReturn(List.of(new OrderItem(1L, 10L, "商品", new BigDecimal("9.50"), 1)));
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.advanceBySeller(1L, OrderStatus.SHIPPED))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        service.cancelByCustomer(1L, 1L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getLockedStock()).isZero();
        assertThatThrownBy(() -> service.advanceBySeller(1L, OrderStatus.SELLER_CONFIRMED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void completionDeductsStockAndBuyerCannotCancelAfterShipping() {
        Order order = new Order("number", 1L, new BigDecimal("9.50"), null);
        ReflectionTestUtils.setField(order, "id", 1L);
        order.setStatus(OrderStatus.SHIPPED);
        Product product = new Product("商品", "", new BigDecimal("9.50"), null, 1);
        product.lockStock(1);
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderItems.findByOrderIdOrderByIdAsc(1L))
                .thenReturn(List.of(new OrderItem(1L, 10L, "商品", new BigDecimal("9.50"), 1)));
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.cancelByCustomer(1L, 1L))
                .isInstanceOf(BusinessException.class);
        service.advanceBySeller(1L, OrderStatus.COMPLETED);
        assertThat(product.getStock()).isZero();
        assertThat(product.getLockedStock()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThatThrownBy(() -> service.cancelBySeller(1L)).isInstanceOf(BusinessException.class);
    }
}
