package com.group7.shop.order;

import com.group7.shop.dto.CheckoutResult;
import com.group7.shop.entity.CartItem;
import com.group7.shop.entity.Customer;
import com.group7.shop.entity.OrderStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.repository.CartItemRepository;
import com.group7.shop.repository.CustomerRepository;
import com.group7.shop.repository.ProductRepository;
import com.group7.shop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class OrderCorePersistenceTest {
    @Autowired private OrderService orders;
    @Autowired private CustomerRepository customers;
    @Autowired private ProductRepository products;
    @Autowired private CartItemRepository cart;

    @Test
    void partialShortagePersistsOrderAndCompletingDeductsStock() {
        Customer buyer = customers.save(new Customer("order_" + UUID.randomUUID().toString().substring(0, 8),
                "salt", "hash", "13800000000", null));
        Product available = products.save(new Product("可买商品", "", new BigDecimal("19.50"), null, 3));
        Product shortage = products.save(new Product("缺货商品", "", new BigDecimal("10.00"), null, 1));
        CartItem accepted = cart.save(new CartItem(buyer.getId(), available.getId(), 2));
        CartItem rejected = cart.save(new CartItem(buyer.getId(), shortage.getId(), 2));

        CheckoutResult result = orders.checkout(buyer, List.of(accepted.getId(), rejected.getId()));
        assertNotNull(result.order());
        assertEquals(new BigDecimal("39.00"), result.order().totalAmount());
        assertEquals(1, result.order().items().size());
        assertEquals(1, result.rejected().size());
        assertEquals(rejected.getId(), result.rejected().get(0).cartItemId());
        assertEquals(2, products.findById(available.getId()).orElseThrow().getLockedStock());
        assertEquals(3, products.findById(available.getId()).orElseThrow().getStock());
        assertEquals(false, cart.existsById(accepted.getId()));
        assertEquals(true, cart.existsById(rejected.getId()));

        Long orderId = result.order().id();
        orders.advanceBySeller(orderId, OrderStatus.SELLER_CONFIRMED);
        orders.advanceBySeller(orderId, OrderStatus.READY_TO_SHIP);
        orders.advanceBySeller(orderId, OrderStatus.SHIPPED);
        orders.advanceBySeller(orderId, OrderStatus.COMPLETED);
        assertEquals(1, products.findById(available.getId()).orElseThrow().getStock());
        assertEquals(0, products.findById(available.getId()).orElseThrow().getLockedStock());
        assertEquals(OrderStatus.COMPLETED, orders.listCustomerOrders(buyer.getId(), 1, 12)
                .getContent().get(0).status());
    }
}
