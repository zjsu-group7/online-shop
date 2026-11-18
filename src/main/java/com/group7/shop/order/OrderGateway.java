package com.group7.shop.order;

import com.group7.shop.dto.PageResult;

/**
 * 订单核心域（周琳皓）向账号与后台域（李宇轩）暴露的最小契约。
 *
 * <p><b>为什么是一个接口而不是直接调用 {@code OrderService}：</b>两个域由不同人并行开发，
 * 订单实体与五态状态机属于订单核心域，卖家后台只消费其读模型与状态推进能力。
 * 本接口把跨域调用点收在一个文件里，订单域实现它即可，双方都不必改对方的类。
 * 与《子系统划分》中「账号与后台域 → 订单核心域」的单向依赖一致。
 *
 * <p><b>实现方（订单核心域）必须保证的错误语义：</b>
 * <ul>
 *   <li>订单号不存在 → 404；买家操作他人订单 → **同样返回 404**（不泄露订单是否存在）；</li>
 *   <li>状态机不允许的推进（回退、跳级、对已取消/已完成订单操作）→ 409；</li>
 *   <li>库存不足等业务冲突 → 409；</li>
 *   <li>取消时释放库存、成交时扣减库存由实现方负责，本层不重复处理。</li>
 * </ul>
 *
 * <p><b>本接口不负责鉴权</b>：卖家身份由 {@code SellerAuthInterceptor}、
 * 买家身份由 {@code CustomerAuthInterceptor} 在进入 Controller 前校验完毕，
 * 买家侧的 {@code customerId} 一律取自 token，绝不来自请求参数。
 */
public interface OrderGateway {

    /**
     * 卖家视角：分页查询全部订单，按下单时间倒序。
     *
     * @param status 状态过滤，{@code null} 表示全部；取值见 {@code PENDING/CONFIRMED/PACKED/SHIPPED/COMPLETED/CANCELLED}
     * @param page   页码，从 1 开始
     * @param size   每页条数
     */
    PageResult<SellerOrderView> sellerOrders(String status, int page, int size);

    /**
     * 买家视角：分页查询**本人**订单，按下单时间倒序。
     * 实现方必须按 {@code customerId} 过滤，不得返回他人订单。
     */
    PageResult<SellerOrderView> buyerOrders(Long customerId, int page, int size);

    /**
     * 卖家推进订单状态（确认 / 备货 / 发货 / 完成）。
     * 状态机非法时抛 409；订单不存在抛 404。
     */
    SellerOrderView advance(String orderNo, OrderAction action);

    /**
     * 卖家取消订单（交易完成前），实现方负责释放已锁定库存。
     * 已完成的订单取消应返回 409。
     */
    SellerOrderView cancelBySeller(String orderNo, String reason);

    /**
     * 买家取消本人订单（开始发货前），实现方负责释放已锁定库存。
     * 非本人订单或已发货及之后的状态应分别返回 404 / 409。
     *
     * @param reason 取消原因，已由本域归一（省略时传「买家取消」），实现方负责落库
     */
    SellerOrderView cancelByBuyer(Long customerId, String orderNo, String reason);
}
