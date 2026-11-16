package com.group7.shop.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 购物车多选转收藏请求（华天遥，挑战需求 B / FR-B9 / UC-06）。 */
public record TransferFavoriteRequest(
        @NotEmpty(message = "请至少勾选一件购物车商品")
        List<Long> itemIds
) {
}
