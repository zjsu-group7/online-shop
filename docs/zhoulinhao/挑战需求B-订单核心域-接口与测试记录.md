# 挑战需求 B：订单核心域（周琳皓）

日期：2026-11-16。基于李婉婷 `iteration4-liwanting` 的订单表和库存锁定骨架、华天遥 `iteration4-huatianyao` 的购物车代码开发。

## 接口与服务契约

买家接口均沿用 `/api/customer/**` 的 `X-Token` 鉴权：

| 方法 | 路径 | 输入 | 输出 |
|---|---|---|---|
| POST | `/api/customer/purchase-orders` | `{ "cartItemIds": [1, 2] }` | `order`（订单或 null）及 `rejected`（逐件缺货/下架提示） |
| GET | `/api/customer/purchase-orders?page=1&size=12` | 页码从 1 开始 | 按创建时间、ID 倒序的本人订单分页 |
| POST | `/api/customer/purchase-orders/{id}/cancel` | 本人订单 ID | 已取消订单及库存释放结果 |

卖家后台由李宇轩负责接入 `OrderService.advanceBySeller(orderId, nextStatus)` 和 `cancelBySeller(orderId)`；状态推进只允许下一个状态，不可回退或跳级。

五个业务进度状态为 `CUSTOMER_SUBMITTED → SELLER_CONFIRMED → READY_TO_SHIP → SHIPPED → COMPLETED`；另有 `CANCELLED` 终态。客户只能在发货前取消；商家可在完成前取消。完成扣减 `stock` 并释放锁定数；取消只释放锁定数。

下单使用商品悲观写锁逐件校验 `stock - lockedStock`。可买商品在同一个订单内生成订单项，保存名称与单价快照、数量、订单号和总金额，并从购物车移除；缺货条目保留在购物车且返回原因。全部缺货时不生成空订单。

## 实际验证

- `OrderServiceTest`：部分缺货仍生成可买订单、价格数量快照、取消释放锁定、跳级/终态拒绝、成交扣库存及发货后买家取消拒绝。
- 定向测试：3 项通过，0 失败。
- 基于华天遥分支的完整 `mvn clean package`：103 项通过、0 失败、0 错误、0 跳过，`BUILD SUCCESS`，生成 `target/online-shop.jar`。

## 联调事项

现有 `/api/customer/orders` 是挑战需求 A 的旧意向下单/历史接口。订单新接口暂用 `/api/customer/purchase-orders` 避免覆盖旧契约；李宇轩更新《接口设计》时应统一最终路径并决定旧接口迁移方式。李婉婷仍需完成商品两态化、旧意向交易链路替换及数据库迁移集成。本分支未修改这些组员负责的代码。
