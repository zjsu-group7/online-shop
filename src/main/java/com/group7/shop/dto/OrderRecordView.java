package com.group7.shop.dto;

import java.time.Instant;

/**
 * 我的下单记录条目（《需求规格说明书》v3.1 FR-B7）。
 * 字段严格限定为：商品名称、下单时间、交易结果、交易时间；
 * 另附 productId / intentId / 封面图，供前端跳转与卡片展示，不包含其他买家信息。
 *
 * @param result 交易结果：SUCCESS 成功 / FAIL 失败
 */
public record OrderRecordView(
        Long intentId,
        Long productId,
        String productName,
        String coverImage,
        Instant submittedAt,
        String result,
        Instant resultAt
) {
}
