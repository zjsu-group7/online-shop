package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.ProductView;
import com.group7.shop.dto.TradeResult;
import com.group7.shop.dto.TransactionView;
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

/** 周琳皓负责的买家选择、交易结果和商品状态联动。 */
@Service
public class ProductTransactionService {
    private final ProductRepository productRepository;
    private final IntentRepository intentRepository;

    public ProductTransactionService(ProductRepository productRepository,
                                     IntentRepository intentRepository) {
        this.productRepository = productRepository;
        this.intentRepository = intentRepository;
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

    /**
     * 标记当前交易结果。
     * 成功：商品下架、选中意向成交、其余排队意向失败。
     * 失败：选中意向失败、其余队列保留、商品恢复在售。
     */
    @Transactional
    public TransactionView markResult(Long productId, TradeResult result) {
        Product product = getProductForUpdate(productId);
        if (product.getStatus() != ProductStatus.FROZEN || product.getFrozenBy() != FrozenBy.AUTO) {
            throw BusinessException.conflict("商品当前没有进行中的交易");
        }

        Intent selected = intentRepository.findByProductIdAndStatus(productId, IntentStatus.IN_TRADE)
                .stream().findFirst()
                .orElseThrow(() -> BusinessException.conflict("商品没有对应的交易中意向"));

        if (result == TradeResult.SUCCESS) {
            selected.markTraded();
            failWaitingIntents(productId);
            product.closeAfterTradeSuccess();
        } else {
            selected.markFailed();
            product.restoreAfterTradeFailure();
        }

        Intent savedIntent = intentRepository.save(selected);
        Product savedProduct = productRepository.save(product);
        return TransactionView.from(ProductView.from(savedProduct), savedIntent);
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
