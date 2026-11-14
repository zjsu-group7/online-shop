package com.group7.shop.service;

import com.group7.shop.entity.Product;
import com.group7.shop.repository.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 迭代 3 的存量数据迁移（负责人：李婉婷），可重复执行：
 * 1) 存量商品补齐 lockedStock（新增列，旧数据为 null → 0）。
 *
 * <p>注：商品「两态化」（删除 FROZEN 状态）需与订单/购物车/收藏对意向交易链路的
 * 替换一并完成，届时在此追加「存量 FROZEN 商品 → ON_SALE」的迁移。
 */
@Component
public class Iteration3Migration implements ApplicationRunner {

    private final ProductRepository productRepository;

    public Iteration3Migration(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Product product : productRepository.findAll()) {
            if (product.getLockedStock() == null) {
                product.setLockedStock(0);
                productRepository.save(product);
            }
        }
    }
}
