package com.group7.shop.dto;

import java.util.List;

/**
 * 后台首页聚合数据。
 * 迭代 2 多商品适配：products 返回全部在售/交易中商品及各自的意向队列；
 * product/queue 字段保留为最新一件在售商品的兼容视图（旧前端/脚本仍可用）。
 */
public record SellerOverview(
        ProductView product,
        List<IntentView> queue,
        List<ProductQueue> products,
        boolean canPublish
) {

    /** 某一件在售商品及其活跃意向队列（WAITING + IN_TRADE，按提交时间正序含位次）。 */
    public record ProductQueue(ProductView product, List<IntentView> queue) {
    }
}
