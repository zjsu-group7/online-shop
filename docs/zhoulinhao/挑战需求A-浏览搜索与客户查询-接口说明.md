# 挑战需求 A：浏览搜索与客户查询接口说明

> 负责人：周琳皓
>
> 日期：2026-10-21
>
> 功能分支：`feature/iteration2-zhoulinhao-browse-orders`
>
> 依据：Wiki《接口设计》《业务流程图》《需求规格说明书》v3、《用例表》

## 1. 实现边界

本模块只负责以下只读能力：

1. 商品列表分页、关键词搜索、二级类别筛选、发布时间/价格排序。
2. 商品详情，多图按顺序返回，描述返回服务端已过滤的安全 HTML。
3. 卖家查看全部注册客户和指定客户的终态购买历史。

本模块不实现或修改商品发布、类别维护、库存、商品状态流转、买家登录与下单。旧接口 `GET /api/product` 暂时保留兼容，新页面应使用 `GET /api/products`。

所有响应继续使用统一格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 2. 商品列表

### `GET /api/products`

无需登录。

查询参数：

| 参数 | 必填 | 默认值 | 规则 |
| --- | --- | --- | --- |
| `q` | 否 | 空 | 同时匹配名称和描述，忽略大小写；空白等同不筛选；最长 100 字符 |
| `categoryId` | 否 | 空 | 二级类别 ID，必须为正整数 |
| `page` | 否 | `1` | 从 1 开始 |
| `size` | 否 | `12` | 1～100；前端标准页固定使用 12 |
| `sort` | 否 | `publishedAt,desc` | 见下表 |

`sort` 的正式取值：

| 值 | 含义 |
| --- | --- |
| `publishedAt,desc` | 发布时间从新到旧，默认 |
| `publishedAt,asc` | 发布时间从旧到新 |
| `price,asc` | 价格从低到高 |
| `price,desc` | 价格从高到低 |

兼容值：`createdAt,asc`、`createdAt,desc` 与对应的 `publishedAt` 含义相同。非法字段或方向返回 HTTP 400。

业务规则：

- 只返回 `ON_SALE`；`FROZEN`、`OFF_SHELF` 不进入列表。
- `q`、`categoryId` 可组合使用。
- 关键词中的 `%`、`_` 和反斜杠按普通字符匹配，不作为 SQL 通配符。
- 同一排序值下用商品 ID 稳定排序，翻页不会随机抖动。
- 列表使用轻量摘要，不返回富文本和交易内部字段；`images` 仅包含封面图，供最新前端直接渲染商品卡片。

成功响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "page": 1,
    "size": 12,
    "total": 25,
    "items": [
      {
        "id": 15,
        "name": "二手相机",
        "price": 1888.00,
        "imageUrl": "/uploads/cover.png",
        "images": ["/uploads/cover.png"],
        "categoryId": 2,
        "stock": 1,
        "status": "ON_SALE",
        "createdAt": "2026-10-21T06:00:00Z"
      }
    ]
  }
}
```

## 3. 商品详情

### `GET /api/products/{id}`

无需登录。只允许查看当前 `ON_SALE` 商品；不存在返回 404，冻结或下架返回 404 和“商品已下架或暂不可售”。

`data` 使用完整 `ProductView`，主要字段：

```json
{
  "id": 15,
  "name": "二手相机",
  "description": "<p><strong>安全富文本</strong></p>",
  "price": 1888.00,
  "imageUrl": "/uploads/cover.png",
  "images": [
    "/uploads/cover.png",
    "/uploads/detail-1.png"
  ],
  "categoryId": 2,
  "stock": 1,
  "status": "ON_SALE",
  "createdAt": "2026-10-21T06:00:00Z"
}
```

- `images` 按 `sort`、`id` 升序。
- 多图记录缺失时回退 `imageUrl`，保证存量单图商品仍可展示。
- `description` 在发布时已经白名单过滤；`script`、`iframe`、事件属性等不会返回。

## 4. 卖家客户列表

### `GET /api/seller/customers`

必须携带有效卖家请求头：

```http
X-Token: <seller-token>
```

按 SRS FR-S10 返回**全部注册客户**，按注册时间倒序。进行中订单不计入统计。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": 3,
        "username": "buyer01",
        "phone": "138****1234",
        "createdAt": "2026-10-21T06:00:00Z",
        "successCount": 1,
        "failureCount": 2,
        "orderCount": 3
      }
    ],
    "total": 1
  }
}
```

隐私规则：

- 11 位电话保留前 3 后 4 位，中间替换为 `****`。
- 其他长度电话仅保留前后各 2 位；4 位及以下全部隐藏。
- 不返回默认交易地点、密码哈希、盐、token。

## 5. 客户购买历史

### `GET /api/seller/customers/{id}/orders`

需要卖家 `X-Token`。客户不存在返回 404。

只返回该客户的终态记录：`TRADED → SUCCESS`、`FAILED → FAIL`；`WAITING`、`IN_TRADE`、`CANCELLED` 不返回。按下单时间倒序。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "customerId": 3,
    "username": "buyer01",
    "items": [
      {
        "intentId": 21,
        "productId": 15,
        "productName": "二手相机",
        "coverImage": "/uploads/cover.png",
        "submittedAt": "2026-10-21T06:00:00Z",
        "result": "SUCCESS",
        "resultAt": "2026-10-21T07:00:00Z"
      }
    ],
    "total": 1
  }
}
```

该接口复用买家“我的下单记录”的聚合规则，确保买卖双方看到的成功/失败口径一致。

## 6. HTTP 错误

| 状态 | 场景 |
| --- | --- |
| 400 | 页码、每页数量、分类 ID、排序格式或商品/客户 ID 不合法 |
| 401 | 卖家接口缺少 token、token 无效或误用买家 token |
| 404 | 商品、客户不存在，或商品不再可售 |

错误也保持 `{ code, message, data }`，其中失败响应的 `data` 为 `null`。

## 7. 代码位置

- `controller/BuyerProductController.java`
- `service/ProductQueryService.java`
- `dto/PageResult.java`
- `dto/ProductSummaryView.java`
- `controller/SellerCustomerController.java`
- `service/SellerCustomerQueryService.java`
- `dto/SellerCustomer*View.java`
- `test/query/BrowseAndCustomerQueryIntegrationTest.java`

## 8. 集成依赖与待确认项

1. 当前 Java 账号域采用 `customers + customer_order_links`；李婉婷迁移 SQL 同时存在 `buyers`，与通知中的 `intention.buyer_id` 也不一致。本模块不擅自重构他人数据模型，集成前由李婉婷统一迁移口径。
2. 华天遥的 Challenge A 库存与状态机分支已出现；本模块只读取最终状态，不修改状态流转，三方集成结果见测试记录。
3. 当前主干仍存在旧口令码与撤销意向接口；移除工作属于华天遥/李宇轩边界，本模块未改动。
4. 已兼容邱圣洁前端的 `createdAt,*` 排序值；商品列表同时返回 `imageUrl` 与单元素 `images` 封面数组。
