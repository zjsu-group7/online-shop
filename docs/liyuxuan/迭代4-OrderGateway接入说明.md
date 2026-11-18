# OrderGateway 接入说明（给订单核心域）

> 目标：让卖家后台的 3 个订单接口从 `503 尚未接入` 变成真正可用。
> 代价：**订单域只需新增 1 个类**，不改动已有任何一行代码。
> 编写：李宇轩　2026-11-18

## 一、为什么是这层接口

卖家后台要的是"能直接渲染的订单视图"（中文状态、可点按钮、明细清单），
订单域要维护的是"状态机 + 库存生命周期"。两者职责不同，硬绑在一起就会互相牵制：

- 我这边需要能编译、能自测，但订单域当时还没有代码；
- 订单域合并后，我的 DTO 字段一变，前端就得跟着改。

所以把边界收敛成 `com.group7.shop.order.OrderGateway` 一个接口、5 个方法。
订单域实现它，后台侧完全不感知内部实现；我这边在订单域缺席时降级为 `503`（已有测试守护）。

## 二、契约

| 方法 | 入参 | 出参 | 失败语义 |
|---|---|---|---|
| `sellerOrders` | `status`（可为 null）、`page`（≥1）、`size`（1~100） | `PageResult<SellerOrderView>`，按下单时间倒序 | 状态取值非法 → 400（本域已在校验，实现方可直接信任） |
| `buyerOrders` | `customerId`、`page`、`size` | `PageResult<SellerOrderView>`，仅本人订单 | 同上 |
| `advance` | `orderNo`、`OrderAction`（CONFIRM/PACK/SHIP/COMPLETE） | 推进后的 `SellerOrderView` | 订单不存在 → 404；跳级 / 回退 / 终态 → **409** |
| `cancelBySeller` | `orderNo`、`reason` | 取消后的视图 | 不存在 → 404；已完成 → 409 |
| `cancelByBuyer` | `customerId`、`orderNo`、`reason` | 取消后的视图 | 不是本人的单 → **404**（不用 403，避免泄露单号是否存在） |

`SellerOrderView` 关键约定（`order.SellerOrderView`）：

- `status` 用**你方枚举名**（`CUSTOMER_SUBMITTED` 等），前端徽章按这套配色；也兼容本域旧码（`PENDING` 等），由 `OrderStatusCodes.normalize` 归一；
- `statusText` 中文，可留空由本域补齐；
- `allowedActions` 当前状态**唯一允许的下一步**，前端据此渲染按钮，终态给空列表；
- `itemCount` 是**明细行数（商品种数）**，不是件数——前端显示"共 N 种商品"；
- `items[].available` 用 `Boolean`，不确定就传 `null`（前端不标注），确定为不可售才传 `false`。

## 三、参考实现（130 行，直接放进订单域即可）

放在 `com.group7.shop.order` 包下，类名可自定；它只调用你已有的
`OrderService` 与 `OrderRepository`，不复制任何业务规则：

