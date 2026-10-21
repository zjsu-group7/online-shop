package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.ProductView;
import com.group7.shop.dto.TradeResult;
import com.group7.shop.dto.TransactionView;
import com.group7.shop.entity.FailedAction;
import com.group7.shop.entity.FrozenBy;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;
import com.group7.shop.entity.Product;
import com.group7.shop.entity.ProductStatus;
import com.group7.shop.repository.IntentRepository;
import com.group7.shop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 买家选择与交易结果编排（迭代 1：周琳皓；迭代 2 规则域：华天遥）。
 * 迭代 2 起：成交扣 1 库存并直接下架；失败按 failedAction 作废或重新排队。
 */
@Service
public class ProductTransactionService {
    private final ProductRepository productRepository;
    private final IntentRepository intentRepository;
    private final IntentService intentService;

    public ProductTransactionService(ProductRepository productRepository,
                                     IntentRepository intentRepository,
                                     IntentService intentService) {
        this.productRepository = productRepository;
        this.intentRepository = intentRepository;
        this.intentService = intentService;
    }

    /** 选定一条排队中的购买意向，并把商品自动冻结为交易冻结。 */
    @Transactional
    public TransactionView selectBuyer(Long productId, Long intentId) {
        Product product = getProductForUpdate(productId);
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw BusinessException.conflict("仅在售商品可以选择买家");
        }

        Intent intent = intentRepository.findById(intentId)
                .orElseThrow(() -> BusinessException.notFound("购买意向不存在"));
        if (!productId.equals(intent.getProductId())) {
            throw BusinessException.badRequest("购买意向不属于该商品");
        }
        if (intent.getStatus() != IntentStatus.WAITING) {
            throw BusinessException.conflict("仅排队中的购买意向可以进入交易");
        }

        product.freezeForTrade();
        intent.markInTrade();
        Product savedProduct = productRepository.save(product);
        Intent savedIntent = intentRepository.save(intent);
        return TransactionView.from(ProductView.from(savedProduct), savedIntent);
    }

    /** 兼容迭代 1 的两参调用：失败按 VOID（作废）处置。 */
    @Transactional
    public TransactionView markResult(Long productId, TradeResult result) {
        return markResult(productId, result, result == TradeResult.FAIL ? FailedAction.VOID : null);
    }

    /**
     * 标记当前交易结果（规则域负责人：华天遥）。
     * 成功：库存扣 1、商品<b>直接下架</b>（即使仍有剩余库存）、选中意向成交、其余排队意向失败。
     * 失败：商品恢复在售、库存不动；选中意向按 failedAction 处置（VOID 作废 / REQUEUE 排到队尾）。
     */
    @Transactional
    public TransactionView markResult(Long productId, TradeResult result, FailedAction failedAction) {
        Product product = getProductForUpdate(productId);
        if (product.getStatus() != ProductStatus.FROZEN || product.getFrozenBy() != FrozenBy.AUTO) {
            throw BusinessException.conflict("商品当前没有进行中的交易");
        }

        Intent selected = intentRepository.findByProductIdAndStatus(productId, IntentStatus.IN_TRADE)
                .stream().findFirst()
                .orElseThrow(() -> BusinessException.conflict("商品没有对应的交易中意向"));

        IntentStatus intentStatus;
        if (result == TradeResult.SUCCESS) {
            if (product.getStock() <= 0) {
                throw BusinessException.conflict("库存不足，无法完成交易，请先补货");
            }
            selected.markTraded();
            intentStatus = IntentStatus.TRADED;
            failWaitingIntents(productId);
            product.deductOneStock();
            product.closeAfterTradeSuccess();
        } else {
            if (failedAction == null) {
                throw BusinessException.badRequest("交易失败时 failedAction 只能为 VOID 或 REQUEUE");
            }
            if (failedAction == FailedAction.REQUEUE) {
                intentStatus = intentService.requeueToTail(selected.getId());
            } else {
                selected.markFailed();
                intentStatus = IntentStatus.FAILED;
            }
            product.restoreAfterTradeFailure();
        }

        Intent savedIntent = intentRepository.save(selected);
        Product savedProduct = productRepository.save(product);
        return new TransactionView(ProductView.from(savedProduct), savedIntent.getId(), intentStatus);
    }

    private void failWaitingIntents(Long productId) {
        List<Intent> waiting = intentRepository
                .findByProductIdAndStatusOrderBySubmittedAtAscIdAsc(productId, IntentStatus.WAITING);
        waiting.forEach(Intent::markFailed);
        intentRepository.saveAll(waiting);
    }

    private Product getProductForUpdate(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
    }
}
