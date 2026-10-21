package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.dto.ProductView;
import com.group7.shop.dto.ProductHistoryView;
import com.group7.shop.dto.PublishProductRequest;
import com.group7.shop.dto.DealRequest;
import com.group7.shop.dto.TradeResult;
import com.group7.shop.dto.TradeResultRequest;
import com.group7.shop.dto.TransactionView;
import com.group7.shop.dto.UpdateStockRequest;
import com.group7.shop.entity.FailedAction;
import com.group7.shop.service.ProductService;
import com.group7.shop.service.ProductTransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seller")
public class SellerProductController {
    private final ProductService productService;
    private final ProductTransactionService transactionService;

    public SellerProductController(ProductService productService,
                                   ProductTransactionService transactionService) {
        this.productService = productService;
        this.transactionService = transactionService;
    }

    /** 该路由必须由李宇轩的 X-Token 拦截器保护。 */
    @PostMapping("/products")
    public Result<ProductView> publish(@Valid @RequestBody PublishProductRequest request) {
        return Result.success("商品发布成功", productService.publish(request));
    }

    /** 包含当前商品与历史商品，按创建时间倒序。 */
    @GetMapping("/products")
    public Result<List<ProductView>> products() {
        return Result.success(productService.sellerProducts());
    }

    @PostMapping("/products/{id}/freeze")
    public Result<ProductView> freeze(@PathVariable Long id) {
        return Result.success("商品已手动冻结", productService.freezeManually(id));
    }

    @PostMapping("/products/{id}/unfreeze")
    public Result<ProductView> unfreeze(@PathVariable Long id) {
        return Result.success("商品已解冻", productService.unfreezeManually(id));
    }

    @PostMapping("/products/{id}/offshelf")
    public Result<ProductView> offshelf(@PathVariable Long id) {
        return Result.success("商品已下架", productService.offshelf(id));
    }

    @PostMapping("/products/{id}/deal")
    public Result<TransactionView> deal(@PathVariable Long id,
                                        @Valid @RequestBody DealRequest request) {
        return Result.success("已选定买家，商品进入交易冻结",
                transactionService.selectBuyer(id, request.intentId()));
    }

    /** 修改库存（唯一入口）；改为 0 时在售商品自动下架。 */
    @PutMapping("/products/{id}/stock")
    public Result<ProductView> updateStock(@PathVariable Long id,
                                           @Valid @RequestBody UpdateStockRequest request) {
        return Result.success("库存已更新", productService.updateStock(id, request.stock()));
    }

    /** 改完库存后重新上架；库存为 0 时拒绝。 */
    @PostMapping("/products/{id}/reshelf")
    public Result<ProductView> reshelf(@PathVariable Long id) {
        return Result.success("商品已重新上架", productService.reshelf(id));
    }

    /** 标记交易成功 / 失败；成功扣 1 库存并直接下架，失败按 failedAction 处置。 */
    @PostMapping("/products/{id}/result")
    public Result<TransactionView> result(@PathVariable Long id,
                                          @Valid @RequestBody TradeResultRequest request) {
        String message;
        if (request.result() == TradeResult.SUCCESS) {
            message = "交易成功，商品已下架";
        } else if (request.failedAction() == FailedAction.REQUEUE) {
            message = "交易失败，商品已恢复在售，该记录已重新排队";
        } else {
            message = "交易失败，商品已恢复在售";
        }
        return Result.success(message,
                transactionService.markResult(id, request.result(), request.failedAction()));
    }

    @GetMapping("/history")
    public Result<Map<String, List<ProductHistoryView>>> history() {
        return Result.success(Map.of("items", productService.history()));
    }
}
