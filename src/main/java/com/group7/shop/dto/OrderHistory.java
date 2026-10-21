package com.group7.shop.dto;

import java.util.List;

/**
 * 我的下单记录响应。只包含已出结果（成功 / 失败）的记录，
 * 排队中与交易中的记录属于「进行中」，不在此接口返回。
 */
public record OrderHistory(
        List<OrderRecordView> items,
        int total
) {
}
