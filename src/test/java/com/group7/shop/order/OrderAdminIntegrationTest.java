package com.group7.shop.order;

import com.group7.shop.common.BusinessException;
import com.group7.shop.common.Result;
import com.group7.shop.dto.OrderItemView;
import com.group7.shop.dto.OrderView;
import com.group7.shop.dto.PageResult;
import com.group7.shop.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单后台与账号隔离集成测试（李宇轩负责，挑战需求 B 的账号权限 + 卖家后台订单）。
 *
 * <p>覆盖四件事：
 * <ol>
 *   <li><b>登录强制</b>：/api/orders、/api/cart、/api/favorites、/api/seller/orders 无 token 一律 401；</li>
 *   <li><b>账号隔离</b>：买家只能看到本人订单；取消他人订单按契约 404，上游若误返他人订单本层拦成 403；</li>
 *   <li><b>卖家状态推进</b>：合法推进 200，跳级/回退 409，非法动作 400；</li>
 *   <li><b>卖家取消</b>：默认原因、状态落地。</li>
 * </ol>
 *
 * <p>订单核心域尚未合并，本测试用 {@link FakeOrderGateway} 顶替 {@link OrderGateway}，
 * 以便在订单域落地前就能验证本层的鉴权、隔离与参数约束；订单域实现该接口后，
 * 只需删掉该测试配置即可对真实实现回归。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderAdminIntegrationTest {

    private static final String PASSWORD = "buyer12345";

    @Autowired private MockMvc mockMvc;
    @Autowired private FakeOrderGateway gateway;
    @Autowired private CustomerRepository customerRepository;

    private String tokenA;
    private String tokenB;
    private Long customerAId;
    private Long customerBId;

    private static final OrderItemView ITEM_BOOK =
            new OrderItemView(1L, "《软件工程》", "/uploads/book.png", new BigDecimal("59.00"), 2, new BigDecimal("118.00"), true);
    private static final OrderItemView ITEM_PEN =
            new OrderItemView(2L, "签字笔", "/uploads/pen.png", new BigDecimal("6.50"), 4, new BigDecimal("26.00"), false);

    @BeforeEach
    void setUp() throws Exception {
        gateway.reset();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userA = "buyer_a_" + suffix;
        String userB = "buyer_b_" + suffix;
        tokenA = registerAndLogin(userA);
        tokenB = registerAndLogin(userB);
        customerAId = customerRepository.findByUsernameIgnoreCase(userA).orElseThrow().getId();
        customerBId = customerRepository.findByUsernameIgnoreCase(userB).orElseThrow().getId();
        assertNotNull(customerAId);
        assertNotNull(customerBId);

        // A 两单、B 一单；A 的第一单停留在「客户下单」，第二单已推进到「开始发货」
        gateway.put("SO-A-1", customerAId, "张三", "13800000001", "PENDING", ITEM_BOOK);
        gateway.put("SO-A-2", customerAId, "张三", "13800000001", "SHIPPED", ITEM_PEN);
        gateway.put("SO-B-1", customerBId, "李四", "13800000002", "CONFIRMED", ITEM_BOOK);
    }

    /* ==================== 一、登录强制 ==================== */

    @Test
    @DisplayName("买家订单接口未带 token → 401")
    void buyerOrdersWithoutToken() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("买家取消订单未带 token → 401")
    void buyerCancelWithoutToken() throws Exception {
        mockMvc.perform(post("/api/orders/SO-A-1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("卖家订单接口未带 token → 401")
    void sellerOrdersWithoutToken() throws Exception {
        mockMvc.perform(get("/api/seller/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("购物车、收藏路径同样纳入登录拦截（挑战需求 B 账号隔离前提）")
    void cartAndFavoritePathsAreProtected() throws Exception {
        mockMvc.perform(get("/api/cart/_probe")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/cart/_probe")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/favorites/_probe")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/favorites/_probe")).andExpect(status().isUnauthorized());
    }

    /* ==================== 二、账号隔离 ==================== */

    @Test
    @DisplayName("买家只看到本人订单，且按下单时间倒序")
    void buyerSeesOnlyOwnOrders() throws Exception {
        mockMvc.perform(get("/api/orders").header("X-Token", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("SO-A-2"))
                .andExpect(jsonPath("$.data.items[1].orderNo").value("SO-A-1"))
                .andExpect(jsonPath("$.data.items[0].customerId").value(customerAId));
    }

    @Test
    @DisplayName("买家取消他人订单 → 404（不泄露订单是否存在）")
    void buyerCannotCancelOthersOrder() throws Exception {
        mockMvc.perform(post("/api/orders/SO-B-1/cancel").header("X-Token", tokenA))
                .andExpect(status().isNotFound());
        // B 的订单不受影响
        assertEquals("CONFIRMED", gateway.statusOf("SO-B-1"));
    }

    @Test
    @DisplayName("上游误返他人订单时，本层二次核对拦成 403")
    void ownershipGuardBlocksForeignOrder() throws Exception {
        gateway.returnForeignOrder = true;
        mockMvc.perform(post("/api/orders/SO-A-1/cancel").header("X-Token", tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权操作他人的订单"));
    }

    @Test
    @DisplayName("买家取消本人未发货订单 → 200，状态落地为已取消")
    void buyerCancelsOwnPendingOrder() throws Exception {
        mockMvc.perform(post("/api/orders/SO-A-1/cancel")
                        .header("X-Token", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"不想要了\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.statusText").value("已取消"))
                .andExpect(jsonPath("$.data.cancelReason").value("不想要了"));
        assertEquals("CANCELLED", gateway.statusOf("SO-A-1"));
        assertEquals("不想要了", gateway.lastBuyerCancelReason);
    }

    @Test
    @DisplayName("买家取消已发货订单 → 409（B.1.2：开始发货前才可取消）")
    void buyerCannotCancelShippedOrder() throws Exception {
        mockMvc.perform(post("/api/orders/SO-A-2/cancel").header("X-Token", tokenA))
                .andExpect(status().isConflict());
    }

    /* ==================== 三、卖家状态推进 ==================== */

    @Test
    @DisplayName("卖家订单列表：全部与按状态过滤")
    void sellerListsOrders() throws Exception {
        mockMvc.perform(get("/api/seller/orders").header("X-Token", sellerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/seller/orders").param("status", "pending").header("X-Token", sellerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].statusText").value("客户下单"));

        mockMvc.perform(get("/api/seller/orders").param("status", "SHIPPED").header("X-Token", sellerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("SO-A-2"))
                .andExpect(jsonPath("$.data.items[0].statusText").value("开始发货"));
    }

    @Test
    @DisplayName("卖家确认订单：PENDING → CONFIRMED，可执行动作随状态变化")
    void sellerConfirmsOrder() throws Exception {
        mockMvc.perform(post("/api/seller/orders/SO-A-1/advance")
                        .header("X-Token", sellerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"confirm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.allowedActions[0]").value("PACK"));
        assertEquals("CONFIRMED", gateway.statusOf("SO-A-1"));
    }

    @Test
    @DisplayName("跳级推进 → 409（五态单向，不可跳级/回退）")
    void sellerCannotSkipState() throws Exception {
        mockMvc.perform(post("/api/seller/orders/SO-A-1/advance")
                        .header("X-Token", sellerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SHIP\"}"))
                .andExpect(status().isConflict());
        assertEquals("PENDING", gateway.statusOf("SO-A-1"));
    }

    @Test
    @DisplayName("非法动作码 → 400，且提示取消走独立接口")
    void illegalActionRejected() throws Exception {
        mockMvc.perform(post("/api/seller/orders/SO-A-1/advance")
                        .header("X-Token", sellerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"FLY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("状态过滤取值非法 → 400")
    void illegalStatusFilterRejected() throws Exception {
        mockMvc.perform(get("/api/seller/orders").param("status", "DONE").header("X-Token", sellerToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("分页越界 → 400")
    void illegalPageSizeRejected() throws Exception {
        mockMvc.perform(get("/api/seller/orders").param("size", "1000").header("X-Token", sellerToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/seller/orders").param("page", "0").header("X-Token", sellerToken()))
                .andExpect(status().isBadRequest());
    }

    /* ==================== 四、卖家取消 ==================== */

    @Test
    @DisplayName("卖家取消订单：省略原因时记「卖家取消」")
    void sellerCancelsOrder() throws Exception {
        mockMvc.perform(post("/api/seller/orders/SO-B-1/cancel").header("X-Token", sellerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("卖家取消"));
        assertEquals("CANCELLED", gateway.statusOf("SO-B-1"));
    }

    @Test
    @DisplayName("卖家取消已完成订单 → 409（B.1.3：交易完成前才可取消）")
    void sellerCannotCancelCompletedOrder() throws Exception {
        gateway.forceStatus("SO-A-2", "COMPLETED");
        mockMvc.perform(post("/api/seller/orders/SO-A-2/cancel").header("X-Token", sellerToken()))
                .andExpect(status().isConflict());
    }

    /* ==================== 辅助方法 ==================== */

    private String registerAndLogin(String username) throws Exception {
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
        return matcher.group(1);
    }

    /** 卖家 token：走真实登录接口，避免测试里伪造身份。 */
    private String sellerToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/seller/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"seller\",\"password\":\"12345678\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), "卖家登录响应应包含 token");
        return matcher.group(1);
    }

    /* ==================== 测试替身 ==================== */

    /**
     * 订单核心域的测试替身：内存五态状态机，语义与 {@link OrderGateway} 的契约一致。
     * 真正的实现由订单域（周琳皓）提供，本类只用于在本层提前验证鉴权、隔离与参数约束。
     */
    @TestConfiguration
    static class FakeGatewayConfig {

        @Bean
        @Primary
        FakeOrderGateway fakeOrderGateway() {
            return new FakeOrderGateway();
        }

        /** 仅测试用的探针：验证购物车/收藏路径已被登录拦截器覆盖（真实接口由华天遥提供）。 */
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

        /**
         * 刻意不用 {@code @RestController}：测试类位于主应用包下，若带 stereotype 注解会被
         * 组件扫描再注册一次，与 {@code @Bean} 形成重复映射。改用 {@code @RequestMapping}
         * + {@code @ResponseBody}，只经 {@code @Bean} 注册一次。
         */
        @RequestMapping
        @ResponseBody
        static class ProbeController {

            @GetMapping("/api/cart/_probe")
            Result<Void> cartProbe() {
                return Result.success(null);
            }

            @GetMapping("/api/favorites/_probe")
            Result<Void> favoriteProbe() {
                return Result.success(null);
            }

            @PostMapping("/api/cart/_probe")
            Result<Void> cartProbePost() {
                return Result.success(null);
            }

            @PostMapping("/api/favorites/_probe")
            Result<Void> favoriteProbePost() {
                return Result.success(null);
            }
        }
    }

    /** 内存版订单网关。 */
    static class FakeOrderGateway implements OrderGateway {

        private final Map<String, FakeOrder> orders = new LinkedHashMap<>();
        private int cursor = 0;

        /** 模拟「上游误把他人订单返回来」的故障注入，用于验证本层的二次核对。 */
        boolean returnForeignOrder = false;

        void reset() {
            orders.clear();
            cursor = 0;
            returnForeignOrder = false;
            lastBuyerCancelReason = null;
        }

        void put(String orderNo, Long customerId, String name, String phone, String status, OrderItemView... items) {
            cursor++;
            FakeOrder order = new FakeOrder();
            order.orderNo = orderNo;
            order.customerId = customerId;
            order.customerName = name;
            order.customerPhone = phone;
            order.status = status;
            order.items = List.of(items);
            order.totalAmount = order.items.stream()
                    .map(OrderItemView::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.createdAt = Instant.parse("2026-11-15T02:00:00Z").plusSeconds(cursor * 60L);
            order.updatedAt = order.createdAt;
            orders.put(orderNo, order);
        }

        String statusOf(String orderNo) {
            FakeOrder order = orders.get(orderNo);
            return order == null ? null : order.status;
        }

        void forceStatus(String orderNo, String status) {
            FakeOrder order = orders.get(orderNo);
            if (order != null) {
                order.status = status;
            }
        }

        @Override
        public PageResult<OrderView> sellerOrders(String status, int page, int size) {
            return page(orders.values().stream()
                    .filter(o -> status == null || status.equals(o.status))
                    .sorted(Comparator.comparing((FakeOrder o) -> o.createdAt).reversed())
                    .toList(), page, size);
        }

        @Override
        public PageResult<OrderView> buyerOrders(Long customerId, int page, int size) {
            return page(orders.values().stream()
                    .filter(o -> customerId != null && customerId.equals(o.customerId))
                    .sorted(Comparator.comparing((FakeOrder o) -> o.createdAt).reversed())
                    .toList(), page, size);
        }

        @Override
        public OrderView advance(String orderNo, OrderAction action) {
            FakeOrder order = require(orderNo);
            String expected = switch (action) {
                case CONFIRM -> "PENDING";
                case PACK -> "CONFIRMED";
                case SHIP -> "PACKED";
                case COMPLETE -> "SHIPPED";
            };
            if (!expected.equals(order.status)) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "订单当前状态为 " + OrderStatusCodes.labelOf(order.status) + "，不能执行「" + action.getLabel() + "」");
            }
            order.status = switch (action) {
                case CONFIRM -> "CONFIRMED";
                case PACK -> "PACKED";
                case SHIP -> "SHIPPED";
                case COMPLETE -> "COMPLETED";
            };
            order.updatedAt = Instant.now();
            return order.toView();
        }

        @Override
        public OrderView cancelBySeller(String orderNo, String reason) {
            FakeOrder order = require(orderNo);
            if ("COMPLETED".equals(order.status) || "CANCELLED".equals(order.status)) {
                throw new BusinessException(HttpStatus.CONFLICT, "订单已结束，不能取消");
            }
            order.status = "CANCELLED";
            order.cancelReason = reason;
            order.updatedAt = Instant.now();
            return order.toView();
        }

        /** 记录买家取消时收到的原因，用于断言「原因确实传到了订单域」。 */
        String lastBuyerCancelReason;

        @Override
        public OrderView cancelByBuyer(Long customerId, String orderNo, String reason) {
            lastBuyerCancelReason = reason;
            FakeOrder order = orders.get(orderNo);
            if (order == null || !order.customerId.equals(customerId)) {
                throw BusinessException.notFound("订单不存在");
            }
            if ("SHIPPED".equals(order.status) || "COMPLETED".equals(order.status) || "CANCELLED".equals(order.status)) {
                throw new BusinessException(HttpStatus.CONFLICT, "订单已发货，不能取消");
            }
            order.status = "CANCELLED";
            order.cancelReason = reason;
            order.updatedAt = Instant.now();
            return returnForeignOrder ? foreignViewFor(customerId) : order.toView();
        }

        /** 故障注入：返回一条不属于该客户的订单，验证本层二次核对。 */
        private OrderView foreignViewFor(Long customerId) {
            return orders.values().stream()
                    .filter(o -> !o.customerId.equals(customerId))
                    .findFirst()
                    .map(FakeOrder::toView)
                    .orElseThrow();
        }

        private FakeOrder require(String orderNo) {
            FakeOrder order = orders.get(orderNo);
            if (order == null) {
                throw BusinessException.notFound("订单不存在");
            }
            return order;
        }

        private PageResult<OrderView> page(List<FakeOrder> all, int page, int size) {
            List<OrderView> items = new ArrayList<>();
            int from = Math.min((page - 1) * size, all.size());
            int to = Math.min(from + size, all.size());
            for (int i = from; i < to; i++) {
                items.add(all.get(i).toView());
            }
            return new PageResult<>(page, size, all.size(), items);
        }

        static final class FakeOrder {
            String orderNo;
            Long customerId;
            String customerName;
            String customerPhone;
            String status;
            BigDecimal totalAmount;
            List<OrderItemView> items;
            Instant createdAt;
            Instant updatedAt;
            String cancelReason;

            OrderView toView() {
                return new OrderView(
                        orderNo,
                        customerId,
                        customerName,
                        customerPhone,
                        status,
                        null,                 // 故意留空：由本域的 OrderViewNormalizer 补齐文案
                        totalAmount,
                        items.size(),
                        createdAt,
                        updatedAt,
                        allowedActions(),
                        cancelReason,
                        items
                );
            }

            List<String> allowedActions() {
                return switch (status) {
                    case "PENDING" -> List.of("CONFIRM");
                    case "CONFIRMED" -> List.of("PACK");
                    case "PACKED" -> List.of("SHIP");
                    case "SHIPPED" -> List.of("COMPLETE");
                    default -> List.of();
                };
            }
        }
    }
}
