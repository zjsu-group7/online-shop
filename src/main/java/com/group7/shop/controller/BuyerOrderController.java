package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.config.CustomerAuthInterceptor;
import com.group7.shop.dto.OrderCancelRequest;
import com.group7.shop.dto.OrderView;
import com.group7.shop.dto.PageResult;
import com.group7.shop.entity.Customer;
import com.group7.shop.service.BuyerOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家订单接口（李宇轩负责，挑战需求 B.1.2）。
 *
 * <pre>
 * GET  /api/orders                    我的订单列表（倒序 + 翻页）
 * POST /api/orders/{orderNo}/cancel   取消订单（开始发货前）
 * </pre>
 *
 * 两条接口都由 {@link CustomerAuthInterceptor} 校验 X-Token（{@code /api/orders/**}
 * 已纳入拦截范围），当前客户通过 request attribute 注入；本类**不接收 customerId 入参**，
 * 从入口就断掉越权查询他人订单的可能。
 *
 * <p>注意与挑战需求 A 的 {@code GET /api/customer/orders} 区分：那条是意向型下单记录，
 * 本条对应 B.1 的正式订单（五态 + 多商品清单）。
 */
@RestController
@RequestMapping("/api/orders")
public class BuyerOrderController {

    private final BuyerOrderService buyerOrderService;

    public BuyerOrderController(BuyerOrderService buyerOrderService) {
        this.buyerOrderService = buyerOrderService;
    }

    @GetMapping
    public Result<PageResult<OrderView>> list(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return Result.success(buyerOrderService.list(customer, page, size));
    }

    /** 取消订单；请求体可省，省略时原因记为「买家取消」。 */
    @PostMapping("/{orderNo}/cancel")
    public Result<OrderView> cancel(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                    @PathVariable String orderNo,
                                    @RequestBody(required = false) @Valid OrderCancelRequest request) {
        return Result.success("订单已取消", buyerOrderService.cancel(customer, orderNo, request));
    }
}
