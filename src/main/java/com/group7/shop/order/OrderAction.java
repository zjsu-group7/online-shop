package com.group7.shop.order;

/**
 * 卖家推进订单状态的动作（挑战需求 B.1.1，李宇轩负责的卖家后台口径）。
 *
 * <p>只包含**向前推进**的四个动作；取消是独立入口（{@code POST .../cancel}），
 * 因为取消允许在任意未完成状态发生，语义不是「下一状态」。
 *
 * <p>状态取值（由订单核心域持有，本层只做展示）：
 * {@code PENDING}（客户下单）→ {@code CONFIRMED}（商家确认）→ {@code PACKED}（备货完成）
 * → {@code SHIPPED}（开始发货）→ {@code COMPLETED}（交易完成），另有终态 {@code CANCELLED}。
 *
 * <p>动作与目标状态的映射、以及「当前状态能否接受该动作」由订单核心域判定，
 * 非法推进一律抛 409；本枚举只负责把 HTTP 上的字符串安全地转成动作，多余取值返回空。
 */
public enum OrderAction {

    /** 商家确认（PENDING → CONFIRMED）。 */
    CONFIRM("商家确认"),
    /** 备货完成（CONFIRMED → PACKED）。 */
    PACK("备货完成"),
    /** 开始发货（PACKED → SHIPPED）。 */
    SHIP("开始发货"),
    /** 交易完成（SHIPPED → COMPLETED）。 */
    COMPLETE("交易完成");

    private final String label;

    OrderAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 忽略大小写解析动作码；未知取值返回 {@code null}（由调用方转 400）。 */
    public static OrderAction parse(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        for (OrderAction action : values()) {
            if (action.name().equalsIgnoreCase(normalized)) {
                return action;
            }
        }
        return null;
    }
}
