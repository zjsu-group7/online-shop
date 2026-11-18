package com.group7.shop.service;

import com.group7.shop.common.AccountGuard;
import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.OrderCancelRequest;
import com.group7.shop.dto.OrderView;
import com.group7.shop.dto.PageResult;
import com.group7.shop.entity.Customer;
import com.group7.shop.order.OrderGatewayAccessor;
import com.group7.shop.order.OrderViewNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 买家订单接口（李宇轩负责，挑战需求 B.1.2）。
 *
 * <p>买家订单列表的查询与分页由订单核心域实现（周琳皓），本类承担**账号隔离**：
 * <ol>
 *   <li>客户身份只从 {@code CustomerAuthInterceptor} 注入的 request attribute 获取，
 *       接口不接受任何 customerId 入参，因此不存在「传别人 id 查别人订单」的入口；</li>
 *   <li>取消订单属写操作，返回后再用 {@link AccountGuard} 二次核对归属——即便上游实现
 *       出错返回了别人的订单，这里也会拦成 403，不会把数据吐出去。</li>
 * </ol>
 *
 * <p>与挑战需求 A 的 {@code GET /api/customer/orders}（意向型下单记录）是两套东西：
 * 那条线记录的是「排队 + 口令码」时代的意向，本接口对应 B.1 的正式订单（含 5 个状态、多商品清单）。
 */
@Service
public class BuyerOrderService {

    private static final Logger log = LoggerFactory.getLogger(BuyerOrderService.class);

    private static final String DEFAULT_CANCEL_REASON = "买家取消";

    private final OrderGatewayAccessor gatewayAccessor;

    public BuyerOrderService(OrderGatewayAccessor gatewayAccessor) {
        this.gatewayAccessor = gatewayAccessor;
    }

    /** 我的订单，按下单时间倒序（翻页由订单域完成）。 */
    public PageResult<OrderView> list(Customer customer, Integer page, Integer size) {
        int safePage = OrderViewNormalizer.requirePage(page);
        int safeSize = OrderViewNormalizer.requireSize(size);
        return OrderViewNormalizer.normalize(
                gatewayAccessor.require().buyerOrders(customer.getId(), safePage, safeSize));
    }

    /** 买家取消本人订单：仅「开始发货」之前可取消，之后由订单域返回 409。 */
    public OrderView cancel(Customer customer, String orderNo, OrderCancelRequest request) {
        String safeOrderNo = OrderViewNormalizer.requireOrderNo(orderNo);
        String rawReason = request == null ? null : request.reason();
        String reason = (rawReason == null || rawReason.isBlank()) ? DEFAULT_CANCEL_REASON : rawReason.trim();
        OrderView view = gatewayAccessor.require().cancelByBuyer(customer.getId(), safeOrderNo, reason);
        if (view == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "订单域未返回取消结果");
        }
        // 防御性兜底：即便订单域按 id 过滤有误，也不允许把他人订单返回给当前客户
        AccountGuard.requireOwner("订单", view.customerId(), customer);
        log.info("客户 {} 取消订单 {}，原因：{}", customer.getUsername(), safeOrderNo, reason);
        return OrderViewNormalizer.normalize(view);
    }
}
