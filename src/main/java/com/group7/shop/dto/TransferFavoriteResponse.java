package com.group7.shop.dto;

/** 购物车转收藏结果（华天遥，挑战需求 B / UC-06）。 */
public record TransferFavoriteResponse(
        /** 本次实际新加入收藏的数量。 */
        int transferred,
        /** 勾选中已在收藏里、保持不重复的数量。 */
        int skipped
) {
}
