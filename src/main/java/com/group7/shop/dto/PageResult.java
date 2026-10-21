package com.group7.shop.dto;

import java.util.List;

/** Wiki 统一分页结构：page 从 1 开始。 */
public record PageResult<T>(
        int page,
        int size,
        long total,
        List<T> items
) {
}
