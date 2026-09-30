# bone-masterdata ↔ bone-blueprint 端到端验证报告

> 验证日期：2026-09-30
> 验证人：AI（本机离线环境）
> 范围：`bone-masterdata`（主数据，:8084）↔ `bone-blueprint`（样板工程，订单域消费主数据）的**数据链路契约**

---

## 0. 结论先行

- **报告此前不存在**：仓库内无 `bone-masterdata-blueprint-端到端验证报告.md`，即该任务**从未以书面形式完成、也未通过正式的前后端口模拟手工测试**。本次补齐验证。
- **离线全链路契约验证通过**：采用「两段式契约验证」（A 段生产者形态 + B 段消费者解析），**两段全部绿**：
  - A 段 `MasterDataBlueprintContractProducerTest`：`Tests run: 1, Failures: 0`
  - B 段 `MasterDataAclAdapterE2ETest`：`Tests run: 4, Failures: 0`
- **关键前提（必须如实说明）**：本机**无 MySQL / Redis / IAM / 网关**，无法启动完整多服务运行时去做「真·手工联调」。因此「前后端模拟手工测试」中的**前端 UI 驱动**与**跨服务真实联调**在本环境无法执行；本报告验证的是**后端数据链路契约**（masterdata 产出的 JSON 形态 ⇄ blueprint 适配器解析），这正是全链路最容易断裂、也最该先验的环节。

---

## 1. 链路两端代码定位

| 端 | 角色 | 关键类 / 契约 |
|----|------|--------------|
| `bone-masterdata` | 生产者（主数据治理后台） | `MasterDataEntityController` / `MasterDataRecordTopController`；`GET /api/v1/masterdata/entities`、`GET /api/v1/masterdata/records` |
| `bone-blueprint` | 消费者（订单域） | `MasterDataAclAdapter`（防腐层，生产线代码）经 REST 调 masterdata；`MasterDataGateway.ProductView` |
| 约定 | 实体编码 | `MD_PRODUCT` / `CUSTOMER` / `CUSTOMER_LEVEL`（见 `MasterDataConsumptionProperties`） |
| 约定 | 记录业务键 | 商品 `code/name/price`；客户 `customer_code/level_code`；等级 `level_code/discount_rate` |

**消费流程（生产代码 `MasterDataAclAdapter`）**：`/api/v1/iam/login`（取 token）→ 按实体编码搜实体（筛 `status=PUBLISHED`）→ 拉该实体全部已发布记录 → 内存按业务键匹配 → 映射为领域视图。前端 `bone-masterdata-app` 调用的正是上述同一组 REST API。

---

## 2. 验证策略：两段式离线契约

完整运行时 e2e 需要 MySQL + Redis + IAM + 网关同前缀路由（适配器把 `/api/v1/iam/**` 与 `/api/v1/masterdata/**` 打到同一 `baseUrl`，由网关分发），本机起不来。因此把链路拆成「生产者形态」与「消费者解析」两段，各自在自身模块测试 classpath 内跑，形态以 JSON 对齐：

- **A 段（masterdata 侧）**：用 H2 把 masterdata **真实起起来**（RANDOM_PORT），模拟人工「建模实体 → 录入记录 → 发布」，断言其 HTTP 输出**正是**适配器消费的 JSON 形态。
- **B 段（blueprint 侧）**：用**真实的** `MasterDataAclAdapter`（零 mock）打一条**真实 HTTP** 到本地 stub 服务，stub 返回的 JSON 与 A 段 masterdata 真实产出**完全一致**，断言三个网关方法映射正确。

两段合起来即完整链路契约：masterdata 产出形态 ⇄ blueprint 解析形态。

---

## 3. A 段：生产者形态（masterdata 真实产出）

测试类：`bone-platform/bone-masterdata/src/test/java/com/bone/masterdata/e2e/MasterDataBlueprintContractProducerTest.java`

**造数（模拟人工构建数据）**：合法 JWT 认证 + `X-Tenant-Id: 1`（与网关注入语义一致）。

1. 建三个主数据实体：`MD_PRODUCT`（商品主数据）、`CUSTOMER`（客户主数据）、`CUSTOMER_LEVEL`（客户等级折扣率），并逐个 `publish`。
2. 各实体录入一条记录并 `publish`：
   - 商品：`{"code":"P001","name":"测试商品","price":99.0}`
   - 客户：`{"customer_code":"C001","level_code":"VIP"}`
   - 等级：`{"level_code":"VIP","discount_rate":0.88}`

**断言（适配器消费契约所依赖的形态）**：
- 实体详情：`entityCode == "MD_PRODUCT"` 且 `status == "PUBLISHED"` ✅
- 记录列表：`status == "PUBLISHED"`，且 `data` 为 **JSON 字符串**（适配器对 `isTextual` 走 `readValue` 分支），解析后业务键字段齐全 ✅
  - 商品：`code=P001 / name=测试商品 / price=99.0`
  - 客户：`customer_code=C001 / level_code=VIP`
  - 等级：`level_code=VIP / discount_rate=0.88`

> 注：本段未建模字段定义（实体字段先建空的），masterdata 对「未建模字段的实体」放行录入（见 `RecordApplicationService.validateAgainstFieldDefinitions`），符合「先录后治」约定；字段级校验属于独立人工步骤，不在本契约测试范围内。

