package com.group7.shop.entity;

/** 商品进入历史列表的关闭原因。 */
public enum ProductCloseReason {
    SOLD,
    MANUAL,
    /** 库存改为 0 触发的自动下架（规则域）。 */
    OUT_OF_STOCK
}
