package com.group7.shop.query;

import com.group7.shop.dto.PublishProductRequest;
import com.group7.shop.dto.RegisterRequest;
import com.group7.shop.entity.Category;
import com.group7.shop.entity.Customer;
import com.group7.shop.entity.CustomerOrderLink;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductImage;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.CategoryRepository;
import com.group7.shop.repository.CustomerOrderLinkRepository;
import com.group7.shop.repository.CustomerRepository;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.repository.ProductImageRepository;
import com.group7.shop.repository.ProductRepository;
import com.group7.shop.service.CustomerService;
import com.group7.shop.service.ProductService;
import com.group7.shop.service.SellerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BrowseAndCustomerQueryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductImageRepository imageRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerOrderLinkRepository orderLinkRepository;
    @Autowired private IntentRepository intentRepository;
    @Autowired private ProductService productService;
    @Autowired private CustomerService customerService;
    @Autowired private SellerService sellerService;

    private Long categoryA;
    private Long categoryB;

    @BeforeEach
    void cleanDatabase() {
        orderLinkRepository.deleteAll();
        intentRepository.deleteAll();
        imageRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
        categoryRepository.deleteAll();

        Category root = categoryRepository.save(new Category("数码", null, 0));
        categoryA = categoryRepository.save(new Category("相机", root.getId(), 0)).getId();
        categoryB = categoryRepository.save(new Category("电脑", root.getId(), 1)).getId();
    }

    @Test
    @DisplayName("商品列表默认每页 12 条，只返回在售商品并按发布时间倒序")
    void listDefaultsToTwelveOnSaleProducts() throws Exception {
        Instant base = Instant.parse("2026-10-21T00:00:00Z");
        for (int i = 0; i < 13; i++) {
            saveProduct("商品" + i, "普通描述", i + 1, categoryA,
                    ProductStatus.ON_SALE, base.plusSeconds(i));
        }
        saveProduct("冻结商品", "不应出现", 99, categoryA,
                ProductStatus.FROZEN, base.plusSeconds(100));
        saveProduct("下架商品", "不应出现", 100, categoryA,
                ProductStatus.OFF_SHELF, base.plusSeconds(101));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(12))
                .andExpect(jsonPath("$.data.total").value(13))
                .andExpect(jsonPath("$.data.items.length()").value(12))
                .andExpect(jsonPath("$.data.items[0].name").value("商品12"))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("/images/商品12.png"))
                .andExpect(jsonPath("$.data.items[0].images[0]").value("/images/商品12.png"))
                .andExpect(jsonPath("$.data.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].lastTradeFailedAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].closeReason").doesNotExist())
                .andExpect(jsonPath("$.data.items[11].name").value("商品1"));

        mockMvc.perform(get("/api/products").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(13))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("商品0"));
    }

    @Test
    @DisplayName("关键词搜索名称和描述，可与类别筛选及价格排序组合")
    void searchCategoryAndPriceSortCanBeCombined() throws Exception {
        saveProduct("Camera 入门机", "适合旅行", 3000, categoryA,
                ProductStatus.ON_SALE, Instant.parse("2026-10-21T01:00:00Z"));
        saveProduct("旅行镜头", "兼容 CAMERA 机身", 1200, categoryA,
                ProductStatus.ON_SALE, Instant.parse("2026-10-21T02:00:00Z"));
        saveProduct("Camera 电脑摄像头", "网络会议", 200, categoryB,
                ProductStatus.ON_SALE, Instant.parse("2026-10-21T03:00:00Z"));
        saveProduct("普通镜头", "无关描述", 100, categoryA,
                ProductStatus.ON_SALE, Instant.parse("2026-10-21T04:00:00Z"));

        mockMvc.perform(get("/api/products")
                        .param("q", "  camera ")
                        .param("categoryId", categoryA.toString())
                        .param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].name").value("旅行镜头"))
                .andExpect(jsonPath("$.data.items[1].name").value("Camera 入门机"));

        mockMvc.perform(get("/api/products").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].price").value(3000));
    }

    @Test
    @DisplayName("空关键词等同全部商品，百分号按普通字符搜索")
    void blankAndWildcardKeywordsAreHandledSafely() throws Exception {
        saveProduct("九成新 100%", "正常描述", 10, categoryA,
                ProductStatus.ON_SALE, Instant.parse("2026-10-21T01:00:00Z"));
        saveProduct("普通商品", "正常描述", 20, categoryA,
                ProductStatus.ON_SALE, Instant.parse("2026-10-21T02:00:00Z"));

        mockMvc.perform(get("/api/products").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/products").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("九成新 100%"));
    }

    @Test
    @DisplayName("非法分页、分类和排序参数返回统一 400")
    void invalidListParametersReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/products").param("categoryId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/products").param("sort", "name,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("商品详情返回有序多图与过滤后的富文本")
    void detailReturnsOrderedImagesAndSanitizedRichText() throws Exception {
        var view = productService.publish(new PublishProductRequest(
                "富文本相机",
                "<p><strong>安全内容</strong></p><script>alert(1)</script><img src=\"https://example.com/a.png\" onerror=\"alert(2)\">",
                new BigDecimal("1888.00"), categoryA, 2, null, null));
        imageRepository.deleteAll();
        imageRepository.save(new ProductImage(view.id(), "/uploads/third.png", 2));
        imageRepository.save(new ProductImage(view.id(), "/uploads/first.png", 0));
        imageRepository.save(new ProductImage(view.id(), "/uploads/second.png", 1));

        mockMvc.perform(get("/api/products/{id}", view.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.images[0]").value("/uploads/first.png"))
                .andExpect(jsonPath("$.data.images[1]").value("/uploads/second.png"))
                .andExpect(jsonPath("$.data.images[2]").value("/uploads/third.png"))
                .andExpect(jsonPath("$.data.description").value(containsString("安全内容")))
                .andExpect(jsonPath("$.data.description").value(not(containsString("script"))))
                .andExpect(jsonPath("$.data.description").value(not(containsString("onerror"))));
    }

    @Test
    @DisplayName("不存在或非在售商品的详情返回 404")
    void detailRejectsMissingOrUnavailableProduct() throws Exception {
        Product offShelf = saveProduct("已下架", "", 10, categoryA,
                ProductStatus.OFF_SHELF, Instant.now());

        mockMvc.perform(get("/api/products/{id}", offShelf.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(get("/api/products/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("商品详情在多图记录缺失时回退旧封面字段")
    void detailFallsBackToLegacyCoverImage() throws Exception {
        Product product = saveProduct("存量商品", "旧数据", 10, categoryA,
                ProductStatus.ON_SALE, Instant.now());

        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value("/images/存量商品.png"))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.images[0]").value("/images/存量商品.png"));
    }

    @Test
    @DisplayName("卖家客户列表需要卖家 token，返回全部客户、脱敏电话和终态统计")
    void sellerCustomerListIsProtectedAndMasked() throws Exception {
        Customer first = register("buyer_a", "13800001234");
        Customer second = register("buyer_b", "05718888");
        register("buyer_empty", "1234");

        Product product = saveProduct("测试商品", "", 10, categoryA,
                ProductStatus.ON_SALE, Instant.now());
        link(first, intent(product, "success-a", Instant.now().minusSeconds(30), "TRADED"));
        link(first, intent(product, "waiting-a", Instant.now().minusSeconds(10), "WAITING"));
        link(second, intent(product, "failed-b", Instant.now().minusSeconds(20), "FAILED"));

        mockMvc.perform(get("/api/seller/customers"))
                .andExpect(status().isUnauthorized());

        String sellerToken = sellerService.login("seller", "12345678").token();
        mockMvc.perform(get("/api/seller/customers").header("X-Token", sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].username").value("buyer_empty"))
                .andExpect(jsonPath("$.data.items[0].phone").value("****"))
                .andExpect(jsonPath("$.data.items[0].orderCount").value(0))
                .andExpect(jsonPath("$.data.items[1].username").value("buyer_b"))
                .andExpect(jsonPath("$.data.items[1].phone").value("05****88"))
                .andExpect(jsonPath("$.data.items[1].failureCount").value(1))
                .andExpect(jsonPath("$.data.items[2].username").value("buyer_a"))
                .andExpect(jsonPath("$.data.items[2].phone").value("138****1234"))
                .andExpect(jsonPath("$.data.items[2].successCount").value(1))
                .andExpect(jsonPath("$.data.items[2].orderCount").value(1))
                .andExpect(jsonPath("$.data.items[2].defaultTradePlace").doesNotExist())
                .andExpect(content().string(not(containsString("13800001234"))));
    }

    @Test
    @DisplayName("客户购买历史只返回该客户的成功和失败记录")
    void sellerCustomerHistoryOnlyReturnsFinishedOrdersOfSelectedCustomer() throws Exception {
        Customer selected = register("history_owner", "13900001234");
        Customer other = register("history_other", "13700001234");
        Instant now = Instant.now();

        Product successProduct = saveProduct("成交相机", "", 100, categoryA,
                ProductStatus.ON_SALE, now.minusSeconds(500));
        successProduct.closeAfterTradeSuccess();
        productRepository.save(successProduct);

        Product failedProduct = saveProduct("失败镜头", "", 50, categoryA,
                ProductStatus.ON_SALE, now.minusSeconds(400));
        failedProduct.restoreAfterTradeFailure();
        productRepository.save(failedProduct);

        Intent success = intent(successProduct, "owner-success", now.minusSeconds(30), "TRADED");
        Intent failed = intent(failedProduct, "owner-failed", now.minusSeconds(300), "FAILED");
        Intent waiting = intent(failedProduct, "owner-waiting", now.minusSeconds(10), "WAITING");
        Intent others = intent(successProduct, "other-success", now.minusSeconds(20), "TRADED");
        link(selected, success);
        link(selected, failed);
        link(selected, waiting);
        link(other, others);

        String sellerToken = sellerService.login("seller", "12345678").token();
        mockMvc.perform(get("/api/seller/customers/{id}/orders", selected.getId())
                        .header("X-Token", sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(selected.getId()))
                .andExpect(jsonPath("$.data.username").value("history_owner"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].productName").value("成交相机"))
                .andExpect(jsonPath("$.data.items[1].result").value("FAIL"))
                .andExpect(jsonPath("$.data.items[1].productName").value("失败镜头"));
    }

    @Test
    @DisplayName("客户 token 不能访问卖家客户接口，不存在客户返回 404")
    void sellerCustomerQueriesEnforceRoleAndNotFound() throws Exception {
        register("role_customer", "13600001234");
        String customerToken = customerService.login("role_customer", "password123").token();
        mockMvc.perform(get("/api/seller/customers").header("X-Token", customerToken))
                .andExpect(status().isUnauthorized());

        String sellerToken = sellerService.login("seller", "12345678").token();
        mockMvc.perform(get("/api/seller/customers/{id}/orders", 999999L)
                        .header("X-Token", sellerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    private Product saveProduct(String name,
                                String description,
                                int price,
                                Long categoryId,
                                ProductStatus status,
                                Instant createdAt) {
        Product product = new Product(name, description, BigDecimal.valueOf(price),
                "/images/" + name + ".png", categoryId, 1);
        product.setStatus(status);
        ReflectionTestUtils.setField(product, "createdAt", createdAt);
        return productRepository.save(product);
    }

    private Customer register(String username, String phone) {
        customerService.register(new RegisterRequest(
                username, "password123", phone, "东门"));
        return customerRepository.findByUsernameIgnoreCase(username).orElseThrow();
    }

    private Intent intent(Product product, String hash, Instant submittedAt, String status) {
        Intent intent = new Intent(product.getId(), "测试买家", "13500000000", hash, submittedAt);
        switch (status) {
            case "TRADED" -> intent.markTraded();
            case "FAILED" -> intent.markFailed();
            case "WAITING" -> { }
            default -> throw new IllegalArgumentException("未知测试状态: " + status);
        }
        return intentRepository.save(intent);
    }

    private void link(Customer customer, Intent intent) {
        orderLinkRepository.save(new CustomerOrderLink(
                customer.getId(), intent.getId(), intent.getProductId(), intent.getSubmittedAt()));
    }
}
