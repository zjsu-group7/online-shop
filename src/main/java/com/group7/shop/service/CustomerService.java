package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.common.PasswordHasher;
import com.group7.shop.dto.CreateOrderResponse;
import com.group7.shop.dto.CustomerLoginResponse;
import com.group7.shop.dto.CustomerView;
import com.group7.shop.dto.OrderHistory;
import com.group7.shop.dto.OrderRecordView;
import com.group7.shop.dto.RegisterRequest;
import com.group7.shop.dto.SubmitIntentRequest;
import com.group7.shop.dto.UpdateProfileRequest;
import com.group7.shop.entity.Customer;
import com.group7.shop.entity.CustomerOrderLink;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.repository.CustomerOrderLinkRepository;
import com.group7.shop.repository.CustomerRepository;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户账号、个人中心与我的下单记录（李宇轩负责，挑战需求 A.2.1 / A.2.2）。
 *
 * <p>安全口径与卖家一致：加盐哈希、token 2 小时过期、连续失败 5 次锁 5 分钟；
 * 但客户身份**独立建表、独立拦截器**，与卖家互不影响。
 *
 * <p><b>与意向域的边界：</b>按团队约定不修改他人代码，因此本服务只**调用**意向域已有的
 * {@link IntentService#submit(SubmitIntentRequest)}，不修改 {@code Intent} 实体；
 * 「谁下的单」记录在本域自有的 {@code customer_order_links} 表，通过 intent_id 关联。
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private static final Duration TOKEN_TTL = Duration.ofHours(2);

    /** 只有出结果的记录才算「历史下单记录」（Q26）。 */
    private static final List<IntentStatus> HISTORY_STATUSES =
            List.of(IntentStatus.TRADED, IntentStatus.FAILED);

    private final CustomerRepository customerRepository;
    private final CustomerOrderLinkRepository orderLinkRepository;
    private final IntentRepository intentRepository;
    private final ProductRepository productRepository;
    private final IntentService intentService;
    private final IntentCodeGenerator codeGenerator;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerOrderLinkRepository orderLinkRepository,
                           IntentRepository intentRepository,
                           ProductRepository productRepository,
                           IntentService intentService,
                           IntentCodeGenerator codeGenerator) {
        this.customerRepository = customerRepository;
        this.orderLinkRepository = orderLinkRepository;
        this.intentRepository = intentRepository;
        this.productRepository = productRepository;
        this.intentService = intentService;
        this.codeGenerator = codeGenerator;
    }

    /* ==================== 注册 / 登录 / 退出 ==================== */

    /** 注册：用户名忽略大小写唯一；密码加盐哈希后存储，响应不回传任何密码字段。 */
    @Transactional
    public CustomerView register(RegisterRequest request) {
        String username = request.trimmedUsername();
        if (username.isEmpty()) {
            throw BusinessException.badRequest("用户名必填");
        }
        if (customerRepository.existsByUsernameIgnoreCase(username)) {
            throw BusinessException.conflict("用户名已被占用");
        }

        String salt = PasswordHasher.generateSalt();
        String passwordHash = salt + ":" + PasswordHasher.hash(salt, request.password());
        Customer customer = new Customer(
                username,
                salt,
                passwordHash,
                request.trimmedPhone(),
                request.trimmedTradePlace()
        );
        customerRepository.save(customer);
        log.info("客户账号 {} 注册成功", username);
        return CustomerView.from(customer);
    }

    /**
     * 登录。失败计数必须落库，故声明 {@code noRollbackFor}，
     * 否则凭据错误抛出的业务异常会把刚写入的失败次数一起回滚，锁定机制失效。
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public CustomerLoginResponse login(String username, String password) {
        String normalized = username == null ? "" : username.trim();
        Customer customer = customerRepository.findByUsernameIgnoreCase(normalized)
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));

        Instant now = Instant.now();
        if (customer.isLocked(now)) {
            long seconds = Duration.between(now, customer.getLockedUntil()).getSeconds();
            long minutes = Math.max(1, (seconds + 59) / 60);
            log.warn("客户账号 {} 处于锁定期，拒绝登录，剩余约 {} 分钟", normalized, minutes);
            throw BusinessException.unauthorized("账号已锁定，请 " + minutes + " 分钟后再试");
        }

        if (!PasswordHasher.matches(password, customer.getPasswordHash())) {
            int attempts = customer.getFailedAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                customer.setFailedAttempts(0);
                customer.setLockedUntil(now.plus(LOCK_DURATION));
                customerRepository.save(customer);
                log.warn("客户账号 {} 连续登录失败 {} 次，已锁定至 {}", normalized, MAX_FAILED_ATTEMPTS, customer.getLockedUntil());
            } else {
                customer.setFailedAttempts(attempts);
                customerRepository.save(customer);
                log.warn("客户账号 {} 登录失败（第 {}/{} 次）", normalized, attempts, MAX_FAILED_ATTEMPTS);
            }
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        customer.setFailedAttempts(0);
        customer.setLockedUntil(null);
        customer.setToken(generateToken());
        customer.setTokenExpiresAt(now.plus(TOKEN_TTL));
        customerRepository.save(customer);
        log.info("客户账号 {} 登录成功，token 有效期至 {}", customer.getUsername(), customer.getTokenExpiresAt());
        return new CustomerLoginResponse(customer.getToken(), customer.getTokenExpiresAt(), CustomerView.from(customer));
    }

    /** 退出：清空 token，旧凭证立即失效。 */
    @Transactional
    public void logout(Customer customer) {
        customer.setToken(null);
        customer.setTokenExpiresAt(null);
        customerRepository.save(customer);
        log.info("客户账号 {} 已登出", customer.getUsername());
    }

    /* ==================== 个人中心 ==================== */

    @Transactional(readOnly = true)
    public CustomerView profile(Customer customer) {
        return CustomerView.from(customer);
    }

    /** 修改资料：电话必填，默认交易地点传空表示清空。 */
    @Transactional
    public CustomerView updateProfile(Customer customer, UpdateProfileRequest request) {
        String phone = request.trimmedPhone();
        if (phone.isEmpty()) {
            throw BusinessException.badRequest("电话必填");
        }
        customer.setPhone(phone);
        customer.setDefaultTradePlace(request.trimmedTradePlace());
        customerRepository.save(customer);
        return CustomerView.from(customer);
    }

    /* ==================== 下单（登录态） ==================== */

    /**
     * 登录客户下单：复用意向域的提交逻辑（排队、口令码、商品状态校验全部沿用），
     * 成功后把 intentId 关联到当前客户。**不修改意向域任何代码。**
     */
    @Transactional
    public CreateOrderResponse createOrder(Customer customer, SubmitIntentRequest request) {
        String code = intentService.submit(request);

        Intent intent = intentRepository.findByCodeHash(codeGenerator.hash(code))
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "下单记录关联失败，请重试"));

        if (!orderLinkRepository.existsByIntentId(intent.getId())) {
            orderLinkRepository.save(new CustomerOrderLink(
                    customer.getId(),
                    intent.getId(),
                    intent.getProductId(),
                    intent.getSubmittedAt()
            ));
        }
        log.info("客户账号 {} 下单成功，intentId={}", customer.getUsername(), intent.getId());
        return new CreateOrderResponse(code, intent.getId());
    }

    /* ==================== 我的下单记录 ==================== */

    /**
     * 仅返回本人、且已出结果（TRADED / FAILED）的记录，按提交时间倒序。
     * 状态以意向表为准；交易时间首次取到时落入关联表，之后固定不变。
     */
    @Transactional
    public OrderHistory orders(Customer customer) {
        List<CustomerOrderLink> links =
                orderLinkRepository.findByCustomerIdOrderBySubmittedAtDescIdDesc(customer.getId());
        if (links.isEmpty()) {
            return new OrderHistory(List.of(), 0);
        }

        List<Long> intentIds = links.stream().map(CustomerOrderLink::getIntentId).toList();
        Map<Long, Intent> intents = intentRepository.findAllById(intentIds).stream()
                .collect(Collectors.toMap(Intent::getId, Function.identity()));

        Map<Long, Product> products = productRepository
                .findAllById(links.stream().map(CustomerOrderLink::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderRecordView> items = new ArrayList<>();
        for (CustomerOrderLink link : links) {
            Intent intent = intents.get(link.getIntentId());
            if (intent == null || !HISTORY_STATUSES.contains(intent.getStatus())) {
                continue;
            }
            Product product = products.get(link.getProductId());
            Instant resultAt = resolveResultAt(link, intent, product);

            items.add(new OrderRecordView(
                    intent.getId(),
                    link.getProductId(),
                    product == null ? "商品已下架" : product.getName(),
                    product == null ? null : product.getImage(),
                    link.getSubmittedAt(),
                    intent.getStatus() == IntentStatus.TRADED ? "SUCCESS" : "FAIL",
                    resultAt
            ));
        }
        return new OrderHistory(items, items.size());
    }

    /**
     * 交易结果时间：优先用关联表已落库值；首次查询终态时从商品侧补齐——
     * 成功取商品 closedAt，失败取商品 lastTradeFailedAt。补齐后落库，避免被后续交易覆盖。
     */
    private Instant resolveResultAt(CustomerOrderLink link, Intent intent, Product product) {
        if (link.getResultAt() != null) {
            return link.getResultAt();
        }
        if (product == null) {
            return null;
        }
        Instant derived = intent.getStatus() == IntentStatus.TRADED
                ? product.getClosedAt()
                : product.getLastTradeFailedAt();
        if (derived != null) {
            link.setResultAt(derived);
            orderLinkRepository.save(link);
        }
        return derived;
    }

    /* ==================== 内部协作 ==================== */

    /** 供拦截器解析 token；token 缺失或无效返回空，不抛异常。 */
    @Transactional(readOnly = true)
    public Optional<Customer> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return customerRepository.findByToken(token)
                .filter(customer -> customer.getTokenExpiresAt() == null
                        || customer.getTokenExpiresAt().isAfter(Instant.now()));
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }
}
