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
 * 订单域缺席时的降级行为（李宇轩负责）。
 *
 * <p>本类**不加载**任何 {@link OrderGateway} 实现，模拟迭代 3 开发期订单核心域尚未合并的状态：
 * 应用必须能正常启动（前端、部署、其他域的联调不受影响），只有访问订单接口时返回 503 并说明原因，
 * 而不是启动失败或抛出 500 堆栈。订单域合并后本类即可删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderGatewayAbsentTest {

    private static final String PASSWORD = "buyer12345";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("订单域未接入时，订单接口返回 503 且提示明确")
    void orderEndpointsDegradeTo503() throws Exception {
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
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("订单核心域尚未接入，订单相关接口暂不可用"));
    }
}
