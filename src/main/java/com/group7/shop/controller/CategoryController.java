package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.dto.CategoryNode;
import com.group7.shop.dto.CategoryRequest;
import com.group7.shop.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 买家端：两级类别树，无需登录。 */
    @GetMapping("/api/categories")
    public Result<List<CategoryNode>> tree() {
        return Result.success(categoryService.tree());
    }

    /** 以下三个接口由 /api/seller/** 的 X-Token 拦截器保护。 */
    @PostMapping("/api/seller/categories")
    public Result<CategoryNode> create(@Valid @RequestBody CategoryRequest request) {
        return Result.success("类别创建成功", categoryService.create(request));
    }

    @PutMapping("/api/seller/categories/{id}")
    public Result<CategoryNode> update(@PathVariable Long id,
                                       @Valid @RequestBody CategoryRequest request) {
        return Result.success("类别修改成功", categoryService.update(id, request));
    }

    @DeleteMapping("/api/seller/categories/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success("类别已删除", null);
    }
}