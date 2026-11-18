package com.group7.shop.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 卖家推进订单状态的入参。
 *
 * <p>只有动作码一个字段：状态变更的留痕（操作人、时间）由订单域在推进时记录，
 * 不在请求里额外传备注，避免出现「传了但没落库」的假字段。
 *
 * @param action 动作码：CONFIRM / PACK / SHIP / COMPLETE（忽略大小写）；
 *               取消走独立接口，不走本参数
 */
public record OrderActionRequest(
        @NotBlank(message = "动作必填") String action
) {
}
