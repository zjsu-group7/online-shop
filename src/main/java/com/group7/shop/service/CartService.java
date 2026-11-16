package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.AddCartRequest;
import com.group7.shop.dto.CartItemView;
import com.group7.shop.dto.CartView;
import com.group7.shop.dto.TransferFavoriteRequest;
import com.group7.shop.dto.TransferFavoriteResponse;
import com.group7.shop.dto.UpdateCartQuantityRequest;
import com.group7.shop.entity.CartItem;
import com.group7.shop.entity.Favorite;
import com.group7.shop.entity.Product;
import com.group7.shop.repository.CartItemRepository;
import com.group7.shop.repository.FavoriteRepository;
import com.group7.shop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车（华天遥负责，挑战需求 B / FR-B7、FR-B9；表结构牵头：李婉婷）。
 *
 * <p>规则口径（Wiki 需求规格说明书 v4.0 §3.6）：</p>
 * <ul>
 *   <li>账号绑定，服务端存储；重复加入同一商品 = 数量累加，不产生重复条目</li>
 *   <li>购物车不受库存限制：允许加入下架/无货商品，列表查询时实时标注（ProductAvailability）</li>
 *   <li>下单时才做库存校验（周琳皓的订单域负责）；下单成功后由订单域调用 {@link #removeOwned} 移除所选</li>
 * </ul>
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final FavoriteRepository favoriteRepository;

    public CartService(CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       FavoriteRepository favoriteRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.favoriteRepository = favoriteRepository;
    }

    /** 加购：重复加入同一商品时数量累加（(customer_id, product_id) 唯一约束兜底）。 */
    @Transactional
    public CartItemView add(Long customerId, AddCartRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
        int addQuantity = request.quantity() == null ? 1 : request.quantity();

        CartItem item = cartItemRepository.findByCustomerIdAndProductId(customerId, product.getId())
                .orElseGet(() -> new CartItem(customerId, product.getId(), 0));
        item.setQuantity(item.getQuantity() + addQuantity);
        return toView(cartItemRepository.save(item), product);
    }

    /** 购物车列表：实时计算每条的「已下架/无货」标注。 */
    @Transactional(readOnly = true)
    public CartView list(Long customerId) {
        List<CartItem> items = cartItemRepository.findByCustomerIdOrderByIdAsc(customerId);
        Map<Long, Product> products = loadProducts(items);

        List<CartItemView> views = items.stream()
                .map(item -> toView(item, products.get(item.getProductId())))
                .toList();
        int totalQuantity = views.stream().mapToInt(CartItemView::quantity).sum();
        return new CartView(views, views.size(), totalQuantity);
    }

    /** 修改数量：条目必须属于当前客户（防越权），数量 >= 1（删除走删除接口）。 */
    @Transactional
    public CartItemView updateQuantity(Long customerId, Long itemId, UpdateCartQuantityRequest request) {
        CartItem item = ownedItem(customerId, itemId);
        item.setQuantity(request.quantity());
        Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
        return toView(cartItemRepository.save(item), product);
    }

    /** 删除单条（仅本人的条目）。 */
    @Transactional
    public void remove(Long customerId, Long itemId) {
        cartItemRepository.delete(ownedItem(customerId, itemId));
    }

    /** 一键清空当前客户的购物车。 */
    @Transactional
    public void clear(Long customerId) {
        List<CartItem> items = cartItemRepository.findByCustomerIdOrderByIdAsc(customerId);
        cartItemRepository.deleteAll(items);
    }

    /**
     * 购物车多选转收藏（UC-06）：同一事务内「购物车移除 + 进收藏」。
     * 已收藏的商品保持不重复（skipped），不双向保留；收藏转购物车不在本期范围。
     */
    @Transactional
    public TransferFavoriteResponse transferToFavorites(Long customerId, TransferFavoriteRequest request) {
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(request.itemIds()));
        List<CartItem> items = cartItemRepository.findByCustomerIdAndIdIn(customerId, distinctIds);
        if (items.size() != distinctIds.size()) {
            throw BusinessException.badRequest("勾选中包含不存在或非本人的购物车条目");
        }

        int transferred = 0;
        int skipped = 0;
        for (CartItem item : items) {
            if (favoriteRepository.existsByCustomerIdAndProductId(customerId, item.getProductId())) {
                skipped++;
            } else {
                favoriteRepository.save(new Favorite(customerId, item.getProductId()));
                transferred++;
            }
            cartItemRepository.delete(item);
        }
        return new TransferFavoriteResponse(transferred, skipped);
    }

    /**
     * 供订单域（周琳皓）在下单成功后调用：移除客户选定的购物车条目。
     * 只删除属于该客户的条目，他人的/不存在的静默跳过，不影响下单主流程。
     */
    @Transactional
    public void removeOwned(Long customerId, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        List<CartItem> items = cartItemRepository.findByCustomerIdAndIdIn(customerId, itemIds);
        cartItemRepository.deleteAll(items);
    }

    /* ---------- 内部 ---------- */

    private CartItem ownedItem(Long customerId, Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> BusinessException.notFound("购物车条目不存在"));
        if (!item.getCustomerId().equals(customerId)) {
            throw BusinessException.notFound("购物车条目不存在");
        }
        return item;
    }

    private Map<Long, Product> loadProducts(List<CartItem> items) {
        List<Long> productIds = items.stream().map(CartItem::getProductId).distinct().toList();
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private CartItemView toView(CartItem item, Product product) {
        return CartItemView.of(new CartItemView.CartItemViewSource(
                item.getId(), item.getQuantity(), item.getCreatedAt(), product));
    }
}
