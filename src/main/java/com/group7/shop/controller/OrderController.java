package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.config.CustomerAuthInterceptor;
import com.group7.shop.dto.CheckoutRequest;
import com.group7.shop.dto.CheckoutResult;
import com.group7.shop.dto.OrderView;
import com.group7.shop.entity.Customer;
import com.group7.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/purchase-orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    public Result<CheckoutResult> checkout(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                           @Valid @RequestBody CheckoutRequest request) {
        return Result.success(orders.checkout(customer, request.cartItemIds()));
    }

    @GetMapping
    public Result<Page<OrderView>> list(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "12") int size) {
        return Result.success(orders.listCustomerOrders(customer.getId(), page, size));
    }

    @PostMapping("/{id}/cancel")
    public Result<OrderView> cancel(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                    @PathVariable Long id) {
        return Result.success(orders.cancelByCustomer(customer.getId(), id));
    }
}
