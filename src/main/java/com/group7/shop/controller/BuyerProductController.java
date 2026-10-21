package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.dto.PageResult;
import com.group7.shop.dto.ProductSummaryView;
import com.group7.shop.dto.ProductView;
import com.group7.shop.service.ProductQueryService;
import com.group7.shop.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Collections;

@RestController
@RequestMapping("/api")
public class BuyerProductController {
    private final ProductService productService;
    private final ProductQueryService productQueryService;

    public BuyerProductController(ProductService productService,
                                  ProductQueryService productQueryService) {
        this.productService = productService;
        this.productQueryService = productQueryService;
    }

    /** Challenge A：公开商品列表，支持分页、关键词、二级类别和排序。 */
    @GetMapping("/products")
    public Result<PageResult<ProductSummaryView>> products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "publishedAt,desc") String sort) {
        return Result.success(productQueryService.products(q, categoryId, page, size, sort));
    }

    /** Challenge A：公开商品详情，仅返回当前在售商品。 */
    @GetMapping("/products/{id}")
    public Result<ProductView> product(@PathVariable Long id) {
        return Result.success(productQueryService.product(id));
    }

    /** Wiki: GET /api/product -> data: { product: ProductView | null } */
    @GetMapping("/product")
    public Result<Map<String, ProductView>> currentProduct() {
        ProductView product = productService.currentBuyerProduct();
        return Result.success(Collections.singletonMap("product", product));
    }
}
