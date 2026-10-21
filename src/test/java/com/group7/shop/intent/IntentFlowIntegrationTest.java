package com.group7.shop.intent;

import com.group7.shop.dto.BuyerIntentView;
import com.group7.shop.dto.PublishProductRequest;
import com.group7.shop.entity.Category;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;
import com.group7.shop.repository.CategoryRepository;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.service.IntentCodeGenerator;
import com.group7.shop.service.IntentService;
import com.group7.shop.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 购买意向模块集成测试（负责人：华天遥）。
 * 通过 MockMvc 按 Wiki《接口设计》逐条验证四个买家接口及排队规则；
 * 卖家侧联动（冻结/下架/选定买家）通过 ProductService/IntentService 内部方法触发。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntentFlowIntegrationTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-HJ-NP-Z2-9]{8}$");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductService productService;
    @Autowired private IntentService intentService;
    @Autowired private IntentRepository intentRepository;
    @Autowired private IntentCodeGenerator codeGenerator;
    @Autowired private CategoryRepository categoryRepository;

    private Long productId;

    private Long categoryId() {
        Category root = categoryRepository.save(new Category("测试一级", null, 0));
        return categoryRepository.save(new Category("测试二级", root.getId(), 0)).getId();
    }

    @BeforeEach
    void publishProduct() {
        productId = productService.publish(new PublishProductRequest(
                "测试手机", "九成新测试机", new BigDecimal("1999.99"), categoryId(), 1, null, null)).id();
    }

    /* ---------- 辅助方法 ---------- */

    private String submitIntent(String name, String phone) throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("name", name, "phone", phone));
        MvcResult result = mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.code").isNotEmpty())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("code").asText();
    }

    private Intent intentByCode(String code) {
        return intentRepository.findByCodeHash(codeGenerator.hash(code)).orElseThrow();
    }

    /* ---------- TC-01~04 提交与口令码 ---------- */

    @Test
    @DisplayName("TC-I01 无在售商品时提交意向 → 400 暂无商品在售")
    void submitWithoutProduct() throws Exception {
        // 当前有商品，先下架腾出场景（下架会清空队列，无副作用）
        productService.offshelf(productId);
        mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("暂无商品在售"));
    }

    @Test
    @DisplayName("TC-I02 姓名或电话去空格后为空 → 400 姓名与联系电话均为必填")
    void submitBlankNameOrPhone() throws Exception {
        mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"phone\":\"13800000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("姓名与联系电话均为必填"));

        mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"phone\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("姓名与联系电话均为必填"));
    }

    @Test
    @DisplayName("TC-I03 提交成功返回 8 位口令码（不含 I/O/0/1），姓名电话去空格入库")
    void submitReturnsCode() throws Exception {
        String code = submitIntent("  张三  ", " 13800000000 ");
        assertTrue(CODE_PATTERN.matcher(code).matches(), "口令码格式非法: " + code);

        Intent intent = intentByCode(code);
        assertEquals("张三", intent.getName(), "姓名应去空格后入库");
        assertEquals("13800000000", intent.getPhone());
        assertEquals(IntentStatus.WAITING, intent.getStatus());
        assertEquals(productId, intent.getProductId());
    }

    @Test
    @DisplayName("TC-I04 连续提交 100 个意向，口令码全库唯一")
    void submitHundredUniqueCodes() throws Exception {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            codes.add(submitIntent("买家" + i, "1380000000" + (i % 10)));
        }
        assertEquals(100, codes.size(), "口令码出现重复");
    }

    /* ---------- TC-05~06 凭码查询与排队位次 ---------- */

    @Test
    @DisplayName("TC-I05 凭码查询：返回姓名/电话/状态/位次/提交时间与商品信息")
    void queryByCode() throws Exception {
        String code = submitIntent("张三", "13800000000");
        mockMvc.perform(get("/api/intents/{code}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent.name").value("张三"))
                .andExpect(jsonPath("$.data.intent.phone").value("13800000000"))
                .andExpect(jsonPath("$.data.intent.status").value("WAITING"))
                .andExpect(jsonPath("$.data.intent.position").value(1))
                .andExpect(jsonPath("$.data.intent.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.product.name").value("测试手机"))
                .andExpect(jsonPath("$.data.intent.codeHash").doesNotExist())
                .andExpect(jsonPath("$.data.intent.id").doesNotExist());
    }

    @Test
    @DisplayName("TC-I06 排队按提交时间先到先得，撤销后其余位次依次前移")
    void positionFifoAndShiftAfterCancel() throws Exception {
        String codeA = submitIntent("张三", "13800000001");
        String codeB = submitIntent("李四", "13800000002");
        String codeC = submitIntent("王五", "13800000003");

        mockMvc.perform(get("/api/intents/{code}", codeB))
                .andExpect(jsonPath("$.data.intent.position").value(2));
        mockMvc.perform(get("/api/intents/{code}", codeC))
                .andExpect(jsonPath("$.data.intent.position").value(3));

        // 张三撤销 → 李四、王五位次前移
        mockMvc.perform(post("/api/intents/{code}/cancel", codeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/intents/{code}", codeB))
                .andExpect(jsonPath("$.data.intent.position").value(1));
        mockMvc.perform(get("/api/intents/{code}", codeC))
                .andExpect(jsonPath("$.data.intent.position").value(2));
    }

    /* ---------- TC-07~09 口令码无效场景 ---------- */

    @Test
    @DisplayName("TC-I07 口令码不存在 / 格式非法 → 404 口令码无效或已失效")
    void unknownOrInvalidCode() throws Exception {
        mockMvc.perform(get("/api/intents/{code}", "ZZZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("口令码无效或已失效"));
        mockMvc.perform(get("/api/intents/{code}", "abc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("口令码无效或已失效"));
    }

    @Test
    @DisplayName("TC-I08 已撤销意向为终态：查询/修改/再次撤销均 404")
    void terminalIntentNotAccessible() throws Exception {
        String code = submitIntent("张三", "13800000000");
        mockMvc.perform(post("/api/intents/{code}/cancel", code)).andExpect(status().isOk());

        mockMvc.perform(get("/api/intents/{code}", code))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/intents/{code}", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名字\",\"phone\":\"13900000000\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/intents/{code}/cancel", code))
                .andExpect(status().isNotFound());

        // 终态留痕：数据库中仍为 CANCELLED，未物理删除
        assertEquals(IntentStatus.CANCELLED, intentByCode(code).getStatus());
    }

    @Test
    @DisplayName("TC-I09 修改意向：姓名电话更新成功且位次不变")
    void updateKeepsPosition() throws Exception {
        String codeA = submitIntent("张三", "13800000001");
        submitIntent("李四", "13800000002");

        mockMvc.perform(put("/api/intents/{code}", codeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三丰\",\"phone\":\"13900000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent.name").value("张三丰"))
                .andExpect(jsonPath("$.data.intent.phone").value("13900000001"))
                .andExpect(jsonPath("$.data.intent.position").value(1));

        mockMvc.perform(get("/api/intents/{code}", codeA))
                .andExpect(jsonPath("$.data.intent.position").value(1));
    }

    /* ---------- TC-10~12 交易中与商品联动 ---------- */

    @Test
    @DisplayName("TC-I10 卖家选定买家后：交易中不可撤销、不可修改，查询 position 置空")
    void inTradeRules() throws Exception {
        String codeA = submitIntent("张三", "13800000001");
        submitIntent("李四", "13800000002");
        Long intentId = intentByCode(codeA).getId();
        intentService.startTrade(productId, intentId);

        mockMvc.perform(get("/api/intents/{code}", codeA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent.status").value("IN_TRADE"))
                .andExpect(jsonPath("$.data.intent.position").isEmpty());

        mockMvc.perform(post("/api/intents/{code}/cancel", codeA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("你已进入交易，无法自行撤销"));

        mockMvc.perform(put("/api/intents/{code}", codeA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三丰\",\"phone\":\"13900000001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("你已进入交易，无法修改信息"));
    }

    @Test
    @DisplayName("TC-I11 商品冻结后不接收新意向 → 400 商品交易中，暂停接收新意向")
    void frozenProductRejectsNewIntent() throws Exception {
        productService.freezeManually(productId);
        mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("商品交易中，暂停接收新意向"));
    }

    @Test
    @DisplayName("TC-I12 商品下架后剩余排队意向全部置为失败（终态留痕）")
    void offshelfFailsWaitingIntents() throws Exception {
        String codeA = submitIntent("张三", "13800000001");
        String codeB = submitIntent("李四", "13800000002");

        productService.offshelf(productId);

        assertEquals(IntentStatus.FAILED, intentByCode(codeA).getStatus());
        assertEquals(IntentStatus.FAILED, intentByCode(codeB).getStatus());
        mockMvc.perform(get("/api/intents/{code}", codeA))
                .andExpect(status().isNotFound());
    }

    /* ---------- TC-13~15 卖家侧内部协作 ---------- */

    @Test
    @DisplayName("TC-I13 startTrade：意向不属于该商品 → 400")
    void startTradeWrongProduct() throws Exception {
        String code = submitIntent("张三", "13800000000");
        Long intentId = intentByCode(code).getId();
        try {
            intentService.startTrade(productId + 999, intentId);
        } catch (com.group7.shop.common.BusinessException e) {
            assertEquals(400, e.getStatus().value());
            assertEquals("意向不属于该商品", e.getMessage());
        }
    }

    @Test
    @DisplayName("TC-I14 工作台队列：交易中意向 position 为空，其余依次重排")
    void activeQueueForOverview() throws Exception {
        String codeA = submitIntent("张三", "13800000001");
        String codeB = submitIntent("李四", "13800000002");
        String codeC = submitIntent("王五", "13800000003");

        intentService.startTrade(productId, intentByCode(codeA).getId());

        List<BuyerIntentView> queue = intentService.activeQueue(productId);
        assertEquals(3, queue.size());
        assertEquals(IntentStatus.IN_TRADE, queue.get(0).status());
        assertNull(queue.get(0).position());
        assertEquals(1, queue.get(1).position());
        assertEquals(2, queue.get(2).position());
    }

    @Test
    @DisplayName("TC-I15 交易成功：该意向 TRADED，其余 WAITING 全部 FAILED")
    void tradeSuccessFailsOthers() throws Exception {
        String codeA = submitIntent("张三", "13800000001");
        String codeB = submitIntent("李四", "13800000002");

        Long intentIdA = intentByCode(codeA).getId();
        intentService.startTrade(productId, intentIdA);
        intentService.markTraded(intentIdA);
        intentService.failAllWaiting(productId);

        assertEquals(IntentStatus.TRADED, intentByCode(codeA).getStatus());
        assertEquals(IntentStatus.FAILED, intentByCode(codeB).getStatus());
    }

    /* ---------- TC-16 响应体统一格式 ---------- */

    @Test
    @DisplayName("TC-I16 统一响应体 { code, message, data } 且错误码与 HTTP 状态一致")
    void unifiedResponseShape() throws Exception {
        MvcResult ok = mockMvc.perform(get("/api/product")).andReturn();
        JsonNode okNode = objectMapper.readTree(ok.getResponse().getContentAsString());
        assertEquals(200, okNode.path("code").asInt());
        assertTrue(okNode.has("message"));
        assertTrue(okNode.has("data"));

        MvcResult bad = mockMvc.perform(post("/api/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phone\":\"\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        JsonNode badNode = objectMapper.readTree(bad.getResponse().getContentAsString());
        assertEquals(400, badNode.path("code").asInt());
        assertNotNull(badNode.path("message").asText());
        assertTrue(bNodeDataNullOrAbsent(badNode));
    }

    private boolean bNodeDataNullOrAbsent(JsonNode node) {
        return node.path("data").isNull() || node.path("data").isMissingNode();
    }
}
