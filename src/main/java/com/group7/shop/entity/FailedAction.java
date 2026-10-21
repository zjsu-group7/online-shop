package com.group7.shop.entity;

/**
 * 交易失败后对被选中意向的处置方式（规则域负责人：华天遥）。
 * VOID 作废（终态 FAILED）/ REQUEUE 重新排队（回到队尾）。
 */
public enum FailedAction {
    VOID,
    REQUEUE
}
