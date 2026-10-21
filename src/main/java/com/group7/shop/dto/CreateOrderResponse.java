package com.group7.shop.dto;

/**
 * 客户下单响应。沿用意向域的口令码（前端仍可凭码查询进度），
 * 额外返回关联后的意向 id，便于前端直接跳转订单详情。
 */
public record CreateOrderResponse(
        String code,
        Long intentId
) {
}
