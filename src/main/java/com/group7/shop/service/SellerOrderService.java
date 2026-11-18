package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.OrderActionRequest;
import com.group7.shop.dto.OrderCancelRequest;
import com.group7.shop.dto.OrderView;
import com.group7.shop.dto.PageResult;
import com.group7.shop.order.OrderAction;
import com.group7.shop.order.OrderGatewayAccessor;
import com.group7.shop.order.OrderStatusCodes;
import com.group7.shop.order.OrderViewNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 卖家后台订单管理（李宇轩负责，挑战需求 B.1.1 / B.1.3）。
 *
 * <p>职责边界：本类**只做后台侧的把关**——入参白名单校验、分页边界、状态过滤归一化、
 * 出参文案补齐；订单的五态状态机、库存锁定与释放、订单号生成、价格快照全部由
 * 订单核心域（周琳皓）在 {@link com.group7.shop.order.OrderGateway} 内完成。
 * 这样状态的唯一定义处只有一处，不会出现「后台放行、订单域拒绝」的口径分裂。
 *
 * <p>卖家身份由 {@code SellerAuthInterceptor} 在进入 Controller 前校验，
 * 本系统只有唯一卖家账号，因而不做「订单归属卖家」的过滤；
 * 若后续引入多商家，只需在此处按 sellerId 增加过滤条件。
 */
@Service
public class SellerOrderService {

    private static final Logger log = LoggerFactory.getLogger(SellerOrderService.class);

    private static final String DEFAULT_CANCEL_REASON = "卖家取消";

    private final OrderGatewayAccessor gatewayAccessor;

    public SellerOrderService(OrderGatewayAccessor gatewayAccessor) {
        this.gatewayAccessor = gatewayAccessor;
    }

    /**
     * 订单列表，按下单时间倒序。
     *
     * @param status 状态过滤，空表示全部；非法取值直接 400，
     *               避免「状态名拼错 → 返回空列表 → 以为订单丢了」
     */
    public PageResult<OrderView> list(String status, Integer page, Integer size) {
        int safePage = OrderViewNormalizer.requirePage(page);
        int safeSize = OrderViewNormalizer.requireSize(size);

        String filter = null;
        if (status != null && !status.isBlank()) {
            filter = OrderStatusCodes.normalize(status);
            if (filter == null) {
                throw BusinessException.badRequest("状态取值不合法，可选：" + String.join(" / ", OrderStatusCodes.all()));
            }
        }
        return OrderViewNormalizer.normalize(gatewayAccessor.require().sellerOrders(filter, safePage, safeSize));
    }

    /**
     * 推进订单状态：确认 → 备货完成 → 开始发货 → 交易完成，单向不可回退、不可跳级。
     * 跳级或回退由订单域判定并返回 409，本层不复制该规则。
     */
    public OrderView advance(String orderNo, OrderActionRequest request) {
        String safeOrderNo = OrderViewNormalizer.requireOrderNo(orderNo);
        OrderAction action = OrderAction.parse(request.action());
        if (action == null) {
            throw BusinessException.badRequest(
                    "动作取值不合法，可选：CONFIRM / PACK / SHIP / COMPLETE（取消请调用 /cancel）");
        }
        OrderView view = gatewayAccessor.require().advance(safeOrderNo, action);
        log.info("卖家推进订单 {}：{} → {}", safeOrderNo, action.getLabel(), view == null ? "?" : view.status());
        return OrderViewNormalizer.normalize(view);
    }

    /**
     * 卖家取消订单（交易完成前）。已完成的订单由订单域返回 409；
     * 库存释放由订单域在同一事务内完成，本层不重复扣还。
     */
    public OrderView cancel(String orderNo, OrderCancelRequest request) {
        String safeOrderNo = OrderViewNormalizer.requireOrderNo(orderNo);
        String reason = request == null ? null : request.reason();
        String finalReason = (reason == null || reason.isBlank()) ? DEFAULT_CANCEL_REASON : reason.trim();
        OrderView view = gatewayAccessor.require().cancelBySeller(safeOrderNo, finalReason);
        log.info("卖家取消订单 {}，原因：{}", safeOrderNo, finalReason);
        return OrderViewNormalizer.normalize(view);
    }
}
