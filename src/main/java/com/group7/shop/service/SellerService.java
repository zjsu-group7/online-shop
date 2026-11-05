package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.common.PasswordHasher;
import com.group7.shop.dto.IntentView;
import com.group7.shop.dto.LoginResponse;
import com.group7.shop.dto.ProductView;
import com.group7.shop.dto.SellerOverview;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.entity.Seller;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.repository.ProductRepository;
import com.group7.shop.repository.SellerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 账号与后台域核心服务（李宇轩负责）。
 * 登录/登出/改密/后台总览均在此实现；token 仅存于 Seller.token 字段，不做服务端会话。
 * 密码哈希使用 PasswordHasher（JDK 内置，无第三方依赖）。
 */
@Service
public class SellerService {

    private static final List<ProductStatus> ACTIVE_STATUSES =
            List.of(ProductStatus.ON_SALE, ProductStatus.FROZEN);

    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final IntentRepository intentRepository;

    public SellerService(SellerRepository sellerRepository,
                         ProductRepository productRepository,
                         IntentRepository intentRepository) {
        this.sellerRepository = sellerRepository;
        this.productRepository = productRepository;
        this.intentRepository = intentRepository;
    }

    @Transactional
    public LoginResponse login(String username, String password) {
        Seller seller = sellerRepository.findByUsername(username)
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        if (!PasswordHasher.matches(password, seller.getPasswordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        // 重新登录使旧 token 失效：覆盖为新 token
        seller.setToken(generateToken());
        sellerRepository.save(seller);
        return new LoginResponse(seller.getToken(), seller.getUsername());
    }

    @Transactional
    public void logout(Seller seller) {
        seller.setToken(null);
        sellerRepository.save(seller);
    }

    @Transactional
    public void changePassword(Seller seller, String oldPassword, String newPassword) {
        if (!PasswordHasher.matches(oldPassword, seller.getPasswordHash())) {
            throw BusinessException.badRequest("原密码错误");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw BusinessException.badRequest("新密码不少于8位");
        }
        // 换盐重算哈希
        String salt = PasswordHasher.generateSalt();
        seller.setSalt(salt);
        seller.setPasswordHash(salt + ":" + PasswordHasher.hash(salt, newPassword));
        // 改后旧 token 失效
        seller.setToken(null);
        sellerRepository.save(seller);
    }

    @Transactional(readOnly = true)
    public SellerOverview overview(Seller seller) {
        // 迭代 2 多商品适配：返回全部在售/交易中商品及各自的意向队列
        List<Product> actives = productRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_STATUSES);
        Product product = actives.isEmpty() ? null : actives.get(0);
        ProductView productView = product == null ? null : ProductView.from(product);

        List<SellerOverview.ProductQueue> products = new ArrayList<>();
        for (Product p : actives) {
            List<IntentView> q = queueOf(p.getId());
            products.add(new SellerOverview.ProductQueue(ProductView.from(p), q));
        }

        // 兼容字段：最新一件在售商品的队列
        List<IntentView> queue = product == null ? new ArrayList<>() : queueOf(product.getId());

        boolean canPublish = actives.isEmpty();
        return new SellerOverview(productView, queue, products, canPublish);
    }

    /** 某商品的活跃意向队列：WAITING + IN_TRADE，按提交时间正序，WAITING 带位次。 */
    private List<IntentView> queueOf(Long productId) {
        List<IntentView> queue = new ArrayList<>();
        List<Intent> intents = intentRepository.findByProductIdOrderBySubmittedAtAsc(productId);
        int position = 0;
        for (Intent intent : intents) {
            if (intent.getStatus() == IntentStatus.WAITING) {
                position++;
            }
            if (intent.getStatus() == IntentStatus.WAITING || intent.getStatus() == IntentStatus.IN_TRADE) {
                // 已进入交易的意向 position 置空
                Integer pos = intent.getStatus() == IntentStatus.IN_TRADE ? null : position;
                queue.add(new IntentView(
                        intent.getId(),
                        intent.getName(),
                        intent.getPhone(),
                        intent.getStatus(),
                        pos,
                        intent.getSubmittedAt()));
            }
        }
        return queue;
    }

    private String generateToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
