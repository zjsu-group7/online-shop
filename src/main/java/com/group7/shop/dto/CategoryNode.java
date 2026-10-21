package com.group7.shop.dto;

import com.group7.shop.entity.Category;

import java.util.List;

/** 两级类别树节点。 */
public record CategoryNode(
        Long id,
        String name,
        Long parentId,
        int sort,
        List<CategoryNode> children
) {
    public static CategoryNode of(Category category, List<CategoryNode> children) {
        return new CategoryNode(category.getId(), category.getName(),
                category.getParentId(), category.getSort(), children);
    }
}