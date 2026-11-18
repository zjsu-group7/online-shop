package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.dto.OrderActionRequest;
import com.group7.shop.dto.OrderCancelRequest;
import com.group7.shop.dto.OrderView;
import com.group7.shop.dto.PageResult;
import com.group7.shop.service.SellerOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 卖家后台订单管理（李宇轩负责，挑战需求 B.1.1 → B.1.3）。
 *
 * <pre>
 * GET  /api/seller/orders                    订单列表（status 过滤 + 分页，下单时间倒序）
 * POST /api/seller/orders/{orderNo}/advance   状态推进（确认 / 备货 / 发货 / 完成）
 * POST /api/seller/orders/{orderNo}/cancel    卖家取消（交易完成前）
 * </pre>
 *
 * 三条接口都被 {@code SellerAuthInterceptor} 覆盖（/api/seller/**），本类不解析 token。
 * 前端「订单管理」页签的按钮显隐直接读响应里的 {@code allowedActions}，不再自行判断状态。
 */
@RestController
@RequestMapping("/api/seller/orders")
public class SellerOrderController {

    private final SellerOrderService sellerOrderService;

    public SellerOrderController(SellerOrderService sellerOrderService) {
        this.sellerOrderService = sellerOrderService;
    }

    /** 订单列表：status 选填（PENDING/CONFIRMED/PACKED/SHIPPED/COMPLETED/CANCELLED），page 从 1 开始。 */
    @GetMapping
    public Result<PageResult<OrderView>> list(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return Result.success(sellerOrderService.list(status, page, size));
    }

    /** 推进状态；跳级或回退返回 409。 */
    @PostMapping("/{orderNo}/advance")
    public Result<OrderView> advance(@PathVariable String orderNo,
                                     @Valid @RequestBody OrderActionRequest request) {
        return Result.success("订单状态已更新", sellerOrderService.advance(orderNo, request));
    }

    /** 卖家取消；请求体可省，省略时原因记为「卖家取消」。 */
    @PostMapping("/{orderNo}/cancel")
    public Result<OrderView> cancel(@PathVariable String orderNo,
                                    @RequestBody(required = false) @Valid OrderCancelRequest request) {
        return Result.success("订单已取消", sellerOrderService.cancel(orderNo, request));
    }
}
