package com.group7.shop.dto;

import jakarta.validation.constraints.NotNull;

public record DealRequest(
        @NotNull(message = "请选择购买意向")
        Long intentId
) {
}