```java
package com.group7.shop.order;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.OrderItemView;
import com.group7.shop.dto.PageResult;
import com.group7.shop.entity.Customer;
import com.group7.shop.entity.Order;
import com.group7.shop.entity.OrderItem;
import com.group7.shop.entity.OrderStatus;
import com.group7.shop.repository.CustomerRepository;
import com.group7.shop.repository.OrderItemRepository;
import com.group7.shop.repository.OrderRepository;
import com.group7.shop.service.OrderService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 订单核心域对后台侧 OrderGateway 的实现：只做视图转换，不改动状态机与库存逻辑。 */
@Component
public class OrderGatewayImpl implements OrderGateway {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;

    public OrderGatewayImpl(OrderService orderService, OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository, CustomerRepository customerRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public PageResult<SellerOrderView> sellerOrders(String status, int page, int size) {
        OrderStatus filter = toStatus(status);
        List<Order> matched = new ArrayList<>();
        for (Order order : orderRepository.findAllByOrderByCreatedAtDescIdDesc()) {
            if (filter == null || order.getStatus() == filter) {
                matched.add(order);
            }
        }
        int from = Math.min((page - 1) * size, matched.size());
        int to = Math.min(from + size, matched.size());
        List<SellerOrderView> items = new ArrayList<>();
        for (Order order : matched.subList(from, to)) {
            items.add(toView(order, null));
        }
        return new PageResult<>(page, size, matched.size(), items);
    }

    @Override
    public PageResult<SellerOrderView> buyerOrders(Long customerId, int page, int size) {
        var pageData = orderService.listCustomerOrders(customerId, page, size);
        List<SellerOrderView> items = pageData.getContent().stream()
                .map(view -> toView(orderRepository.findById(view.id()).orElseThrow(), null))
                .toList();
        return new PageResult<>(page, size, pageData.getTotalElements(), items);
    }

    @Override
    public SellerOrderView advance(String orderNo, OrderAction action) {
        Order order = requireOrder(orderNo);
        OrderStatus target = switch (action) {
            case CONFIRM -> OrderStatus.SELLER_CONFIRMED;
            case PACK -> OrderStatus.READY_TO_SHIP;
            case SHIP -> OrderStatus.SHIPPED;
            case COMPLETE -> OrderStatus.COMPLETED;
        };
        OrderStatus current = order.getStatus();
        if (current.isTerminal() || target.ordinal() != current.ordinal() + 1) {
            /* 你的 advanceBySeller 抛的是 400，接口契约承诺 409，这里先拦一次 */
            throw BusinessException.conflict("订单状态不可回退或跳级：当前「" + labelOf(current) + "」");
        }
        orderService.advanceBySeller(order.getId(), target);
        return toView(requireOrder(orderNo), null);
    }

    @Override
    public SellerOrderView cancelBySeller(String orderNo, String reason) {
        Order order = requireOrder(orderNo);
        orderService.cancelBySeller(order.getId());
        return toView(requireOrder(orderNo), reason);
    }

    @Override
    public SellerOrderView cancelByBuyer(Long customerId, String orderNo, String reason) {
        Order order = requireOrder(orderNo);
        if (!order.getCustomerId().equals(customerId)) {
            throw BusinessException.notFound("订单不存在");
        }
        orderService.cancelByCustomer(customerId, order.getId());
        return toView(requireOrder(orderNo), reason);
    }

    private Order requireOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
    }

    private SellerOrderView toView(Order order, String cancelReason) {
        List<OrderItem> rows = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        List<OrderItemView> items = rows.stream()
                .map(item -> new OrderItemView(item.getProductId(), item.getProductName(), null,
                        item.getUnitPrice(), item.getQuantity(),
                        item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                        null))
                .toList();
        Customer customer = order.getCustomerId() == null ? null
                : customerRepository.findById(order.getCustomerId()).orElse(null);
        return new SellerOrderView(
                order.getOrderNo(),
                order.getCustomerId(),
                customer == null ? null : customer.getUsername(),
                customer == null ? null : customer.getPhone(),
                order.getStatus().name(),
                labelOf(order.getStatus()),
                order.getTotalAmount(),
                rows.size(),                  /* 种数，不是件数 */
                order.getCreatedAt(),
                order.getUpdatedAt(),
                actionsOf(order.getStatus()),
                cancelReason,
                items);
    }

    private static List<String> actionsOf(OrderStatus status) {
        return switch (status) {
            case CUSTOMER_SUBMITTED -> List.of(OrderAction.CONFIRM.name());
            case SELLER_CONFIRMED -> List.of(OrderAction.PACK.name());
            case READY_TO_SHIP -> List.of(OrderAction.SHIP.name());
            case SHIPPED -> List.of(OrderAction.COMPLETE.name());
            case COMPLETED, CANCELLED -> List.of();
        };
    }

    private static String labelOf(OrderStatus status) {
        String label = OrderStatusCodes.labelOf(status.name());
        return label == null ? status.name() : label;
    }

    private static OrderStatus toStatus(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return switch (OrderStatusCodes.normalize(code)) {
            case "PENDING" -> OrderStatus.CUSTOMER_SUBMITTED;
            case "CONFIRMED" -> OrderStatus.SELLER_CONFIRMED;
            case "PACKED" -> OrderStatus.READY_TO_SHIP;
            case "SHIPPED" -> OrderStatus.SHIPPED;
            case "COMPLETED" -> OrderStatus.COMPLETED;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            default -> throw BusinessException.badRequest("状态取值不合法：" + code);
        };
    }
}
```

> 说明：`toStatus` 里的 `case "PENDING"` 等写法依赖 `OrderStatusCodes.normalize` 先归一；
> 若 Java 版本对 String switch 的 null 分支敏感，可先判空再 switch（上面已判空）。

## 四、接上之后会发生什么

1. `GET /api/seller/orders` 由 `503` 变 `200`，返回真实分页数据；
2. `/api/orders` 同步可用（同一个网关）；
3. 前端卖家后台「订单管理」页签的按钮会自动出现，无需改前端；
4. 我这边**不需要改任何代码**——这也是当初设计软依赖的目的。

## 五、自测命令

```bash
# 卖家登录拿 token
curl -s -X POST localhost:8080/api/seller/login -H "Content-Type: application/json" \
     -d '{"username":"seller","password":"12345678"}'
# 订单列表
curl -s localhost:8080/api/seller/orders -H "X-Token: <token>"
# 推进（非法推进应为 409）
curl -i -X POST localhost:8080/api/seller/orders/<orderNo>/advance \
     -H "Content-Type: application/json" -H "X-Token: <token>" -d '{"action":"CONFIRM"}'
```

## 六、仍需订单域配合的两件事（我改不了）

1. `Order` 实体增加 `cancelReason` 字段——目前取消原因只在响应里回填，刷新即丢；
2. 跳级/回退建议由 `advanceBySeller` 直接抛 409（现在 400），否则每个调用方都要重复前置判断。
