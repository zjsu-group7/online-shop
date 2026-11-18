package com.group7.shop.dto;

import java.util.List;

/** 收藏列表视图（华天遥，挑战需求 B）。 */
public record FavoriteListView(
        List<FavoriteItemView> items
) {
}
