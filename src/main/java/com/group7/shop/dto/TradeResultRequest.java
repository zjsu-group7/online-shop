package com.group7.shop.dto;

import com.group7.shop.entity.FailedAction;
import jakarta.validation.constraints.NotNull;

/**
 * 标记交易结果入参（规则域负责人：华天遥）。
 * result：SUCCESS / FAIL；failedAction：VOID 作废 / REQUEUE 重新排队，仅 FAIL 时必填。
 */
public record TradeResultRequest(
        @NotNull(message = "交易结果必填")
        TradeResult result,

        FailedAction failedAction
) {
    /** 兼容迭代 1 的单参调用。 */
    public TradeResultRequest(TradeResult result) {
        this(result, null);
    }
}
