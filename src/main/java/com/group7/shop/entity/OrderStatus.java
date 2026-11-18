package com.group7.shop.entity;

/**
 * 订单状态机（挑战需求 B，负责人：周琳皓；表结构牵头：李婉婷）。
 * 单向流转、不可回退、不可跳级：客户下单 → 商家确认 → 备货完成 → 开始发货 → 交易完成；
 * 另含「已取消」终态（客户发货前取消 / 商家交易完成前取消）。
 */
public enum OrderStatus {
    /** 客户下单（买家从购物车提交）。 */
    CUSTOMER_SUBMITTED,
    /** 商家确认。 */
    SELLER_CONFIRMED,
    /** 备货完成。 */
    READY_TO_SHIP,
    /** 开始发货（线下开始交付的象征状态）。 */
    SHIPPED,
    /** 交易完成（线下交割完成后商家标记）。 */
    COMPLETED,
    /** 已取消（终态，保留在订单历史中）。 */
    CANCELLED;

    /** 终态判定：已取消/交易完成的订单不可再推进或取消。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
