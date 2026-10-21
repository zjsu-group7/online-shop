package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.IntentQueryResult;
import com.group7.shop.dto.BuyerIntentView;
import com.group7.shop.dto.ProductView;
import com.group7.shop.dto.SubmitIntentRequest;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 购买意向模块核心服务（负责人：华天遥）。
 * 覆盖：意向提交、口令码生成、凭码查询、修改、撤销、排队位次计算；
 * 并向卖家侧模块（李宇轩 overview / 周琳皓 交易流转）提供内部协作方法。
 *
 * 排队规则（Wiki《业务流程图》）：按提交时间先到先得；意向无时效；
 * 撤销后其余位次依次前移；修改信息不影响位次；不按姓名与电话去重。
 */
@Service
public class IntentService {

    private static final List<ProductStatus> ACTIVE_STATUSES =
            List.of(ProductStatus.ON_SALE, ProductStatus.FROZEN);
    /** codeHash 唯一冲突时的重试次数（32^8 ≈ 1.1 万亿组合，实际不可能用尽）。 */
    private static final int GENERATE_RETRY = 8;

    private final IntentRepository intentRepository;
    private final ProductRepository productRepository;
    private final IntentCodeGenerator codeGenerator;

    public IntentService(IntentRepository intentRepository,
                         ProductRepository productRepository,
                         IntentCodeGenerator codeGenerator) {
        this.intentRepository = intentRepository;
        this.productRepository = productRepository;
        this.codeGenerator = codeGenerator;
    }

    /* ==================== 买家侧接口 ==================== */

    /**
     * POST /api/intents：提交购买意向，返回口令码（仅此一次）。
     * 无在售商品 → 400「暂无商品在售」；商品非在售 → 400「商品交易中，暂停接收新意向」；
     * 姓名或电话去空格后为空 → 400「姓名与联系电话均为必填」。
     */
    @Transactional
    public String submit(SubmitIntentRequest request) {
        String name = request.trimmedName();
        String phone = request.trimmedPhone();
        if (name.isEmpty() || phone.isEmpty()) {
            throw BusinessException.badRequest("姓名与联系电话均为必填");
        }

        Product product = currentActiveProduct()
                .orElseThrow(() -> BusinessException.badRequest("暂无商品在售"));
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw BusinessException.badRequest("商品交易中，暂停接收新意向");
        }

