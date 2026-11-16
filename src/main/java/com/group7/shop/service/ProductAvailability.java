package com.group7.shop.service;

import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;

/**
 * 商品可购性实时计算（华天遥，挑战需求 B：购物车/收藏的「已下架/无货」标注）。
 * <p>不下库、不存状态：补货或重新上架后，下次查询标注自动消失、条目恢复可买。</p>
 */
public final class ProductAvailability {

    private ProductAvailability() {
    }

    /** 不可购原因：下架 → OFF_SHELF；可售库存为 0 → OUT_OF_STOCK；可购 → null。 */
    public static String reasonOf(Product product) {
        if (product.getStatus() != ProductStatus.ON_SALE) {
            return "OFF_SHELF";
        }
        if (product.availableStock() <= 0) {
            return "OUT_OF_STOCK";
        }
        return null;
    }

    public static boolean available(Product product) {
        return reasonOf(product) == null;
    }
}