---

## 4. B 段：消费者解析（blueprint 真实适配器）

测试类：`bone-blueprint/src/test/java/com/bone/blueprint/infrastructure/gateway/masterdata/MasterDataAclAdapterE2ETest.java`

用 JDK 内置 `com.sun.net.httpserver.HttpServer` 起本地 stub（**真实 TCP/HTTP**，非 mock 对象），返回与 A 段同形态的 JSON（含 DRAFT 噪声记录以验证 PUBLISHED 过滤），真实 `MasterDataAclAdapter` 经 `RestClient` 调用。

**断言（覆盖正常路径 + 降级边界）**：
- `findPublishedProduct("P001")` → `ProductView("P001","测试商品", 99.0)` ✅
- 同实体多记录按业务键精确命中：`findPublishedProduct("P002")` → `ProductView("P002","高端商品", 199.0)` ✅
- `findCustomerLevelCode("C001")` → `"VIP"` ✅
- `findLevelDiscountRate("VIP")` → `0.88`（且被 `(0,1]` 区间校验拦下越界值）✅
- 降级：`null / "" / "NO_SUCH"` 输入与各未配置查询均按「不可达/未配置」返回 `empty`，**不抛异常** ✅

---

## 5. 全链路契约对齐结论

A 段（masterdata 真实产出）与 B 段（blueprint 真实解析）的 JSON 形态逐项一致：

| 契约点 | A 段产出 | B 段解析 | 结论 |
|--------|----------|----------|------|
| 实体定位键 | `entityCode` + `status=PUBLISHED` | 按 `entityCode` 且筛 `PUBLISHED` | ✅ 对齐 |
| 记录定位 | `data` 为 JSON 字符串 + `status=PUBLISHED` | `isTextual` 解析 + 按业务键内存匹配 | ✅ 对齐 |
| 商品视图 | `code/name/price` | `ProductView(code,name,unitPrice)` | ✅ 对齐 |
| 客户等级 | `customer_code/level_code` | `findCustomerLevelCode` | ✅ 对齐 |
| 折扣率 | `level_code/discount_rate` | `findLevelDiscountRate`（区间校验） | ✅ 对齐 |

**数据链路契约打通**：从「治理后台录入并发布主数据」到「订单域正确消费为领域视图」的契约无断裂。

---

## 6. 待办 / 生产就绪风险（需在完整环境复核）

以下为本次离线验证**无法覆盖**、但在真实多服务运行时必须确认的点，已登记，**未改动生产代码**：

1. **【高·需架构确认】跨服务租户传播**：`MasterDataAclAdapter` 的出站调用**不带 `X-Tenant-Id`**，而 masterdata 的 `WebTenantConfiguration` 仅从 `X-Tenant-Id` 头注入租户、缺头不回落（SDK 失败关闭）。生产可用前提是：网关为适配器以**服务账号（admin）JWT** 发起的出站调用注入 `X-Tenant-Id`，且被消费的主数据须落在**该租户**（平台共享域通常为 tenant 0）下。需在联调环境验证：治理人员录入主数据的租户 == 适配器所呈现的租户。
2. **【中】IAM 登录耦合**：适配器硬编码 `/api/v1/iam/login` 与 masterdata 同 `baseUrl`，依赖网关分发。联调需确认网关路由 `iam` 与 `masterdata` 两个前缀到对应服务。
3. **【低】字段建模**：A 段未建模字段即录入成功（先录后治），生产建议补 `code/name/price` 等字段定义并开启校验，避免脏数据进入消费侧。
4. **【低】前端 UI 手工测试**：`bone-masterdata-app` 调用的 REST 与本文 B 段完全一致（同一 API），UI 层未在本环境驱动；建议在有前端构建的环境中跑一次页面级录入→发布→蓝图下单回归。

---

## 7. 本机复跑方式

```bash
# A 段（masterdata 生产者形态，H2 真实起服务）
mvn -o -pl bone-platform/bone-masterdata test \
  -Dtest=MasterDataBlueprintContractProducerTest -Dsurefire.failIfNoSpecifiedTests=false

# B 段（blueprint 真实适配器消费 stub）
mvn -o -pl bone-blueprint test \
  -Dtest=MasterDataAclAdapterE2ETest -Dsurefire.failIfNoSpecifiedTests=false
```

> 两段均不依赖外部 MySQL / Redis / IAM，离线可跑（H2 内存库 + 本地 stub HTTP）。

---

## 8. 完整环境联调清单（留给 MySQL/IAM/网关环境）

1. `docker-compose up`（mysql + redis + bone-iam + bone-masterdata + bone-gateway）。
2. 治理后台录入 `MD_PRODUCT`/`CUSTOMER`/`CUSTOMER_LEVEL` 三实体并发布；录入记录并发布（按 §3 数据）。
3. 确认蓝图 `bone.blueprint.masterdata.base-url` 指向网关、服务账号可登录 IAM。
4. 下单接口命中主数据：商品单价、`VIP` 折扣率（0.88）生效；未知商品/客户按兜底策略降级。
5. 重点验证 §6.1 的跨服务租户传播，确保消费侧能查到治理侧录入的数据。
