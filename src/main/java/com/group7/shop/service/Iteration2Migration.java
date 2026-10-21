package com.group7.shop.service;

import com.group7.shop.entity.Category;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductImage;
import com.group7.shop.repository.CategoryRepository;
import com.group7.shop.repository.ProductImageRepository;
import com.group7.shop.repository.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 迭代 2 的存量数据迁移，可重复执行：
 * 1) 无类别时创建「未分类 / 未分类商品」两级兜底类别；
 * 2) 存量商品补齐 categoryId 与 stock；
 * 3) 存量单图迁移到 product_images。
 */
@Component
public class Iteration2Migration implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;

    public Iteration2Migration(CategoryRepository categoryRepository,
                               ProductRepository productRepository,
                               ProductImageRepository imageRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() == 0) {
            Category root = categoryRepository.save(new Category("未分类", null, 0));
            categoryRepository.save(new Category("未分类商品", root.getId(), 0));
        }

        Category fallback = categoryRepository.findByParentIdIsNotNullOrderByIdAsc()
                .stream()
                .findFirst()
                .orElse(null);
        if (fallback == null) {
            return;
        }

        for (Product product : productRepository.findAll()) {
            boolean changed = false;
            if (product.getCategoryId() == null) {
                product.setCategoryId(fallback.getId());
                changed = true;
            }
            if (product.getStock() == null) {
                product.setStock(1);
                changed = true;
            }
            if (changed) {
                productRepository.save(product);
            }
            if (imageRepository.findByProductIdOrderBySortAscIdAsc(product.getId()).isEmpty()
                    && product.getImage() != null && !product.getImage().isBlank()) {
                imageRepository.save(new ProductImage(product.getId(), product.getImage(), 0));
            }
        }
    }
}