package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.BuyerIntentView;
import com.group7.shop.dto.PublishProductRequest;
import com.group7.shop.dto.TradeResult;
import com.group7.shop.entity.Category;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.CategoryRepository;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发边界测试（迭代 3，规则域负责人：华天遥）。
 * 覆盖：同一商品并发标记成功只能成功一笔、不同商品并发交易互不阻塞、同一毫秒提交的先到先得次序。
 * <p>本类不使用 @Transactional：并发线程必须看到已提交数据，测试结束手动清理。</p>
 */
@SpringBootTest
class StockConcurrencyTest {

    @Autowired private ProductService productService;
    @Autowired private ProductTransactionService transactionService;
    @Autowired private IntentService intentService;
    @Autowired private ProductRepository productRepository;
    @Autowired private IntentRepository intentRepository;
    @Autowired private CategoryRepository categoryRepository;

    private final List<Long> createdProducts = new ArrayList<>();
    private final List<Long> createdIntents = new ArrayList<>();
    private Long categoryId;

    @AfterEach
    void cleanUp() {
        createdIntents.forEach(id -> intentRepository.deleteById(id));
        createdProducts.forEach(id -> productRepository.deleteById(id));
        createdIntents.clear();
        createdProducts.clear();
    }

    /* ---------- 辅助方法 ---------- */

    private Long categoryId() {
        if (categoryId == null) {
            Category root = categoryRepository.save(new Category("并发测试一级", null, 0));
            categoryId = categoryRepository.save(new Category("并发测试二级", root.getId(), 0)).getId();
        }
        return categoryId;
    }

    private Long publish(int stock, String name) {
        Long id = productService.publish(new PublishProductRequest(
                name, "并发测试", new BigDecimal("10.00"), categoryId(), stock, null, null)).id();
        createdProducts.add(id);
        return id;
    }

    /** 直接落库建意向：IntentService.submit 会绑定「当前在售商品」，多商品场景下不适用。 */
    private Long newIntent(Long productId, String name, Instant submittedAt) {
        String codeHash = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        Intent intent = intentRepository.save(
                new Intent(productId, name, "13800000009", codeHash, submittedAt));
        createdIntents.add(intent.getId());
        return intent.getId();
    }

    private List<String> runConcurrently(List<Callable<String>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier barrier = new CyclicBarrier(tasks.size());
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String markSuccess(Long productId) {
        try {
            transactionService.markResult(productId, TradeResult.SUCCESS, null);
            return "OK";
        } catch (BusinessException ex) {
            return ex.getStatus().value() + ":" + ex.getMessage();
        }
    }

    /* ---------- 场景 ---------- */

    @Test
    @DisplayName("TC-S21 同一商品并发标记成功：仅一笔生效，库存只扣 1 件，另一笔 409")
    void concurrentSuccessDeductsOnce() throws Exception {
        Long id = publish(5, "并发商品A");
        Long winner = newIntent(id, "甲", Instant.now());
        newIntent(id, "乙", Instant.now().plusMillis(1));
        transactionService.selectBuyer(id, winner);

        List<String> results = runConcurrently(List.of(
                () -> markSuccess(id),
                () -> markSuccess(id)));

        assertEquals(1, Collections.frequency(results, "OK"), "只能有一笔成交成功");
        long conflicts = results.stream().filter(r -> r.startsWith("409")).count();
        assertEquals(1, conflicts, "另一笔应返回 409 而不是 500 或重复成交: " + results);

        Product saved = productRepository.findById(id).orElseThrow();
        assertEquals(4, saved.getStock(), "库存只能扣 1 件");
        assertEquals(ProductStatus.OFF_SHELF, saved.getStatus());
        assertEquals(1, intentRepository.findByProductIdAndStatus(
                id, IntentStatus.TRADED).size());
    }

    @Test
    @DisplayName("TC-S22 两件商品并发交易：互不阻塞，各自冻结并成交")
    void twoProductsTradeIndependently() throws Exception {
        Long a = publish(2, "并发商品B1");
        Long b = publish(2, "并发商品B2");
        Long intentA = newIntent(a, "甲", Instant.now());
        Long intentB = newIntent(b, "乙", Instant.now());

        List<String> frozen = runConcurrently(List.of(
                () -> {
                    transactionService.selectBuyer(a, intentA);
                    return "OK";
                },
                () -> {
                    transactionService.selectBuyer(b, intentB);
                    return "OK";
                }));
        assertTrue(frozen.stream().allMatch("OK"::equals), "两件商品应能同时进入交易: " + frozen);

        List<String> results = runConcurrently(List.of(
                () -> markSuccess(a),
                () -> markSuccess(b)));

        assertEquals(2, Collections.frequency(results, "OK"), "两笔成交都应成功: " + results);
        assertEquals(1, productRepository.findById(a).orElseThrow().getStock());
        assertEquals(1, productRepository.findById(b).orElseThrow().getStock());
        assertEquals(ProductStatus.OFF_SHELF, productRepository.findById(a).orElseThrow().getStatus());
        assertEquals(ProductStatus.OFF_SHELF, productRepository.findById(b).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("TC-S23 同一毫秒提交的意向：位次按 id 兜底，先到先得次序稳定")
    void sameInstantIntentsOrderedById() {
        Long id = publish(3, "并发商品C");
        Instant sameMoment = Instant.parse("2026-01-01T00:00:00Z");
        Long first = newIntent(id, "第一", sameMoment);
        Long second = newIntent(id, "第二", sameMoment);
        Long third = newIntent(id, "第三", sameMoment);

        List<BuyerIntentView> queue = intentService.activeQueue(id);

        assertEquals(3, queue.size());
        assertEquals("第一", queue.get(0).name());
        assertEquals("第二", queue.get(1).name());
        assertEquals("第三", queue.get(2).name());
        assertEquals(1, queue.get(0).position());
        assertEquals(3, queue.get(2).position());

        // 买家视图不含 id，按服务端排序规则直接核对底层次序
        List<Intent> ordered = intentRepository
                .findByProductIdAndStatusOrderBySubmittedAtAscIdAsc(id, IntentStatus.WAITING);
        assertEquals(List.of(first, second, third),
                ordered.stream().map(Intent::getId).toList());
    }
}
