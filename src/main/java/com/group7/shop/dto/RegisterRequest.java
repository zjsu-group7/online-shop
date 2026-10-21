package com.group7.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 客户注册请求。
 * 用户名唯一（忽略大小写判重），电话必填但不要求唯一，默认交易地点选填。
 */
public record RegisterRequest(
        @NotBlank(message = "用户名必填")
        @Size(max = 32, message = "用户名不能超过32个字符")
        String username,

        @NotBlank(message = "密码必填")
        @Size(min = 8, max = 64, message = "密码不少于8位")
        String password,

        @NotBlank(message = "电话必填")
        @Size(max = 20, message = "电话不能超过20个字符")
        String phone,

        @Size(max = 200, message = "默认交易地点不能超过200个字符")
        String defaultTradePlace
) {
    public String trimmedUsername() {
        return username == null ? "" : username.trim();
    }

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
