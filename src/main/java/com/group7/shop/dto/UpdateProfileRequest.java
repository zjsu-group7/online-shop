package com.group7.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 客户资料修改请求。电话必填（注册时即必填），默认交易地点选填；
 * 传入空字符串表示清空默认交易地点。
 */
public record UpdateProfileRequest(
        @NotBlank(message = "电话必填")
        @Size(max = 20, message = "电话不能超过20个字符")
        String phone,

        @Size(max = 200, message = "默认交易地点不能超过200个字符")
        String defaultTradePlace
) {
    public String trimmedPhone() {
        return phone == null ? "" : phone.trim();
    }

    public String trimmedTradePlace() {
        if (defaultTradePlace == null) {
            return null;
        }
        String trimmed = defaultTradePlace.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
