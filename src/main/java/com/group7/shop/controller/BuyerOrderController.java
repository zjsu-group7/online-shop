package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.config.CustomerAuthInterceptor;
import com.group7.shop.dto.OrderCancelRequest;
import com.group7.shop.order.SellerOrderView;
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
 * <p>与既有接口的关系（三选一，别用错）：
 * <ul>
 *   <li>挑战需求 A 的 {@code /api/customer/orders}——意向型下单记录，只有成功/失败结果；</li>
 *   <li>订单核心域的 {@code /api/customer/purchase-orders}（周琳皓）——下单与买家订单列表/取消，
 *       前端当前走这一条；</li>
 *   <li>本类的 {@code /api/orders}——买家订单视图，字段与卖家端同构（含 statusText / allowedActions）。</li>
 * </ul>
 * 后两者语义重复，最终口径待组内对齐；本类保留是因为迭代 3 任务明确要求买家订单接口由本域提供，
 * 且它复用同一套 {@code OrderGateway} 契约与出参整形，删或留都不影响卖家端。
 */
@RestController
@RequestMapping("/api/orders")
public class BuyerOrderController {

    private final BuyerOrderService buyerOrderService;

    public BuyerOrderController(BuyerOrderService buyerOrderService) {
        this.buyerOrderService = buyerOrderService;
    }

    @GetMapping
    public Result<PageResult<SellerOrderView>> list(
            @RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.success(buyerOrderService.list(customer, page, size));
    }

    /** 取消订单；请求体可省，省略时原因记为「买家取消」。 */
    @PostMapping("/{orderNo}/cancel")
    public Result<SellerOrderView> cancel(
            @RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
            @PathVariable String orderNo,
            @RequestBody(required = false) @Valid OrderCancelRequest request) {
        return Result.success("订单已取消", buyerOrderService.cancel(customer, orderNo, request));
    }
}