        return saveWithUniqueCode(product.getId(), name, phone);
    }

    /**
     * GET /api/intents/{code}：凭口令码查排队位次与状态。
     * 口令码不存在或已处终态（成功/撤销/失败）→ 404「口令码无效或已失效」；
     * 已进入交易时 position 置空。
     */
    @Transactional(readOnly = true)
    public IntentQueryResult query(String code) {
        Intent intent = loadActiveByCode(code);
        ProductView product = productRepository.findById(intent.getProductId())
                .map(ProductView::from)
                .orElse(null);
        return new IntentQueryResult(BuyerIntentView.from(intent, positionOf(intent)), product);
    }

    /**
     * PUT /api/intents/{code}：修改姓名/电话，位次不变。
     * 校验同提交；终态 → 404；已进入交易 → 400「你已进入交易，无法修改信息」。
     */
    @Transactional
    public IntentQueryResult update(String code, SubmitIntentRequest request) {
        String name = request.trimmedName();
        String phone = request.trimmedPhone();
        if (name.isEmpty() || phone.isEmpty()) {
            throw BusinessException.badRequest("姓名与联系电话均为必填");
        }
        Intent intent = loadActiveByCode(code);
        if (intent.getStatus() == IntentStatus.IN_TRADE) {
            throw BusinessException.badRequest("你已进入交易，无法修改信息");
        }
        intent.updateContact(name, phone);
        Intent saved = intentRepository.save(intent);
        ProductView product = productRepository.findById(saved.getProductId())
                .map(ProductView::from)
                .orElse(null);
        return new IntentQueryResult(BuyerIntentView.from(saved, positionOf(saved)), product);
    }

    /**
     * POST /api/intents/{code}/cancel：撤销意向，后续位次前移。
     * 交易中拒绝撤销 → 400「你已进入交易，无法自行撤销」；终态 → 404。
     */
    @Transactional
    public IntentStatus cancel(String code) {
        Intent intent = loadActiveByCode(code);
        if (intent.getStatus() == IntentStatus.IN_TRADE) {
            throw BusinessException.badRequest("你已进入交易，无法自行撤销");
        }
        intent.cancel();
        return intentRepository.save(intent).getStatus();
    }

    /* ==================== 卖家侧内部协作方法 ==================== */

    /**
     * 工作台意向队列（供 GET /api/seller/overview 使用，负责人李宇轩）。
     * 返回该商品排队中 + 交易中的意向，按提交时间正序并带 position；
     * 不含口令码（codeHash），position 对 IN_TRADE 意向为 null。
     */
    @Transactional(readOnly = true)
    public List<BuyerIntentView> activeQueue(Long productId) {
        List<Intent> queue = intentRepository
                .findByProductIdAndStatusInOrderBySubmittedAtAscIdAsc(
                        productId, List.of(IntentStatus.WAITING, IntentStatus.IN_TRADE));
        int position = 0;
        var views = new java.util.ArrayList<BuyerIntentView>(queue.size());
        for (Intent intent : queue) {
            Integer pos = null;
            if (intent.getStatus() == IntentStatus.WAITING) {
                position += 1;
                pos = position;
            }
            views.add(BuyerIntentView.from(intent, pos));
        }
        return views;
    }

    /**
     * 卖家选定买家进入交易（供 POST /api/seller/products/{id}/deal 使用，负责人周琳皓）。
     * 意向不属于该商品 → 400；意向不在排队中 → 409。
     */
    @Transactional
    public void startTrade(Long productId, Long intentId) {
        Intent intent = intentRepository.findById(intentId)
                .orElseThrow(() -> BusinessException.badRequest("意向不存在"));
        if (!intent.getProductId().equals(productId)) {
            throw BusinessException.badRequest("意向不属于该商品");
        }
        if (intent.getStatus() != IntentStatus.WAITING) {
            throw BusinessException.conflict("意向不在排队中，无法发起交易");
        }
        intent.markInTrade();
        intentRepository.save(intent);
    }

    /** 交易成功：该意向置 TRADED（供 result SUCCESS 流程调用，负责人周琳皓）。 */
    @Transactional
    public void markTraded(Long intentId) {
        loadById(intentId).markTraded();
    }

    /** 交易失败：该意向置 FAILED（供 result FAIL / 下架流程调用）。 */
    @Transactional
    public void markFailed(Long intentId) {
        loadById(intentId).markFailed();
    }

    /**
     * 交易失败并选择「重新排队」：该意向回到 WAITING 并刷新排队时间排到队尾。
     * 供标记交易结果流程调用（规则域负责人：华天遥）；非交易中意向 → 409。
     */
    @Transactional
    public IntentStatus requeueToTail(Long intentId) {
        Intent intent = loadById(intentId);
        if (intent.getStatus() != IntentStatus.IN_TRADE) {
            throw BusinessException.conflict("意向不在交易中，无法重新排队");
        }
        intent.requeueToTail(Instant.now());
        return intentRepository.save(intent).getStatus();
    }

    /**
     * 将该商品剩余 WAITING 意向全部置为 FAILED（终态留痕）。
     * 供「交易成功后其余意向置失败」「手动下架后剩余意向置失败」两个场景调用。
     */
    @Transactional
    public int failAllWaiting(Long productId) {
        List<Intent> waiting = intentRepository.findByProductIdAndStatus(productId, IntentStatus.WAITING);
        waiting.forEach(Intent::markFailed);
        intentRepository.saveAll(waiting);
        return waiting.size();
    }

    /* ==================== 私有方法 ==================== */

    /** 生成全库唯一的口令码并保存意向；codeHash 唯一索引兜底并发冲突。 */
    private String saveWithUniqueCode(Long productId, String name, String phone) {
        for (int i = 0; i < GENERATE_RETRY; i++) {
            String code = codeGenerator.generate();
            String hash = codeGenerator.hash(code);
            if (intentRepository.existsByCodeHash(hash)) {
                continue;
            }
            intentRepository.save(new Intent(productId, name, phone, hash, Instant.now()));
            return code;
        }
        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "口令码生成失败，请重试");
    }

    /** 凭口令码加载未到终态的意向；不存在、格式非法或终态一律 404。 */
    private Intent loadActiveByCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (normalized.length() != IntentCodeGenerator.CODE_LENGTH) {
            throw BusinessException.notFound("口令码无效或已失效");
        }
        Intent intent = intentRepository.findByCodeHash(codeGenerator.hash(normalized))
                .orElseThrow(() -> BusinessException.notFound("口令码无效或已失效"));
        if (intent.getStatus().isTerminal()) {
            throw BusinessException.notFound("口令码无效或已失效");
        }
        return intent;
    }

    /** 排队位次：同一商品 WAITING 意向中按提交时间（id 兜底）的序号，从 1 开始；IN_TRADE 为 null。 */
    private Integer positionOf(Intent intent) {
        if (intent.getStatus() != IntentStatus.WAITING) {
            return null;
        }
        List<Intent> waiting = intentRepository
                .findByProductIdAndStatusOrderBySubmittedAtAscIdAsc(intent.getProductId(), IntentStatus.WAITING);
        int index = waiting.stream().filter(i -> i.getId().equals(intent.getId())).findFirst()
                .map(waiting::indexOf)
                .orElse(-1);
        return index < 0 ? null : index + 1;
    }

    /** 当前活跃（在售/已冻结）商品，取最新创建的一件，与 GET /api/product 口径一致。 */
    private Optional<Product> currentActiveProduct() {
        return productRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_STATUSES)
                .stream().findFirst();
    }

    private Intent loadById(Long intentId) {
        return intentRepository.findById(intentId)
                .orElseThrow(() -> BusinessException.notFound("意向不存在"));
    }
}
