package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.BuyerIntentView;
import com.group7.shop.dto.LoginResponse;
import com.group7.shop.dto.PublishProductRequest;
import com.group7.shop.dto.SubmitIntentRequest;
import com.group7.shop.dto.TradeResult;
import com.group7.shop.dto.TransactionView;
import com.group7.shop.entity.FailedAction;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 库存与商品状态规则测试（规则域负责人：华天遥）。
 * 覆盖 Wiki《业务流程图》v3 商品状态机：多商品并存、库存唯一入口、库存 0 自动下架、
 * 成交扣库存并直接下架、失败恢复在售并按 failedAction 作废/重新排队、重新上架校验库存。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StockStatusRuleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductService productService;
    @Autowired private ProductTransactionService transactionService;
    @Autowired private IntentService intentService;
    @Autowired private ProductRepository productRepository;
    @Autowired private IntentRepository intentRepository;
    @Autowired private SellerService sellerService;

    /* ---------- 辅助方法 ---------- */

    private Long publishWithStock(int stock) {
        return productService.publish(new PublishProductRequest(
                "测试商品", "状态机测试", new BigDecimal("99.00"), null, stock)).id();
    }

    private Long submitIntent(Long productId, String name) {
        intentService.submit(new SubmitIntentRequest(name, "13800000001"));
        return intentRepository.findByProductIdAndStatusOrderBySubmittedAtAscIdAsc(
                        productId, IntentStatus.WAITING).stream()
                .filter(i -> i.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }

    private Product product(Long id) {
        return productRepository.findById(id).orElseThrow();
    }

    private Intent intent(Long id) {
        return intentRepository.findById(id).orElseThrow();
    }

    private void assertBizError(Runnable action, int status, String message) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(status, ex.getStatus().value(), "HTTP 状态码不符");
        assertEquals(message, ex.getMessage(), "错误文案不符");
    }

    /* ---------- 库存 ---------- */

    @Test
    @DisplayName("TC-S01 修改库存成功：在售商品库存更新且保持状态")
    void updateStockOnSale() {
        Long id = publishWithStock(3);
        var view = productService.updateStock(id, 8);
        assertEquals(8, view.stock());
        assertEquals(ProductStatus.ON_SALE, view.status());
        assertEquals(8, product(id).getStock());
    }

    @Test
    @DisplayName("TC-S02 库存改为 0：在售商品自动下架，剩余排队意向全部置失败")
    void stockZeroAutoOffShelf() {
        Long id = publishWithStock(3);
        Long first = submitIntent(id, "张三");
        Long second = submitIntent(id, "李四");

        var view = productService.updateStock(id, 0);

        assertEquals(ProductStatus.OFF_SHELF, view.status());
        assertNotNull(view.closedAt());
        assertEquals(IntentStatus.FAILED, intent(first).getStatus());
        assertEquals(IntentStatus.FAILED, intent(second).getStatus());
    }

    @Test
    @DisplayName("TC-S03 库存为负数：400「库存不能为负数」")
    void negativeStockRejected() {
        Long id = publishWithStock(3);
        assertBizError(() -> productService.updateStock(id, -1), 400, "库存不能为负数");
    }

    @Test
    @DisplayName("TC-S04 交易中修改库存：400，须先标记交易结果")
    void stockLockedDuringTrade() {
        Long id = publishWithStock(2);
        Long intentId = submitIntent(id, "张三");
        transactionService.selectBuyer(id, intentId);

        assertBizError(() -> productService.updateStock(id, 5), 400,
                "交易中不可修改库存，请先标记交易结果");
    }

    /* ---------- 重新上架 ---------- */

    @Test
    @DisplayName("TC-S05 重新上架：下架且有库存 → 在售，关闭时间清空")
    void reshelfWithStock() {
        Long id = publishWithStock(2);
        productService.offshelf(id);
        assertEquals(ProductStatus.OFF_SHELF, product(id).getStatus());

        var view = productService.reshelf(id);
        assertEquals(ProductStatus.ON_SALE, view.status());
        assertNull(view.closedAt());
        assertNull(product(id).getFrozenBy());
    }

    @Test
    @DisplayName("TC-S06 重新上架库存为 0：400「库存为 0，请先补货再上架」")
    void reshelfRejectedWithoutStock() {
        Long id = publishWithStock(0);
        productService.offshelf(id);

        assertBizError(() -> productService.reshelf(id), 400, "库存为 0，请先补货再上架");
    }

    @Test
    @DisplayName("TC-S07 在售商品重新上架：409")
    void reshelfRejectedWhenOnSale() {
        Long id = publishWithStock(2);
        assertBizError(() -> productService.reshelf(id), 409, "仅已下架商品可以重新上架");
    }

    /* ---------- 交易结果 ---------- */

    @Test
    @DisplayName("TC-S08 交易成功：库存扣 1、商品直接下架、其余意向全部失败")
    void successDeductsStockAndOffShelf() {
        Long id = publishWithStock(2);
        Long winner = submitIntent(id, "张三");
        Long loserA = submitIntent(id, "李四");
        Long loserB = submitIntent(id, "王五");
        transactionService.selectBuyer(id, winner);

        TransactionView result = transactionService.markResult(
                id, TradeResult.SUCCESS, null);

        assertEquals(IntentStatus.TRADED, result.intentStatus());
        assertEquals(ProductStatus.OFF_SHELF, result.product().status());
        assertEquals(1, result.product().stock());
        assertEquals(1, product(id).getStock());
        assertEquals(IntentStatus.TRADED, intent(winner).getStatus());
        assertEquals(IntentStatus.FAILED, intent(loserA).getStatus());
        assertEquals(IntentStatus.FAILED, intent(loserB).getStatus());
    }

    @Test
    @DisplayName("TC-S09 交易失败-VOID：商品恢复在售、库存不变、被选意向置失败、其余保持排队")
    void failVoidRestoresOnSale() {
        Long id = publishWithStock(2);
        Long chosen = submitIntent(id, "张三");
        Long other = submitIntent(id, "李四");
        transactionService.selectBuyer(id, chosen);

        TransactionView result = transactionService.markResult(
                id, TradeResult.FAIL, FailedAction.VOID);

        assertEquals(IntentStatus.FAILED, result.intentStatus());
        assertEquals(ProductStatus.ON_SALE, result.product().status());
        assertEquals(2, result.product().stock());
        assertNull(product(id).getFrozenBy());
        assertEquals(IntentStatus.FAILED, intent(chosen).getStatus());
        assertEquals(IntentStatus.WAITING, intent(other).getStatus());
    }

    @Test
    @DisplayName("TC-S10 交易失败-REQUEUE：被选意向回到队尾，商品恢复在售")
    void failRequeueGoesToQueueTail() {
        Long id = publishWithStock(2);
        Long chosen = submitIntent(id, "张三");
        submitIntent(id, "李四");
        submitIntent(id, "王五");
        transactionService.selectBuyer(id, chosen);

        TransactionView result = transactionService.markResult(
                id, TradeResult.FAIL, FailedAction.REQUEUE);

        assertEquals(IntentStatus.WAITING, result.intentStatus());
        assertEquals(ProductStatus.ON_SALE, result.product().status());

        List<BuyerIntentView> queue = intentService.activeQueue(id);
        assertEquals(3, queue.size());
        assertEquals("张三", queue.get(queue.size() - 1).name(), "重新排队应排到队尾");
        assertEquals(3, queue.get(queue.size() - 1).position());
    }

    @Test
    @DisplayName("TC-S11 交易失败缺 failedAction：400")
    void failRequiresFailedAction() {
        Long id = publishWithStock(2);
        Long chosen = submitIntent(id, "张三");
        transactionService.selectBuyer(id, chosen);

        assertBizError(() -> transactionService.markResult(id, TradeResult.FAIL, null),
                400, "交易失败时 failedAction 只能为 VOID 或 REQUEUE");
    }

    @Test
    @DisplayName("TC-S12 非交易中商品标记结果：409")
    void resultRejectedWhenNotInTrade() {
        Long id = publishWithStock(2);
        assertBizError(() -> transactionService.markResult(id, TradeResult.SUCCESS, null),
                409, "商品当前没有进行中的交易");
    }

    @Test
    @DisplayName("TC-S13 多商品并存：连续发布两件均在售，状态互不影响")
    void multipleProductsIndependent() {
        Long first = publishWithStock(1);
        Long second = publishWithStock(1);

        assertEquals(ProductStatus.ON_SALE, product(first).getStatus());
        assertEquals(ProductStatus.ON_SALE, product(second).getStatus());

        productService.offshelf(first);
        assertEquals(ProductStatus.OFF_SHELF, product(first).getStatus());
        assertEquals(ProductStatus.ON_SALE, product(second).getStatus());
    }

    @Test
    @DisplayName("TC-S14 卖家接口：PUT stock / POST reshelf / POST result 均按契约返回")
    void sellerEndpoints() throws Exception {
        LoginResponse login = sellerService.login("seller", "12345678");
        String token = login.token();

        // 1) 修改库存
        Long stockProduct = publishWithStock(1);
        String stockBody = objectMapper.writeValueAsString(Map.of("stock", 5));
        mockMvc.perform(put("/api/seller/products/" + stockProduct + "/stock")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.stock").value(5));

        // 2) 重新上架
        Long reshelfProduct = publishWithStock(1);
        productService.offshelf(reshelfProduct);
        mockMvc.perform(post("/api/seller/products/" + reshelfProduct + "/reshelf")
                        .header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        // 3) 标记交易结果
        Long tradeProduct = publishWithStock(1);
        Long intentId = submitIntent(tradeProduct, "张三");
        transactionService.selectBuyer(tradeProduct, intentId);
        mockMvc.perform(post("/api/seller/products/" + tradeProduct + "/result")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("result", "SUCCESS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intentStatus").value("TRADED"))
                .andExpect(jsonPath("$.data.product.status").value("OFF_SHELF"))
                .andExpect(jsonPath("$.data.product.stock").value(0));
    }
}
