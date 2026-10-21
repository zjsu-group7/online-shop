package com.group7.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新建 / 修改类别。parentId 为空表示一级类别。 */
public record CategoryRequest(
        @NotBlank(message = "类别名称必填")
        @Size(max = 50, message = "类别名称不能超过50字")
        String name,

        Long parentId,

        Integer sort
) {
}