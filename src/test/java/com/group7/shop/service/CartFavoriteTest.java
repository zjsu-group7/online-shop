package com.group7.shop.service;

import com.group7.shop.dto.PublishProductRequest;
import com.group7.shop.dto.RegisterRequest;
import com.group7.shop.dto.CustomerLoginResponse;
import com.group7.shop.dto.CustomerView;
import com.group7.shop.entity.Category;
import com.group7.shop.entity.Product;
import com.group7.shop.repository.CategoryRepository;
import com.group7.shop.repository.CustomerRepository;
import com.group7.shop.repository.FavoriteRepository;
import com.group7.shop.repository.CartItemRepository;
import com.group7.shop.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 购物车与收藏边界测试（迭代 4，华天遥；表结构牵头：李婉婷）。
 * 覆盖：加购累加、下架/无货实时标注与恢复、越权防护、账号隔离、收藏去重幂等、购物车转收藏（UC-06）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartFavoriteTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductService productService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private FavoriteRepository favoriteRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerService customerService;

    private final List<Long> customerIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private Long categoryId;

    @BeforeEach
    void setUpCategory() {
        if (categoryId == null) {
            Category root = categoryRepository.save(new Category("购物车测试一级", null, 0));
            categoryId = categoryRepository.save(new Category("购物车测试二级", root.getId(), 0)).getId();
        }
    }

    @AfterEach
    void cleanUp() {
        customerIds.forEach(cid -> {
            cartItemRepository.findByCustomerIdOrderByIdAsc(cid).forEach(cartItemRepository::delete);
            favoriteRepository.findByCustomerIdOrderByIdDesc(cid).forEach(favoriteRepository::delete);
        });
        productIds.forEach(productRepository::deleteById);
        customerIds.forEach(customerRepository::deleteById);
        customerIds.clear();
        productIds.clear();
    }

    /* ---------- 辅助方法 ---------- */

    private String newCustomer(String tag) {
        String username = "cf_" + tag + "_" + UUID.randomUUID().toString().substring(0, 8);
        CustomerView view = customerService.register(
                new RegisterRequest(username, "password123", "13900000000", null));
        customerIds.add(view.id());
        CustomerLoginResponse login = customerService.login(username, "password123");
        return login.token();
    }

    private Long publish(int stock, String name) {
        Long id = productService.publish(new PublishProductRequest(
                name, "购物车测试", new BigDecimal("10.00"), categoryId, stock, null, null)).id();
        productIds.add(id);
        return id;
    }

    private MvcResult postJson(String token, String url, String body) throws Exception {
        return mockMvc.perform(post(url)
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private String cartBody(Long productId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of("productId", productId, "quantity", quantity));
    }

    /* ---------- 购物车 ---------- */

    @Test
    @DisplayName("TC-CF01 加购成功：返回条目 id、数量与商品信息")
    void addToCart() throws Exception {
        String token = newCustomer("add");
        Long productId = publish(5, "加购商品");

        mockMvc.perform(post("/api/customer/cart")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartBody(productId, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.product.id").value(productId))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.unavailableReason").doesNotExist());
    }

    @Test
    @DisplayName("TC-CF02 重复加购同一商品：数量累加（2+3=5），不产生重复条目")
    void addToCartAccumulates() throws Exception {
        String token = newCustomer("acc");
        Long productId = publish(9, "累加商品");

        postJson(token, "/api/customer/cart", cartBody(productId, 2));
        postJson(token, "/api/customer/cart", cartBody(productId, 3));

        mockMvc.perform(get("/api/customer/cart").header("X-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5))
                .andExpect(jsonPath("$.data.kinds").value(1))
                .andExpect(jsonPath("$.data.totalQuantity").value(5));
    }

    @Test
    @DisplayName("TC-CF03 加购不存在的商品 → 404")
    void addMissingProduct() throws Exception {
        String token = newCustomer("miss");

        mockMvc.perform(post("/api/customer/cart")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartBody(999999L, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-CF04 加购已下架商品：允许加入并实时标注 OFF_SHELF（购物车不受库存限制）")
    void addOffShelfAnnotated() throws Exception {
        String token = newCustomer("off");
        Long productId = publish(1, "下架商品");
        productService.updateStock(productId, 0);   // 库存 0 自动下架

        mockMvc.perform(post("/api/customer/cart")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartBody(productId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason").value("OFF_SHELF"));
    }

    @Test
    @DisplayName("TC-CF05 在售但可售库存为 0（全部被订单锁定）：标注 OUT_OF_STOCK")
    void addLockedOutAnnotated() throws Exception {
        String token = newCustomer("lock");
        Long productId = publish(3, "锁定商品");
        Product product = productRepository.findById(productId).orElseThrow();
        product.lockStock(3);                        // 模拟全部库存被订单锁定
        productRepository.save(product);

        mockMvc.perform(post("/api/customer/cart")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartBody(productId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason").value("OUT_OF_STOCK"));
    }

    @Test
    @DisplayName("TC-CF06 下架标注的商品补货并重新上架后：标注自动清除、恢复可买")
    void annotationClearsAfterRestock() throws Exception {
        String token = newCustomer("rest");
        Long productId = publish(1, "恢复商品");
        productService.updateStock(productId, 0);    // 下架
        postJson(token, "/api/customer/cart", cartBody(productId, 1));

        mockMvc.perform(get("/api/customer/cart").header("X-Token", token))
                .andExpect(jsonPath("$.data.items[0].unavailableReason").value("OFF_SHELF"));

        // 卖家补货 + 重新上架
        productService.updateStock(productId, 6);
        productService.reshelf(productId);

        mockMvc.perform(get("/api/customer/cart").header("X-Token", token))
                .andExpect(jsonPath("$.data.items[0].available").value(true))
                .andExpect(jsonPath("$.data.items[0].unavailableReason").doesNotExist());
    }

    @Test
    @DisplayName("TC-CF07 修改数量成功")
    void updateQuantity() throws Exception {
        String token = newCustomer("upd");
        Long productId = publish(5, "改数量商品");
        MvcResult added = postJson(token, "/api/customer/cart", cartBody(productId, 1));
        long itemId = objectMapper.readTree(added.getResponse().getContentAsString())
                .path("data").path("itemId").asLong();

        mockMvc.perform(put("/api/customer/cart/" + itemId)
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(4));
    }

    @Test
    @DisplayName("TC-CF08 改数量为 0 → 400（删除请走删除接口）")
    void updateQuantityZero() throws Exception {
        String token = newCustomer("zero");
        Long productId = publish(5, "零数量商品");
        MvcResult added = postJson(token, "/api/customer/cart", cartBody(productId, 1));
        long itemId = objectMapper.readTree(added.getResponse().getContentAsString())
                .path("data").path("itemId").asLong();

        mockMvc.perform(put("/api/customer/cart/" + itemId)
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-CF09 越权防护：改/删他人购物车条目 → 404")
    void crossCustomerItemDenied() throws Exception {
        String tokenA = newCustomer("ownA");
        String tokenB = newCustomer("ownB");
        Long productId = publish(5, "越权商品");
        MvcResult added = postJson(tokenA, "/api/customer/cart", cartBody(productId, 1));
        long itemId = objectMapper.readTree(added.getResponse().getContentAsString())
                .path("data").path("itemId").asLong();

        mockMvc.perform(put("/api/customer/cart/" + itemId)
                        .header("X-Token", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 9))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/customer/cart/" + itemId).header("X-Token", tokenB))
                .andExpect(status().isNotFound());

        // A 自己的条目仍在、数量未变
        mockMvc.perform(get("/api/customer/cart").header("X-Token", tokenA))
                .andExpect(jsonPath("$.data.items[0].quantity").value(1));
    }

    @Test
    @DisplayName("TC-CF10 删除单条与一键清空")
    void removeAndClear() throws Exception {
        String token = newCustomer("rm");
        Long p1 = publish(5, "删除商品一");
        Long p2 = publish(5, "删除商品二");
        MvcResult added = postJson(token, "/api/customer/cart", cartBody(p1, 1));
        long itemId = objectMapper.readTree(added.getResponse().getContentAsString())
                .path("data").path("itemId").asLong();
        postJson(token, "/api/customer/cart", cartBody(p2, 2));

        mockMvc.perform(delete("/api/customer/cart/" + itemId).header("X-Token", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/customer/cart").header("X-Token", token))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].product.id").value(p2));

        mockMvc.perform(delete("/api/customer/cart").header("X-Token", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/customer/cart").header("X-Token", token))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalQuantity").value(0));
    }

    @Test
    @DisplayName("TC-CF11 未登录访问购物车 → 401")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/customer/cart"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/customer/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartBody(1L, 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-CF12 账号隔离：B 的购物车看不到 A 的条目")
    void customerIsolation() throws Exception {
        String tokenA = newCustomer("isoA");
        String tokenB = newCustomer("isoB");
        Long productId = publish(5, "隔离商品");
        postJson(tokenA, "/api/customer/cart", cartBody(productId, 2));

        mockMvc.perform(get("/api/customer/cart").header("X-Token", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get("/api/customer/cart").header("X-Token", tokenA))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    /* ---------- 收藏 ---------- */

    @Test
    @DisplayName("TC-CF13 收藏成功；重复收藏幂等（不报错、不重复）")
    void favoriteDeduplicated() throws Exception {
        String token = newCustomer("fav");
        Long productId = publish(5, "收藏商品");

        mockMvc.perform(post("/api/customer/favorites")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", productId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.id").value(productId))
                .andExpect(jsonPath("$.data.available").value(true));

        // 再收藏一次：仍只有一条
        mockMvc.perform(post("/api/customer/favorites")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", productId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/customer/favorites").header("X-Token", token))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("TC-CF14 取消收藏成功；再取消幂等不报错")
    void unfavoriteIdempotent() throws Exception {
        String token = newCustomer("unfav");
        Long productId = publish(5, "取消收藏商品");
        postJson(token, "/api/customer/favorites",
                objectMapper.writeValueAsString(Map.of("productId", productId)));

        mockMvc.perform(delete("/api/customer/favorites/" + productId).header("X-Token", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/customer/favorites").header("X-Token", token))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // 再取消一次：幂等成功
        mockMvc.perform(delete("/api/customer/favorites/" + productId).header("X-Token", token))
                .andExpect(status().isOk());
    }

    /* ---------- 购物车转收藏 ---------- */

    @Test
    @DisplayName("TC-CF15 购物车转收藏：转移后购物车移除、进收藏；已收藏的跳过不重复")
    void transferToFavorites() throws Exception {
        String token = newCustomer("trans");
        Long p1 = publish(5, "转移商品一");
        Long p2 = publish(5, "转移商品二");
        MvcResult r1 = postJson(token, "/api/customer/cart", cartBody(p1, 1));
        MvcResult r2 = postJson(token, "/api/customer/cart", cartBody(p2, 2));
        long item1 = objectMapper.readTree(r1.getResponse().getContentAsString())
                .path("data").path("itemId").asLong();
        long item2 = objectMapper.readTree(r2.getResponse().getContentAsString())
                .path("data").path("itemId").asLong();

        // 先把商品二收藏掉：转收藏时应被跳过
        postJson(token, "/api/customer/favorites",
                objectMapper.writeValueAsString(Map.of("productId", p2)));

        mockMvc.perform(post("/api/customer/cart/to-favorites")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", List.of(item1, item2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transferred").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1));

        // 购物车已清空
        mockMvc.perform(get("/api/customer/cart").header("X-Token", token))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // 收藏恰好两条（p1 新进、p2 不重复）
        mockMvc.perform(get("/api/customer/favorites").header("X-Token", token))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("TC-CF16 转收藏包含不存在/非本人条目 → 400；空勾选 → 400")
    void transferInvalid() throws Exception {
        String token = newCustomer("tinv");
        Long productId = publish(5, "无效转移商品");
        postJson(token, "/api/customer/cart", cartBody(productId, 1));

        mockMvc.perform(post("/api/customer/cart/to-favorites")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", List.of(999999L)))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/customer/cart/to-favorites")
                        .header("X-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", List.of()))))
                .andExpect(status().isBadRequest());

        // 失败转移不破坏购物车
        mockMvc.perform(get("/api/customer/cart").header("X-Token", token))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }
}
