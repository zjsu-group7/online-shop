package com.group7.shop.dto;

import jakarta.validation.constraints.Size;

/**
 * 取消订单入参。买家与卖家共用，原因为选填（不填时记为「未填写原因」）。
 */
public record OrderCancelRequest(
        @Size(max = 100, message = "取消原因不超过100字") String reason
) {
}
