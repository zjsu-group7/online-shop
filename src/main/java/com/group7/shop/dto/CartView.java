package com.group7.shop.dto;

import java.util.List;

/** 购物车列表视图（华天遥，挑战需求 B）。 */
public record CartView(
        List<CartItemView> items,
        /** 商品种类数（同一商品数量累加只算一条）。 */
        int kinds,
        /** 总件数（各类数量之和）。 */
        int totalQuantity
) {
}
