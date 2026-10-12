package com.group7.shop;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.ProductHistoryView;
import com.group7.shop.dto.ProductView;
import com.group7.shop.dto.PublishProductRequest;
import com.group7.shop.dto.TradeResult;
import com.group7.shop.dto.TransactionView;
import com.group7.shop.entity.FrozenBy;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.repository.ProductRepository;
import com.group7.shop.service.ProductService;
import com.group7.shop.service.ProductImageStorage;
import com.group7.shop.service.ProductTransactionService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductWorkflowIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductTransactionService transactionService;

    @Autowired
    private ProductImageStorage imageStorage;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private IntentRepository intentRepository;

    @Autowired
    private Validator validator;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
        intentRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void tc14PublishProduct() {
        ProductView product = publish("相机", "九成新", "1999.00");

        assertThat(product.id()).isNotNull();
        assertThat(product.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.imageUrl()).isEqualTo("/placeholder.png");
        assertThat(productService.currentBuyerProduct().id()).isEqualTo(product.id());
    }

    @Test
    void tc15RejectDuplicatePublish() {
        publish("商品一", "", "10.00");

        assertThatThrownBy(() -> publish("商品二", "", "20.00"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("已有商品在售或交易中");
                });
    }

    @Test
    void tc16RejectNonPositivePriceAndMoreThanTwoDecimals() {
        PublishProductRequest tooPrecise = new PublishProductRequest(
                "商品", "", new BigDecimal("1.001"), null);
        PublishProductRequest zero = new PublishProductRequest(
                "商品", "", BigDecimal.ZERO, null);

        Set<ConstraintViolation<PublishProductRequest>> precisionViolations =
                validator.validate(tooPrecise);
        Set<ConstraintViolation<PublishProductRequest>> zeroViolations = validator.validate(zero);

        assertThat(precisionViolations)
                .extracting(ConstraintViolation::getMessage)
                .contains("商品价格最多8位整数和2位小数");
        assertThat(zeroViolations)
                .extracting(ConstraintViolation::getMessage)
                .contains("商品价格必须大于0");
    }

    @Test
    void tc17RejectImageLargerThanFiveMegabytes() {
        String oversizedPng = "data:image/png;base64," + "A".repeat(7 * 1024 * 1024);

        assertThatThrownBy(() -> imageStorage.storeOrPlaceholder(oversizedPng))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("不超过5MB");
                });
    }

    @Test
    void tc22FreezeAndUnfreezeManually() {
        ProductView product = publish("商品", "", "10.00");

        ProductView frozen = productService.freezeManually(product.id());
        ProductView restored = productService.unfreezeManually(product.id());

        assertThat(frozen.status()).isEqualTo(ProductStatus.FROZEN);
        assertThat(frozen.frozenBy()).isEqualTo(FrozenBy.MANUAL);
        assertThat(restored.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(restored.frozenBy()).isNull();
    }

    @Test
    void tc19SelectBuyerFreezesProductAndStartsTrade() {
        ProductView product = publish("商品", "", "10.00");
        Intent intent = waitingIntent(product.id(), "买家甲", "13800000000", "hash-a");

        TransactionView result = transactionService.selectBuyer(product.id(), intent.getId());

        assertThat(result.product().status()).isEqualTo(ProductStatus.FROZEN);
        assertThat(result.product().frozenBy()).isEqualTo(FrozenBy.AUTO);
        assertThat(result.intentStatus()).isEqualTo(IntentStatus.IN_TRADE);
    }

    @Test
    void tc23TradeFreezeCannotBeUnfrozenManually() {
        ProductView product = publish("商品", "", "10.00");
        Intent intent = waitingIntent(product.id(), "买家甲", "13800000000", "hash-a");
        transactionService.selectBuyer(product.id(), intent.getId());

        assertThatThrownBy(() -> productService.unfreezeManually(product.id()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("交易中的商品不能手动解冻");
                });
    }

    @Test
    void tc20SuccessfulTradeClosesProductAndFailsRemainingQueue() {
        ProductView product = publish("商品", "", "10.00");
        Intent selected = waitingIntent(product.id(), "买家甲", "13800000000", "hash-a");
        Intent waiting = waitingIntent(product.id(), "买家乙", "13900000000", "hash-b");
        transactionService.selectBuyer(product.id(), selected.getId());

        TransactionView result = transactionService.markResult(product.id(), TradeResult.SUCCESS);

        assertThat(result.product().status()).isEqualTo(ProductStatus.OFF_SHELF);
        assertThat(result.intentStatus()).isEqualTo(IntentStatus.TRADED);
        assertThat(intentRepository.findById(waiting.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentStatus.FAILED);
        assertThat(productService.currentBuyerProduct()).isNull();
        assertThat(productService.history())
                .extracting(ProductHistoryView::historyType)
                .containsExactly("SOLD");
    }

    @Test
    void tc21FailedTradeRestoresProductAndKeepsRemainingQueue() {
        ProductView product = publish("商品", "", "10.00");
        Intent selected = waitingIntent(product.id(), "买家甲", "13800000000", "hash-a");
        Intent waiting = waitingIntent(product.id(), "买家乙", "13900000000", "hash-b");
        transactionService.selectBuyer(product.id(), selected.getId());

        TransactionView result = transactionService.markResult(product.id(), TradeResult.FAIL);

        assertThat(result.product().status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(result.product().frozenBy()).isNull();
        assertThat(result.intentStatus()).isEqualTo(IntentStatus.FAILED);
        assertThat(intentRepository.findById(waiting.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentStatus.WAITING);
        assertThat(productService.history())
                .extracting(ProductHistoryView::historyType)
                .containsExactly("TRADE_FAILED");
    }

    @Test
    void tc26HistoryContainsManualOffShelfAndFailedTradeProducts() {
        ProductView failedProduct = publish("交易失败商品", "", "10.00");
        Intent selected = waitingIntent(
                failedProduct.id(), "买家甲", "13800000000", "hash-a");
        transactionService.selectBuyer(failedProduct.id(), selected.getId());
        transactionService.markResult(failedProduct.id(), TradeResult.FAIL);
        productService.offshelf(failedProduct.id());

        ProductView manualProduct = publish("手动下架商品", "", "20.00");
        Intent waiting = waitingIntent(
                manualProduct.id(), "买家乙", "13900000000", "hash-b");
        productService.offshelf(manualProduct.id());

        List<ProductHistoryView> history = productService.history();

        assertThat(history).hasSize(2);
        assertThat(history).extracting(ProductHistoryView::historyType)
                .containsExactlyInAnyOrder("MANUAL", "MANUAL");
        assertThat(history).filteredOn(ProductHistoryView::hadTradeFailure).hasSize(1);
        assertThat(intentRepository.findById(waiting.getId()).orElseThrow().getStatus())
                .isEqualTo(IntentStatus.FAILED);
    }

    @Test
    void rejectsIntentThatBelongsToAnotherProduct() {
        ProductView current = publish("当前商品", "", "10.00");
        Intent foreign = waitingIntent(999L, "买家", "13800000000", "hash-x");

        assertThatThrownBy(() -> transactionService.selectBuyer(current.id(), foreign.getId()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("购买意向不属于该商品");
                });
    }

    @Test
    void rejectsResultWhenThereIsNoActiveTrade() {
        ProductView product = publish("商品", "", "10.00");

        assertThatThrownBy(() -> transactionService.markResult(product.id(), TradeResult.SUCCESS))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("商品当前没有进行中的交易");
                });
    }

    @Test
    void sellerTransactionEndpointsUseUnifiedResponseContract() throws Exception {
        ProductView product = publish("商品", "", "10.00");
        Intent intent = waitingIntent(product.id(), "买家甲", "13800000000", "hash-api");

        mockMvc.perform(post("/api/seller/products/{id}/deal", product.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intentId\":" + intent.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.product.status").value("FROZEN"))
                .andExpect(jsonPath("$.data.intentStatus").value("IN_TRADE"));

        mockMvc.perform(post("/api/seller/products/{id}/result", product.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"FAIL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.product.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.intentStatus").value("FAILED"));
    }

    @Test
    void historyEndpointKeepsProductFieldsAtItemLevel() throws Exception {
        ProductView product = publish("历史商品", "接口契约", "88.00");
        productService.offshelf(product.id());

        mockMvc.perform(get("/api/seller/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].id").value(product.id()))
                .andExpect(jsonPath("$.data.items[0].name").value("历史商品"))
                .andExpect(jsonPath("$.data.items[0].historyType").value("MANUAL"))
                .andExpect(jsonPath("$.data.items[0].product").doesNotExist());
    }

    private ProductView publish(String name, String description, String price) {
        return productService.publish(new PublishProductRequest(
                name, description, new BigDecimal(price), null));
    }

    private Intent waitingIntent(Long productId, String name, String phone, String hash) {
        return intentRepository.save(new Intent(productId, name, phone, hash, Instant.now()));
    }
}
