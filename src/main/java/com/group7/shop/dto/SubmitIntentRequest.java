package com.group7.shop.dto;

/**
 * POST /api/intents 与 PUT /api/intents/{code} 的请求体：{ name, phone }。
 * Wiki 规则：去空格后均非空，否则 400「姓名与联系电话均为必填」。
 * 空值校验在 Service 统一做（保证错误文案与 Wiki 完全一致），故不使用注解校验。
 */
public record SubmitIntentRequest(Long productId, String name, String phone) {

    public String trimmedName() {
        return name == null ? "" : name.trim();
    }

    public String trimmedPhone() {
        return phone == null ? "" : phone.trim();
    }
}
