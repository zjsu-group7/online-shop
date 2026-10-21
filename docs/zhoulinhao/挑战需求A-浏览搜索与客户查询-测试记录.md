# 挑战需求 A：浏览搜索与客户查询测试记录

> 负责人：周琳皓
>
> 测试日期：2026-10-21
>
> 功能分支：`feature/iteration2-zhoulinhao-browse-orders`

## 1. 测试基线

- 最新主干：`origin/main@241ad18`（含李宇轩买家账号域）。
- 商品域依赖：`origin/feature/iteration2-liwanting@bf3275c`，以合并提交 `b00d70f` 接入本地功能分支，保留原作者记录。
- 未上传远程；等待本人审核后再推送并创建 PR。

环境：

- Windows 11
- Eclipse Temurin JDK 17.0.19
- Apache Maven 3.9.16
- Spring Boot 4.0.8
- H2 2.4.240（测试内存库）

## 2. 自动测试

专项测试文件：

`src/test/java/com/group7/shop/query/BrowseAndCustomerQueryIntegrationTest.java`

执行命令：

```text
mvn -B -Dtest=BrowseAndCustomerQueryIntegrationTest test
```

实际结果：

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全量回归命令：

```text
mvn -B test
```

最终提交前全量构建：

```text
mvn -B clean package
Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
生成 target/online-shop.jar
```

## 3. 测试用例与实测结果

| 编号 | 测试内容 | 预期结果 | 实测 |
| --- | --- | --- | --- |
| A-ZL-01 | 默认商品列表与第 2 页 | 每页 12、总数正确、发布时间倒序 | 通过 |
| A-ZL-02 | 在售状态过滤 | `FROZEN`、`OFF_SHELF` 不出现 | 通过 |
| A-ZL-03 | 名称/描述关键词 + 类别 + 价格排序 | 条件组合正确，忽略大小写 | 通过 |
| A-ZL-04 | 空白及 `%` 关键词 | 空白等同全部；`%` 按字面匹配 | 通过 |
| A-ZL-05 | 非法 page/size/categoryId/sort | HTTP 400，统一错误结构 | 通过 |
| A-ZL-06 | 商品详情多图和富文本 | 图片有序，XSS 标签/属性已移除 | 通过 |
| A-ZL-07 | 不存在/冻结/下架详情 | HTTP 404 | 通过 |
| A-ZL-08 | 存量商品无多图记录 | `images` 回退旧封面 | 通过 |
| A-ZL-09 | 列表轻量字段 | 不暴露描述、多图和交易历史字段 | 通过 |
| A-ZL-10 | 客户列表鉴权 | 无 token 或买家 token 返回 401 | 通过 |
| A-ZL-11 | 全部注册客户与脱敏 | 无订单客户也展示，电话正确脱敏 | 通过 |
| A-ZL-12 | 客户终态订单统计 | 仅统计成功/失败，不统计排队中 | 通过 |
| A-ZL-13 | 指定客户购买历史 | 只返回该客户成功/失败，按时间倒序 | 通过 |
| A-ZL-14 | 客户不存在 | HTTP 404，统一错误结构 | 通过 |

## 4. 安全与性能检查

- 商品列表只返回轻量摘要，避免重复传输富文本和最多 9 张详情图。
- 商品列表封面直接读取 `Product.image`，没有逐商品图片查询的 N+1 问题。
- 关键词参数使用 Criteria API，并对 `%`、`_`、反斜杠转义。
- 卖家客户接口由现有 `/api/seller/**` 拦截器统一保护。
- 客户列表不返回默认地点、密码哈希、盐、token，电话按规则脱敏。
- 历史查询以路径客户 ID 定位，不能混入其他客户记录。

## 5. JAR 启动与 HTTP 冒烟

实际启动命令：

```text
java -jar target/online-shop.jar --server.port=18082 --spring.datasource.url=jdbc:h2:mem:challengeA;DB_CLOSE_DELAY=-1 --spring.jpa.hibernate.ddl-auto=create-drop
```

实测链路：类别树 → 卖家登录 → 发布商品 → 组合条件列表 → 商品详情 → 注册客户 → 卖家客户列表。

```text
CategoryCode: 200
PublishCode: 200
ProductListCode: 200
ProductListMilliseconds: 26
ProductTotal: 1
ListHasDescription: false
DetailCode: 200
DetailImageCount: 1
DetailContainsScript: false
CustomerRegisterCode: 200
CustomerListCode: 200
CustomerTotal: 1
MaskedPhone: 138****1234
```

结论：可执行 JAR 能启动，关键接口真实 HTTP 响应正确；本次 26 ms 是本机空载 H2 小样本结果，只证明当前验收环境满足 2 秒目标，不代表生产压力测试结果。

## 6. 尚未解决的问题

1. 数据模型存在跨组不一致：运行代码为 `customers + customer_order_links`，迁移 SQL 另有 `buyers`，任务通知又要求 `intention.buyer_id`。本模块按当前可运行代码只读复用，未越权改表。
2. 华天遥库存/状态机 Challenge A 分支尚未出现，成交扣库存、失败 `failedAction` 等最终联调尚不能执行。
3. 邱圣洁前端 Challenge A 分支尚未出现，四个接口的浏览器联调与 Chrome/Edge 截图尚待完成。
4. 当前主干保留旧口令码与买家撤销入口的后端能力，与本周停用口径仍有差异；不属于周琳皓修改范围。
5. Wiki 通知提到《需求规格说明书》第 10 章，但当前 v3 文档只有第 1～9 章；本实现依据第 3～6、8～9 章及《接口设计》完成。
