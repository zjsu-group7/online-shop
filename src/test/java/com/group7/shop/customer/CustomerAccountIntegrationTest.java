package com.group7.shop.customer;

import com.group7.shop.entity.Customer;
import com.group7.shop.entity.CustomerOrderLink;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.Product;
import com.group7.shop.repository.CustomerOrderLinkRepository;
import com.group7.shop.repository.CustomerRepository;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户账号与个人中心集成测试（李宇轩负责，挑战需求 A.2.1 / A.2.2）。
 *
 * <p>覆盖：注册唯一性、密码加盐哈希不外泄、登录与 token 过期时间、失败锁定、
 * 退出失效、X-Token 拦截、资料修改、登录下单与关联、我的下单记录过滤与越权隔离。
 * 每个用例由 {@code @Transactional} 回滚，不污染文件库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerAccountIntegrationTest {

    private static final String PASSWORD = "buyer12345";

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerOrderLinkRepository orderLinkRepository;
    @Autowired private IntentRepository intentRepository;
    @Autowired private ProductRepository productRepository;

    /* ==================== 辅助方法 ==================== */

    private MvcResult register(String username, String password, String phone, String tradePlace) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password
                + "\",\"phone\":\"" + phone + "\""
                + (tradePlace == null ? "" : ",\"defaultTradePlace\":\"" + tradePlace + "\"")
                + "}";
        return mockMvc.perform(post("/api/customer/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/customer/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private String tokenOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("响应中未找到 token: " + body);
        }
        return matcher.group(1);
    }

    private String registerAndLogin(String username) throws Exception {
        register(username, PASSWORD, "13800001111", null);
        return tokenOf(login(username, PASSWORD));
    }

    private Customer customer(String username) {
        return customerRepository.findByUsernameIgnoreCase(username).orElseThrow();
    }

    private Product saveProduct(String name) {
        return productRepository.save(new Product(name, "描述", new BigDecimal("99.00"), "/placeholder.png"));
    }

    private Intent saveIntent(Long productId, Instant submittedAt) {
        return intentRepository.save(new Intent(productId, "张三", "13900002222",
                UUID.randomUUID().toString().replace("-", ""), submittedAt));
    }

    /* ==================== 注册 ==================== */

    @Test
    @DisplayName("注册成功且响应不包含密码字段")
    void registerSucceedsWithoutLeakingPassword() throws Exception {
        MvcResult result = register("buyer_reg_01", PASSWORD, "13800001111", "图书馆门口");
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(result.getResponse().getStatus() == 200, "注册应成功: " + body);
        assertTrue(body.contains("\"username\":\"buyer_reg_01\""), body);
        assertTrue(body.contains("图书馆门口"), body);
        assertTrue(!body.contains("passwordHash") && !body.contains("salt"), "响应不得包含密码或盐: " + body);
    }

    @Test
    @DisplayName("用户名重复注册被拒绝（409）")
    void duplicateUsernameIsRejected() throws Exception {
        register("buyer_dup_01", PASSWORD, "13800001111", null);
        MvcResult second = register("buyer_dup_01", PASSWORD, "13900002222", null);

        assertTrue(second.getResponse().getStatus() == 409,
                "重复用户名应 409，实际 " + second.getResponse().getStatus());
        assertTrue(second.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("用户名已被占用"));
    }

    @Test
    @DisplayName("用户名判重忽略大小写")
    void duplicateUsernameIsCaseInsensitive() throws Exception {
        register("Buyer_Case_01", PASSWORD, "13800001111", null);
        MvcResult second = register("buyer_case_01", PASSWORD, "13800001111", null);

        assertTrue(second.getResponse().getStatus() == 409,
                "忽略大小写后应判为重复，实际 " + second.getResponse().getStatus());
    }

    @Test
    @DisplayName("密码少于 8 位被拒绝（400）")
    void shortPasswordIsRejected() throws Exception {
        MvcResult result = register("buyer_pwd_01", "1234567", "13800001111", null);
        assertTrue(result.getResponse().getStatus() == 400, "短密码应 400");
    }

    /* ==================== 登录 / 退出 ==================== */

    @Test
    @DisplayName("登录成功返回 token 与过期时间")
    void loginReturnsTokenAndExpiry() throws Exception {
        register("buyer_login_01", PASSWORD, "13800001111", null);
        MvcResult result = login("buyer_login_01", PASSWORD);

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(result.getResponse().getStatus() == 200, body);
        assertTrue(body.contains("\"expiresAt\""), "应返回 expiresAt: " + body);
        assertNotNull(tokenOf(result));
    }

    @Test
    @DisplayName("密码错误返回 401，且提示不区分用户名是否存在")
    void wrongPasswordReturnsUnauthorized() throws Exception {
        register("buyer_wrong_01", PASSWORD, "13800001111", null);
        MvcResult wrong = login("buyer_wrong_01", "wrong-password");
        MvcResult unknown = login("no_such_buyer", PASSWORD);

        assertTrue(wrong.getResponse().getStatus() == 401);
        assertTrue(wrong.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("用户名或密码错误"));
        assertTrue(unknown.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("用户名或密码错误"),
                "不得暴露用户名是否存在");
    }

    @Test
    @DisplayName("连续 5 次密码错误后锁定，第 6 次用正确密码仍被拒")
    void fifthWrongPasswordLocksAccount() throws Exception {
        register("buyer_lock_01", PASSWORD, "13800001111", null);
        for (int i = 0; i < 5; i++) {
            login("buyer_lock_01", "bad-password");
        }
        MvcResult locked = login("buyer_lock_01", PASSWORD);

        assertTrue(locked.getResponse().getStatus() == 401);
        assertTrue(locked.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("账号已锁定"),
                "第 6 次应提示锁定");
    }

    @Test
    @DisplayName("退出后旧 token 立即失效")
    void logoutInvalidatesToken() throws Exception {
        String token = registerAndLogin("buyer_logout_01");

        mockMvc.perform(post("/api/customer/logout").header("X-Token", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/customer/me").header("X-Token", token))
                .andExpect(status().isUnauthorized());
    }

    /* ==================== 拦截器与个人中心 ==================== */

    @Test
    @DisplayName("无 token 访问个人中心返回 401")
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/customer/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("个人中心返回本人资料")
    void meReturnsProfile() throws Exception {
        String token = registerAndLogin("buyer_me_01");

        mockMvc.perform(get("/api/customer/me").header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("buyer_me_01"))
                .andExpect(jsonPath("$.data.phone").value("13800001111"));
    }

    @Test
    @DisplayName("资料修改：默认交易地点可选填，也可清空")
    void updateProfileKeepsTradePlaceOptional() throws Exception {
        String token = registerAndLogin("buyer_profile_01");

        mockMvc.perform(put("/api/customer/me")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13700003333\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13700003333"))
                .andExpect(jsonPath("$.data.defaultTradePlace").value(nullValue()));

        mockMvc.perform(put("/api/customer/me")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13700003333\",\"defaultTradePlace\":\"一食堂门口\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTradePlace").value("一食堂门口"));

        mockMvc.perform(put("/api/customer/me")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13700003333\",\"defaultTradePlace\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTradePlace").value(nullValue()));
    }

    @Test
    @DisplayName("卖家 token 不能访问客户接口")
    void sellerTokenCannotAccessCustomerEndpoint() throws Exception {
        MvcResult sellerLogin = mockMvc.perform(post("/api/seller/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"seller\",\"password\":\"12345678\"}"))
                .andReturn();
        String sellerToken = tokenOf(sellerLogin);

        mockMvc.perform(get("/api/customer/me").header("X-Token", sellerToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("客户 token 不能访问卖家接口")
    void customerTokenCannotAccessSellerEndpoint() throws Exception {
        String customerToken = registerAndLogin("buyer_cross_01");

        mockMvc.perform(get("/api/seller/overview").header("X-Token", customerToken))
                .andExpect(status().isUnauthorized());
    }

    /* ==================== 下单与我的下单记录 ==================== */

    @Test
    @DisplayName("登录客户下单成功后建立本人关联，且不影响原有意向接口")
    void createOrderLinksCustomer() throws Exception {
        String token = registerAndLogin("buyer_submit_01");
        Customer me = customer("buyer_submit_01");
        saveProduct("台灯");

        MvcResult result = mockMvc.perform(post("/api/customer/orders")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"李四\",\"phone\":\"13600004444\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"intentId\"\\s*:\\s*(\\d+)").matcher(body);
        assertTrue(matcher.find(), "响应应返回 intentId: " + body);

        Long intentId = Long.valueOf(matcher.group(1));
        Optional<CustomerOrderLink> link = orderLinkRepository.findByIntentId(intentId);
        assertTrue(link.isPresent(), "应建立客户与意向的关联");
        assertTrue(me.getId().equals(link.get().getCustomerId()), "关联应归属当前客户");
    }

    @Test
    @DisplayName("未登录不能调用客户下单接口")
    void createOrderRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/customer/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"李四\",\"phone\":\"13600004444\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("我的下单记录只返回本人已出结果的记录，按提交时间倒序")
    void ordersOnlyReturnFinishedRecordsOfMine() throws Exception {
        String token = registerAndLogin("buyer_orders_01");
        Customer me = customer("buyer_orders_01");
        String otherToken = registerAndLogin("buyer_orders_02");
        Customer other = customer("buyer_orders_02");

        Product product = saveProduct("二手自行车");
        Instant now = Instant.now();

        Intent traded = saveIntent(product.getId(), now.minusSeconds(30));
        traded.markTraded();
        intentRepository.save(traded);
        orderLinkRepository.save(new CustomerOrderLink(me.getId(), traded.getId(), product.getId(), traded.getSubmittedAt()));

        Intent failed = saveIntent(product.getId(), now.minusSeconds(300));
        failed.markFailed();
        intentRepository.save(failed);
        orderLinkRepository.save(new CustomerOrderLink(me.getId(), failed.getId(), product.getId(), failed.getSubmittedAt()));

        // 仍在排队：不应出现在历史里
        Intent waiting = saveIntent(product.getId(), now.minusSeconds(10));
        orderLinkRepository.save(new CustomerOrderLink(me.getId(), waiting.getId(), product.getId(), waiting.getSubmittedAt()));

        // 他人记录：不应出现在我的列表
        Intent others = saveIntent(product.getId(), now.minusSeconds(20));
        others.markTraded();
        intentRepository.save(others);
        orderLinkRepository.save(new CustomerOrderLink(other.getId(), others.getId(), product.getId(), others.getSubmittedAt()));

        // 让成交时间可从商品侧取到（成功下架时会写 closedAt）
        product.closeAfterTradeSuccess();
        productRepository.save(product);

        mockMvc.perform(get("/api/customer/orders").header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].productName").value("二手自行车"))
                .andExpect(jsonPath("$.data.items[0].resultAt").exists())
                .andExpect(jsonPath("$.data.items[1].result").value("FAIL"));

        mockMvc.perform(get("/api/customer/orders").header("X-Token", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("没有下单记录时返回空列表而非 404")
    void ordersReturnEmptyListWhenNoRecords() throws Exception {
        String token = registerAndLogin("buyer_orders_03");

        mockMvc.perform(get("/api/customer/orders").header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }
}
