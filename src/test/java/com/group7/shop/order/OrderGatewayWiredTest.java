package com.group7.shop.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单核心域接入后的接口接线验证（由原「订单域缺席 503 降级」测试演进而来）。
 *
 * <p>订单核心域（周琳皓）与订单管理域契约（李雨轩）经 {@link OrderGatewayAdapter} 桥接后，
 * 原先「订单域缺席 → 503 降级」的场景已不存在。本测试改为验证：登录后访问 {@code /api/orders}
 * 能正确接线（返回 200 空列表，而非 503 或 500），以此确认适配器已作为 {@link OrderGateway}
 * Bean 注册、拦截器放行与订单域查询链路贯通。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderGatewayWiredTest {

    private static final String PASSWORD = "buyer12345";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("订单域接入后，买家订单接口返回 200 空列表而非 503")
    void orderEndpointsReturnEmptyPage() throws Exception {
        String username = "buyer_x_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/customer/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD
                                + "\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/customer/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = login.getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), "登录响应应包含 token");
        String token = matcher.group(1);

        mockMvc.perform(get("/api/orders").header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
