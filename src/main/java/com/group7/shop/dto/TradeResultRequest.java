package com.group7.shop.dto;

import jakarta.validation.constraints.NotNull;

public record TradeResultRequest(
        @NotNull(message = "交易结果必填")
        TradeResult result
) {
}
