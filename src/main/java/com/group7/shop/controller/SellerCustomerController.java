package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.dto.SellerCustomerListView;
import com.group7.shop.dto.SellerCustomerOrderHistoryView;
import com.group7.shop.service.SellerCustomerQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Challenge A：卖家客户列表与指定客户购买历史。 */
@RestController
@RequestMapping("/api/seller/customers")
public class SellerCustomerController {

    private final SellerCustomerQueryService customerQueryService;

    public SellerCustomerController(SellerCustomerQueryService customerQueryService) {
        this.customerQueryService = customerQueryService;
    }

    @GetMapping
    public Result<SellerCustomerListView> customers() {
        return Result.success(customerQueryService.customers());
    }

    @GetMapping("/{id}/orders")
    public Result<SellerCustomerOrderHistoryView> orders(@PathVariable Long id) {
        return Result.success(customerQueryService.orders(id));
    }
}
