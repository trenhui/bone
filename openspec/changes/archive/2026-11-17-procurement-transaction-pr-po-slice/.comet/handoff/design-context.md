# Comet Design Handoff

- Change: procurement-transaction-pr-po-slice
- Phase: design
- Mode: compact
- Context hash: 61084047420555f81ebf045eaf8d83e1af16ee5138d71bfaa190f693fee46e60

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procurement-transaction-pr-po-slice/proposal.md

- Source: openspec/changes/procurement-transaction-pr-po-slice/proposal.md
- Lines: 1-59
- SHA256: 4f80c9faa5ac6e2c471291aff377be39e3ef602bce623e0ba0c872e17671dc5d

```md
# 采购交易 PR/PO 可验证切片

## Why

采购域 01 供应商主数据已交付（Supplier/Vendor 双角色状态机 + 5 个治理端点 + 前端治理页），但 04 交易模块在代码中零实现：`bone-init.sql` 无任何 `t_proc_*` 表，仓库无 `Requisition`/`PurchaseOrder` 聚合，也没有 `/api/v1/procurement/*` 端点。结果是平台能判定「供应商是否合格」，却无法完成「实际下单」这一步，整条 S2P 主链路断在供应商之后。

现在做的另一个理由是依赖恰好就绪：PO 下单最硬的前置是 Vendor 开通闸门（LLD 04 卷 M4-1/M4-2、总体 §12.2 必过项），而该闸门的数据源正是上一轮已交付并验证过的 `SupplierApplicationService.currentState()`。同时 PO 是 05 履约（Ack/ASN/GR/三单匹配）的操作对象，先有 PO 才能让履约模块拿到真实上游，而不是靠桩数据。

## What Changes

- **新增采购域模块组**（对齐总体 §4.2 模块划分与 §8.1 单应用多模块，应用范式对齐 `bone-apps/synap-claim`）：
  - `bone-apps/bone-procurement`（pom 聚合器）
  - `bone-apps/bone-procurement/bone-procurement-app`（Spring Boot 应用与装配，仅启动类/配置/能力开关）
  - `bone-apps/bone-procurement/bone-procurement-transaction`（交易上下文业务模块，DDD 四层）
- **新增 4 张表** 写入 `bone-init.sql`：`t_proc_requisition`、`t_proc_po`、`t_proc_po_line`、`t_proc_price_snapshot`；金额列统一 `DECIMAL(19,4)` + `currency CHAR(3)`，时间列 UTC `DATETIME(3)`，均带租户隔离与 `@Version` 乐观锁列
- **新增 6 个端点**（`/api/v1/procurement`）：
  - `POST /requisitions` 建请购
  - `POST /requisitions/{no}/convert` 转 PO（一次）
  - `POST /purchase-orders` 建 PO（含 Vendor 闸门校验）
  - `POST /purchase-orders/{no}/submit` 提交审批
  - `POST /purchase-orders/{no}/send` 下达并冻结价格快照
  - `GET /purchase-orders/{no}` 查详情（含行与快照引用）
- **新增领域模型与状态机**（显式迁移表 + fail-closed，未列出即拒绝）：
  - `Requisition`：`DRAFT → SUBMITTED → APPROVED → CONVERTED`，`CANCELLED` 终态
  - `PurchaseOrder`：`DRAFT → SUBMITTED → APPROVED → SENT`，`ACKNOWLEDGED`/收货段状态保留枚举但本期不推进；`CANCELLED` 终态
  - `PurchaseOrderLine`：随根一致性，`po_amount = Σ line_amount`
  - `PriceSnapshot`：只追加、不可变，含 `computed_hash`
- **Vendor 闸门**：通过业务侧 `VendorQueryPort` 读供应商投影；非 `ACTIVE_VENDOR` → `31003`，法人实体不匹配 → `31004`
- **前端交易控制台**：请购单与 PO 的列表、建单、提交/下达、详情；落在**新建独立子应用** `bone-frontend/apps/bone-procurement-app`（3014 / activeRule `/procurement`），复刻平台既有 qiankun 范式；供应商治理页从 `bone-masterdata-app` 迁入该应用
- **新增权限码**：`procurement:requisition:write/read`、`procurement:purchase-order:write/read`、`procurement:transaction:read/write`（含菜单种子）

## Capabilities

### New Capabilities

- `procurement/requisition`: 请购单从建单到转采购订单的生命周期行为，含审批后才可转单、转单后终态、预算引用与金额校验
- `procurement/purchase-order`: 采购订单生命周期、Vendor 开通闸门与法人隔离、订单金额与行金额一致性、下达时价格快照冻结与不可变、终态不可回写、非法迁移拒绝
- `procurement/transaction-console`: 交易控制台前端界面行为，含请购与 PO 的列表分页、建单表单、状态推进按钮的允许事件展示、失败降级与权限降级

### Modified Capabilities

`procurement/supplier-mdm-console`（前端落点变更）：供应商治理页从 `bone-masterdata-app` 迁入 `bone-procurement-app`，**行为契约不变**（5 端点、双角色状态、降级与 PII 白名单），仅改宿主应用、路由前缀（`/masterdata#/suppliers` → `/procurement#/suppliers`）与菜单种子；测试随文件迁移并保持全绿。后端 `procurement/supplier-mdm` 需求不变。

## Impact

- **新增代码**：3 个 Maven 模块（含 1 个聚合器）、`com.bone.procurement.transaction` 四层代码；新建前端子应用 `bone-procurement-app`（含 shell 注册与硬编码清单同步）与交易控制台页面
- **DDL**：`bone-init.sql` 新增 4 张 `t_proc_*` 表（本期首次引入采购业务表，此前该文件 `t_proc_` 零命中）
- **API**：6 个新端点与 6 个新权限码（requisition/purchase-order/transaction 各 read+write）；错误码沿用 LLD 04 卷 `31001–31004` / `31401–31405`
- **菜单/权限种子**：新增 2 个 migration SQL（权限码绑超管 + 「采购」菜单目录与 2 个叶子），须通过 G40 权限码与图标白名单门禁
- **依赖**：`bone-procurement-transaction` 通过 `VendorQueryPort` 依赖 `bone-masterdata` 的供应商状态查询；不直连对方表
- **不改动**：存量 `bone-apps/bone-app-purchase` 适配层（12 类，按能力基线 §7.2 路径 A 保留，不重写不删改）
- **明确非目标**：
  - Transfer / RTV 及其补偿编排
  - `PurchaseCost` + `PriceCondition` 条件引擎、PPV / Savings / 价格合规率投影（价格链留待与 03 合同价格链一起做；本期快照仅冻结行净价）
  - 预算系统真实占用/释放（`BudgetAclPort` 一期无真实上游，L19 待决；本期只记录 `budget_ref` 与金额，不做跨系统占用）
  - GR 收货推进、ASN/Ack、发票匹配与付款（属 05 履约与 Finance 域，PA-04 边界）
  - Outbox / Inbox / MQ / Saga（单应用单库，PA-08 明确不引入）
  - `bone-procurement-app` 的 Orchestrator 跨上下文编排（本期无编排需求）
- **已知偏差（如实记录，不在本期偿还）**：上一轮供应商模块落在 `bone-masterdata`（包 `com.bone.masterdata`），与 §4.2 要求的 `bone-procurement-supplier-mdm` 不符；本期不搬迁，作为独立后续 change 偿还

```

## openspec/changes/procurement-transaction-pr-po-slice/design.md

- Source: openspec/changes/procurement-transaction-pr-po-slice/design.md
- Lines: 1-177
- SHA256: 5d31230b7d798d9a7aa9174a8e471bc13a49f3e14e752d9d2c79617e4508dfbf

[TRUNCATED]

```md
---
comet_change: procurement-transaction-pr-po-slice
role: technical-design
canonical_spec: openspec
---

# 技术设计：采购交易 PR/PO 切片

## Context

动机与范围见 `proposal.md`。本设计只回答"怎么落地"。影响方案的关键现状约束：

- **应用范式**：`bone-apps/synap-claim` 是"pom 聚合器 + `synap-claim-app`（`ClaimApplication`，四层代码直接放在 app 内）+ 附属模块"；其 app 的 `application.yml` 用 `BONE_DB_*` 环境变量、`metadata.sdk.appcode`、`sql.init.mode: never`，启动类用 `@SpringBootApplication` + `@EnableSqlRepositories` + `@Import(MetadataAutoConfiguration, SqlRepositoryAutoConfiguration)`。
- **部署形态**：总体 §8.1 + PA-08 规定一期"单应用多模块 + 单库"，单元内本地事务 + 应用服务编排，不引入 Outbox/MQ/Saga。`bone-apps/bone-app-purchase` 是 autoconfiguration 适配层（无启动类、无实体），按能力基线 §7.2 路径 A 保留不动。
- **供应商能力**：`bone-masterdata` 已提供 `SupplierApplicationService.currentState(partyId)` → `SupplierStateDTO`（含 `vendorState`，取值 `PENDING_VENDOR` / `ACTIVE_VENDOR` / `PAYMENT_BLOCKED` / `INACTIVE_VENDOR`）。
- **持久化纪律**：HC-006 要求持久化唯一走 metadata-sdk（`@Table`/`@Column` 驱动，领域仓储接口继承 SDK `Repository`，由 `@EnableSqlRepositories` 生成实现）；`AbstractEntity` 审计列只能用 `java.util.Date`。
- **分层纪律**：ArchUnit `BoneDddArchRules` 约束 `adapter → application → domain ← infrastructure`。

## Goals / Non-Goals

**Goals**

- 用最小模块数量承载 PR/PO 上下文，且结构可平级追加 `-sourcing` / `-fulfillment` 而不改应用壳。
- 让 Vendor 闸门成为**可注入替换**的端口，测试可用内存实现，生产直连供应商应用服务。
- 状态机与金额不变量在 domain 层强制，HTTP 层只做协议转换。
- 现有供应商模块零改动。

**Non-Goals**

- 不做 Orchestrator 跨上下文编排（`bone-procurement-app` 只做装配）。
- 不做条件价格引擎、PPV/Savings 投影、Transfer/RTV、GR 收货、发票匹配与付款。
- 不做真实预算系统占用（无上游契约，L19 待决）。
- 不搬迁供应商模块、不改 `bone-app-purchase`。

## Decisions

### D1 模块结构：聚合器 + 薄应用壳 + 交易上下文模块

```
bone-apps/bone-procurement/                     pom 聚合器（parent=bone-parent）
├── bone-procurement-app/                       Spring Boot 应用（有 main，只做装配）
└── bone-procurement-transaction/               交易上下文（PR/PO 四层代码）
```

`bone-procurement-app` 只放 `ProcurementApplication`、`application.yml`、能力开关配置与装配测试；业务代码全部在 `bone-procurement-transaction`。

**理由**：照搬 synap-claim 会把业务代码塞进 app，而 synap-claim 只有理赔一个上下文；采购有 8 个上下文（§4.2），塞进 app 会变巨型模块且无法平级扩展。拆分后 `-sourcing` / `-fulfillment` 只需在聚合器加 module 并在 app 加依赖，应用壳结构不变。

**备选**：① 全部代码放 app（简单，但不可扩展，否决）；② 9 个模块一次性建齐（违反"不为不存在的边界付复杂度"，否决）。

**依赖方向**：`app → transaction`；`transaction → bone-masterdata`（仅通过端口接口与 DTO，不依赖其 internal 包）。不反向依赖 `bone-app-purchase`。

### D2 包结构

```
com.bone.procurement.transaction
├── domain/model/requisition   Requisition、RequisitionLine、RequisitionStatus、RequisitionTransition
├── domain/model/po            PurchaseOrder、PurchaseOrderLine、PoStatus、PoTransition
├── domain/model/pricing       PriceSnapshot、Money、SnapshotHash
├── domain/port                VendorQueryPort
├── domain/repository          RequisitionRepository、PurchaseOrderRepository、PriceSnapshotRepository
├── application                RequisitionApplicationService、PurchaseOrderApplicationService、OrderNoGenerator
├── application/command|query  Command、Query 与 DTO
├── adapter/web/controller     RequisitionController、PurchaseOrderController
├── adapter/web/dto            请求/响应 DTO（含 ApiResponse 信封）
└── infrastructure             VendorQueryPortAdapter、config、能力开关
```

`VendorQueryPortAdapter` 放在 transaction 的 infrastructure，注入 `bone-masterdata` 的 `SupplierApplicationService`，把 `SupplierStateDTO` 投影为本上下文的 `VendorSnapshot`（只含 vendorState、partyId、legalEntityId）。

**备选**：把 adapter 放 `bone-masterdata` 侧。否决——那会让主数据反向依赖交易，违反依赖方向。

### D3 金额用 Money 值对象，不用裸 BigDecimal

`Money(BigDecimal amount, String currency)`，`amount` 统一 `scale=4`、`RoundingMode.HALF_UP`，跨币种运算直接抛异常。行金额 = `netPrice × qty × (1 + taxRate)` 按行舍入后再汇总，**先逐行舍入再求和**（避免总额与行合计漂移，对齐 LLD §13.1 的舍入要求）。

**理由**：`po_amount = Σ line_amount` 是强一致不变量，若用裸 `double` 或延迟舍入，会出现"显示一致、校验不等"的经典缺陷。

**备选**：全程 `BigDecimal` 不带币种（简单，但跨币种错误无法早暴露，否决）。


```

Full source: openspec/changes/procurement-transaction-pr-po-slice/design.md

## openspec/changes/procurement-transaction-pr-po-slice/tasks.md

- Source: openspec/changes/procurement-transaction-pr-po-slice/tasks.md
- Lines: 1-52
- SHA256: fd3062565c8ca135bb75b85d5fbe6c111677a112adf91c737f589a06c812d350

```md
# 任务清单

## 1. 模块骨架与构建接入

- [x] 1.1 新建 `bone-apps/bone-procurement` 聚合器 pom（packaging=pom，modules 含 app 与 transaction），并在 `bone-apps/pom.xml` 增加 `<module>bone-procurement</module>`。验证：`mvn -o -f bone-apps/pom.xml validate` 通过且新模块出现在 reactor 列表。 <!-- comet-task:83602e67-4b34-4a5b-a2e1-22f929670a03 -->
- [x] 1.2 新建 `bone-procurement-transaction` 模块（parent 指向聚合器，依赖 bone-core / bone-security / bone-web / bone-metadata-sdk / spring-boot-starter-web / validation / test），建立 `com.bone.procurement.transaction` 四层包骨架。验证：`mvn -o -f bone-apps/bone-procurement/pom.xml -pl bone-procurement-transaction compile` 通过。 <!-- comet-task:7f3691aa-7595-45ea-98f2-925af697229a -->
- [x] 1.3 新建 `bone-procurement-app` 模块（`ProcurementApplication` + `application.yml` + 能力开关配置类），装配口径对齐 `ClaimApplication`（`@EnableSqlRepositories` + `@Import(MetadataAutoConfiguration, SqlRepositoryAutoConfiguration)`）。验证：`mvn -o -f bone-apps/bone-procurement/pom.xml -pl bone-procurement-app compile` 通过，且装配测试能加载上下文。 <!-- comet-task:0c68b0e0-2127-41eb-aadd-29804c87b056 -->

## 2. DDL 与持久化映射

- [x] 2.1 在 `bone-init.sql` 追加 `t_proc_requisition` / `t_proc_po` / `t_proc_po_line` / `t_proc_price_snapshot` 四张表，含必备列（id / tenant_id / 审计四列 / deleted / version）、唯一键与索引，金额列 `DECIMAL(19,4)` + `currency CHAR(3)`。验证：DDL 在测试库执行无语法错误，唯一键与索引可查。 <!-- comet-task:4555e3e2-c66d-41f3-973b-889490c6b936 -->
- [x] 2.2 用 metadata-sdk 实体（`@Table`/`@Column`）映射四张表，领域仓储接口继承 SDK `Repository`，审计列用 `java.util.Date`。验证：新增「DDL 列 ↔ 实体字段」对账测试，任一列未映射即失败；查询实体可正常往返读写。 <!-- comet-task:459c5989-61cb-42f0-be7e-a707d9fbb8af -->

## 3. 领域模型与不变量（先写测试）

- [x] 3.1 实现 `Money` 值对象：`scale=4`、`HALF_UP`、跨币种运算抛错；行金额按行舍入后再汇总。验证：单测覆盖四位小数保留、逐行舍入求和、跨币种抛错三个用例。 <!-- comet-task:d62c69ad-0c47-491b-93db-b7f1a25f1ece -->
- [x] 3.2 实现 `Requisition` 状态机（显式迁移表，未列出即拒绝）：`DRAFT → SUBMITTED → APPROVED → CONVERTED`，`CANCELLED` 终态。验证：合法迁移通过；`DRAFT` 直接转单、`CONVERTED` 回写、`CANCELLED` 再提交三类用例均被拒绝。 <!-- comet-task:5651e379-637c-4921-8f64-5634bf748459 -->
- [x] 3.3 实现 `PurchaseOrder` 状态机与金额一致性不变量：`DRAFT → SUBMITTED → APPROVED → SENT`，`CANCELLED` 终态，`po_amount = Σ line_amount`。验证：总额与行合计不等被拒；`DRAFT → SENT` 跳步被拒；`CLOSED`/`CANCELLED` 回写被拒。 <!-- comet-task:071a51d6-5163-4745-8534-37aa87cc7151 -->
- [x] 3.4 实现 `PriceSnapshot` 计算与 `computed_hash`：覆盖行净价、税额、币种、汇率取数、舍入口径、税码版本；插入后不可更新。验证：同输入两次计算哈希一致；重复触发不新增第二行同版本快照。 <!-- comet-task:8fe26667-f25a-4329-97f6-26f9cbb51de4 -->

## 4. 应用服务与端口

- [x] 4.1 定义 `VendorQueryPort` 与 `VendorQueryPortAdapter`（注入 `SupplierApplicationService.currentState`，投影为 `VendorSnapshot`）；提供测试用内存实现。验证：端口契约测试用内存实现通过；Spring 上下文装配测试确认生产 adapter 可注入。 <!-- comet-task:26b58f6c-a088-4a83-826f-56cc6393faa4 -->
- [x] 4.2 实现 `RequisitionApplicationService`：建单、提交、审批（通过/驳回）、转 PO、取消。验证：未审批转单被拒且状态不变；`CONVERTED` 重复转单被拒；转单失败（供应商未开通）时请购单保持 `APPROVED`。 <!-- comet-task:79a9ffe1-60e6-4d28-8d90-c313ba4ff14d -->
- [x] 4.3 实现 `PurchaseOrderApplicationService`：建单（Vendor 闸门 + 法人校验 + 金额一致）、提交、下达（冻结快照）、查询详情。验证：M4-1（`31003`）、M4-2（`31004`）、M4-5（`31402`）、M4-10（`31405`）四类负向用例全部命中。 <!-- comet-task:04099025-2bda-463a-898f-20b700a1e90e -->
- [x] 4.4 实现 `OrderNoGenerator`：`PR-{yyyy}-{6 位}`、`PO-{yyyy}-{6 位}`，按租户+类型+年份取最大值 +1，唯一约束冲突重试上限 3 次。验证：连续生成不重复；模拟冲突时重试生效且超限返回可读错误。 <!-- comet-task:93c2162d-8cb3-47a9-b894-140afdb13cca -->

## 5. HTTP 端点与契约

- [x] 5.1 实现 `RequisitionController` 两个端点：`POST /api/v1/procurement/requisitions`、`POST /api/v1/procurement/requisitions/{no}/convert`，统一响应信封，权限码 `procurement:requisition:write/read`。验证：契约测试锁定 path / method / 请求体 / 状态码。 <!-- comet-task:ad2996cc-be0b-4a43-9135-79c1e62e89c9 -->
- [x] 5.2 实现 `PurchaseOrderController` 五个端点：`POST /purchase-orders`、`GET /purchase-orders`（列表分页，tenantId+status+page+size）、`POST /purchase-orders/{no}/submit`、`POST /purchase-orders/{no}/send`、`GET /purchase-orders/{no}`。验证：契约测试锁定路由与响应字段，详情返回行与快照引用，列表按租户锚定并收窄分页参数。 <!-- comet-task:d988635e-fdcc-4b22-a7fb-e9168a07b34e -->
- [x] 5.3 实现全局异常映射：`31003` / `31004` / `31402` / `31403` / `31405` / `409 invalid_transition`（含 `currentState` / `attempted` / `allowed[]`）。验证：每个错误码至少一个用例断言响应体字段。 <!-- comet-task:21a5d644-a6a2-4ce9-a43d-a38c2e938e5f -->
- [x] 5.4 接入能力开关 `bone.procurement.transaction.enabled`（默认 false），关闭时不注册端点。验证：关闭态请求返回未找到；开启态端点可达。 <!-- comet-task:3656e201-0c0f-446c-8093-b70a57fb9094 -->

## 6. 前端：新建采购子应用与交易控制台

- [x] 6.1 新建 `bone-frontend/apps/bone-procurement-app` 骨架（10 文件：package.json / vite.config.ts / index.html / tsconfig.json / tsconfig.node.json / src/main.tsx / src/qiankun-entry.ts / src/App.tsx / src/components/Layout.tsx / src/services/api.ts），端口 3014、qiankun 名与 activeRule `/procurement`，入口照抄 `bone-iam-app` 的 lifecycle 单对象范式。验证：`npm run typecheck --workspace=bone-procurement-app` RC=0，`npm run build --workspace=bone-procurement-app` 成功。 <!-- comet-task:d9eb0918-0c60-4e30-b344-64e70522fcc5 -->
- [x] 6.2 shell 注册 4 处（`bone-shell/src/App.tsx` 的 microApps / MICRO_APP_ROUTE_PREFIXES / MICRO_APP_BASES / `<Route path="/procurement/*">`）+ 硬编码清单 6 处（`bone-frontend/package.json` build:apps、`dev-frontend-matrix.sh`、`up-all-frontend-e2e.sh`、`restart-all-apps.sh` 含循环上界、`check-menu-route-consistency.py`、`micro-app-matrix-probe.mjs`）+ 运行时拓扑图。验证：菜单可达性探针（起 shell + 新 app，登录后点 `/procurement#/transactions` 不白屏）；G32 菜单路由一致性门禁通过。 <!-- comet-task:a7759d95-ba8c-41e2-a3c1-0b7e1cbb9838 -->
- [x] 6.3 新增 2 个 migration SQL：权限种子（`procurement:transaction:read/write`、`procurement:requisition:read/write`、`procurement:purchase-order:read/write` 绑超管）+ 菜单种子（「采购」目录 + `/procurement#/transactions`、`/procurement#/suppliers` 两叶子，`ShoppingOutlined`）。验证：G40 权限码校验通过，图标白名单门禁通过，SQL 幂等重复执行无重复行。 <!-- comet-task:c3c989ab-32a4-4b89-822b-234326d5d2c0 -->
- [x] 6.4 迁入供应商页 7 文件（masterdata-app → procurement-app），解耦 `SupplierManagement.tsx:18` 的 `useMessage`（新 App 提供同构 `MessageContext`）；新增 `types/purchase.ts`、`services/purchaseApi.ts`（7 端点 + `normalizeTotal`）、`pages/purchaseView.ts`（纯逻辑）、`pages/PurchaseConsole.tsx` + 组件测试。验证：组件测试覆盖「建单失败保留弹窗与表单值」「迁移失败不本地推进状态」「404 降级空态」「403 只读」；供应商页原有测试随迁全绿。 <!-- comet-task:09377c3d-4ce8-49e7-8588-10f28369fda7 -->
- [x] 6.5 前端门禁全绿：全 workspace `tsc --noEmit` RC=0；`eslint -f json` 的 errorCount=0（解析 JSON 不看退出码）；vitest 全绿（含新 app 与迁入的供应商用例）。 <!-- comet-task:5962fbdf-3441-49c1-b13a-e5ac119bcd7c -->

## 7. 门禁与交付验证

- [x] 7.1 后端模块 `clean test` 全绿，含 ArchUnit 分层规则（adapter → application → domain ← infrastructure）。验证：`mvn -o -f bone-apps/bone-procurement/pom.xml clean test` 输出 0 failures / 0 errors。 <!-- comet-task:9a5d400e-763e-48ee-8a5f-0de8685dfd21 -->
- [x] 7.2 负向探针注入必红、还原必绿：至少覆盖 Vendor 未开通（M4-1）、金额不一致（M4-5）、终态回写（M4-6）。验证：临时移除对应守卫后测试变红，还原后变绿并记录两次结果。 <!-- comet-task:59c2954e-a41b-4a0a-8472-84f0fc8f3c62 -->
- [x] 7.3 前后端契约对齐复核：7 个端点（2 PR + 5 PO 含列表分页）的 path / method / 请求字段 / 响应字段逐项比对。验证：差异清单为空，或将差异如实写入验证报告。 <!-- comet-task:dab82009-c544-4009-a9ec-f66eb4ff0ce6 -->

## Workflow follow-up

- Verify 阶段：在 `docs/superpowers/reports/` 写验证报告，如实标注条件价格引擎、预算占用、GR 收货不在本期范围，以及供应商模块命名偏差。
- Archive 阶段：只提交本 change 相关文件，排除工作区中无关的历史报告改动。

```

## openspec/changes/procurement-transaction-pr-po-slice/.openspec.yaml

- Source: openspec/changes/procurement-transaction-pr-po-slice/.openspec.yaml
- Lines: 1-2
- SHA256: db997d460a2d6d123d14faf903215b90f63faf4e7e2dadacfdab82c07857e113

```md
schema: spec-driven
created: 2026-11-16

```

## openspec/changes/procurement-transaction-pr-po-slice/specs/procurement/purchase-order/spec.md

- Source: openspec/changes/procurement-transaction-pr-po-slice/specs/procurement/purchase-order/spec.md
- Lines: 1-159
- SHA256: 29238c40069c843e8802d4eeedc77b62e95964c4a16dca230812b25bba6eb339

[TRUNCATED]

```md
# Spec Delta

## Purpose

定义采购订单从创建到下达的行为契约，覆盖供应商交易资格闸门、法人实体隔离、订单金额一致性、下达时价格快照冻结与不可变、终态保护与非法迁移拒绝，使下游履约与财务能够据此判断一张订单是否具备被执行的前提。

## ADDED Requirements

### Requirement: 采购订单创建与供应商交易资格闸门

系统必须在创建采购订单时校验供应商是否已开通交易与付款资格。未开通交易资格的供应商不得用于下单，校验不通过时必须拒绝建单且不产生订单记录。

#### Scenario: 已开通交易资格的供应商可以下单

- **WHEN** 使用交易状态为 `ACTIVE_VENDOR` 的供应商创建采购订单
- **THEN** 系统创建订单并返回订单号与初始状态 `DRAFT`

#### Scenario: 待开通供应商被拒绝

- **WHEN** 使用交易状态为 `PENDING_VENDOR` 的供应商创建采购订单
- **THEN** 系统拒绝建单并返回错误码 `31003`，不产生订单记录

#### Scenario: 付款冻结的供应商被拒绝

- **WHEN** 使用交易状态为 `PAYMENT_BLOCKED` 的供应商创建采购订单
- **THEN** 系统拒绝建单并返回错误码 `31003`，错误信息说明该供应商当前不具备交易资格

#### Scenario: 已停用的供应商被拒绝

- **WHEN** 使用交易状态为 `INACTIVE_VENDOR` 的供应商创建采购订单
- **THEN** 系统拒绝建单并返回错误码 `31003`

#### Scenario: 供应商不存在时拒绝建单

- **WHEN** 请求引用的供应商在供应商主数据中不存在
- **THEN** 系统拒绝建单并返回供应商不存在或未开通的明确错误，不静默创建订单

### Requirement: 法人实体隔离

采购订单必须携带法人实体标识，且该标识必须与供应商在该法人下的归属一致。跨法人下单必须被拒绝，不得以「供应商存在」为由放行。

#### Scenario: 法人实体不匹配被拒绝

- **WHEN** 创建采购订单时提交的法人实体标识与供应商归属的法人实体不一致
- **THEN** 系统拒绝建单并返回错误码 `31004`

#### Scenario: 法人实体匹配时通过

- **WHEN** 创建采购订单时提交的法人实体标识与供应商归属一致，且供应商交易资格为 `ACTIVE_VENDOR`
- **THEN** 系统通过闸门校验并创建订单

### Requirement: 订单金额与行金额一致性

采购订单总额必须等于全部明细行金额之和，该一致性必须在同一订单内强一致成立。不一致时不得创建或提交订单。

#### Scenario: 总额与行金额合计不一致被拒绝

- **WHEN** 创建或提交采购订单时，声明的订单总额与各明细行金额合计不相等
- **THEN** 系统拒绝该操作并返回错误码 `31402`，订单状态不发生变化

#### Scenario: 总额按行金额合计自动成立

- **WHEN** 创建采购订单时提供若干明细行及其单价、数量、税率
- **THEN** 系统按行计算金额并汇总为订单总额，查询返回的总额与行金额合计一致

#### Scenario: 金额精度满足四位小数

- **WHEN** 订单行的单价形如 `42.5`、数量形如 `100`、税率形如 `0.13`
- **THEN** 系统按四位小数保存行金额与订单总额，不出现浮点舍入漂移，币种随订单保存

### Requirement: 订单提交与审批

采购订单必须经提交才能进入审批。提交时必须同时满足行金额合计一致、供应商交易资格有效、法人实体匹配三项条件，任一不满足即拒绝提交。

#### Scenario: 草稿提交成功

- **WHEN** 对处于 `DRAFT` 状态且满足金额一致性、供应商资格、法人匹配三项条件的订单执行提交
- **THEN** 状态变为 `SUBMITTED`

#### Scenario: 审批通过后进入已批准

```

Full source: openspec/changes/procurement-transaction-pr-po-slice/specs/procurement/purchase-order/spec.md

## openspec/changes/procurement-transaction-pr-po-slice/specs/procurement/requisition/spec.md

- Source: openspec/changes/procurement-transaction-pr-po-slice/specs/procurement/requisition/spec.md
- Lines: 1-97
- SHA256: 8cc49c701bc3dc4a15b99f5499f53b29572b5dd03beea625b51b81f53b39de22

[TRUNCATED]

```md
# Spec Delta

## Purpose

定义采购请购单从创建到转为采购订单的完整行为契约，包括可提交条件、审批前置、转单一次性与终态保护，使调用方能够据此判断何时可以下单、何时必须停止。

## ADDED Requirements

### Requirement: 请购单创建

系统必须允许具备请购写权限的用户创建请购单，并为其分配全局唯一的请购单号。新建请购单初始状态为草稿，必须记录申请组织、申请人、币种与预算引用，允许携带一行或多行需求明细。

#### Scenario: 创建成功返回草稿状态

- **WHEN** 具备 `procurement:requisition:write` 权限的用户提交包含组织、币种和至少一行明细的请购请求
- **THEN** 系统创建请购单并返回请购单号与状态 `DRAFT`，同时以 2xx 响应统一的成功信封返回

#### Scenario: 缺少必要信息被拒绝

- **WHEN** 请求缺少申请组织、币种或全部明细为空
- **THEN** 系统拒绝创建，返回校验失败错误，且不产生任何请购单记录

#### Scenario: 重复提交同一幂等键只创建一次

- **WHEN** 携带相同幂等键的创建请求被重复提交
- **THEN** 系统只创建一张请购单，重复请求返回与首次一致的请购单号，不产生第二张单据

### Requirement: 请购单提交与审批

请购单必须经提交才能进入审批，审批通过后才具备转为采购订单的资格。系统必须显式记录审批结论，未取得审批结论的请购单不得被视为可转单。

#### Scenario: 草稿提交后进入待审批

- **WHEN** 用户对处于 `DRAFT` 状态的请购单执行提交
- **THEN** 状态变为 `SUBMITTED`，并返回可继续审批的标识

#### Scenario: 审批通过后进入已批准

- **WHEN** 审批人对处于 `SUBMITTED` 状态的请购单给出通过结论
- **THEN** 状态变为 `APPROVED`

#### Scenario: 审批驳回后回到可修改状态

- **WHEN** 审批人对处于 `SUBMITTED` 状态的请购单给出驳回结论
- **THEN** 状态回到 `DRAFT`，且该单据保持可编辑与可重新提交

### Requirement: 转为采购订单的前提条件

系统必须只允许已审批通过的请购单转为采购订单。该转换必须是一次性操作，转换完成后请购单进入终态，不得再次转换，也不得在转换失败时提前改变请购单状态。

#### Scenario: 未审批的请购单转单被拒绝

- **WHEN** 对处于 `DRAFT` 或 `SUBMITTED` 状态的请购单发起转采购订单
- **THEN** 系统拒绝该操作并给出「未审批通过不可转单」的明确错误，请购单状态保持不变

#### Scenario: 已批准请购单转单成功

- **WHEN** 对处于 `APPROVED` 状态的请购单发起转采购订单，且请求数据通过采购订单的建单校验
- **THEN** 系统生成采购订单并返回其单号，同时请购单状态变为 `CONVERTED`

#### Scenario: 重复转单被拒绝

- **WHEN** 对已处于 `CONVERTED` 状态的请购单再次发起转采购订单
- **THEN** 系统拒绝该操作，返回「已转单」类错误，且不生成第二张采购订单

#### Scenario: 转单失败时请购单状态不变

- **WHEN** 已批准请购单发起转采购订单，但采购订单建单校验失败（例如供应商未开通交易资格）
- **THEN** 系统返回对应的建单失败错误，请购单仍保持 `APPROVED`，可修正后再次发起

### Requirement: 请购单取消与终态保护

系统必须允许在转单之前取消请购单，取消后不可再提交或转单。已转换的请购单属于终态，不接受任何状态回写。

#### Scenario: 未转单的请购单可取消

- **WHEN** 用户对处于 `DRAFT`、`SUBMITTED` 或 `APPROVED` 状态的请购单执行取消
- **THEN** 状态变为 `CANCELLED`，后续提交与转单操作均被拒绝

#### Scenario: 已转单的请购单不可取消

```

Full source: openspec/changes/procurement-transaction-pr-po-slice/specs/procurement/requisition/spec.md

## openspec/changes/procurement-transaction-pr-po-slice/specs/procurement/transaction-console/spec.md

- Source: openspec/changes/procurement-transaction-pr-po-slice/specs/procurement/transaction-console/spec.md
- Lines: 1-131
- SHA256: 16d5ad37924d095a092c2112d96f8545f40071cc49103b6c18aac8f43c70929d

[TRUNCATED]

```md
# Spec Delta

## Purpose

定义采购交易控制台在前端呈现请购单与采购订单时的界面行为契约，涵盖列表分页归一化、建单表单失败保留、状态推进按钮的允许事件展示、后端错误的可读降级与权限降级，使界面行为可被独立验证而不依赖后端实现细节。

## ADDED Requirements

### Requirement: 列表加载与分页归一化

控制台必须以分页方式展示请购单与采购订单列表，并把后端返回的分页结构归一化为界面可直接消费的形状。后端返回的总数可能是字符串形式，界面必须正确识别为数值。

#### Scenario: 列表按页加载并展示记录

- **WHEN** 用户打开请购单或采购订单列表页
- **THEN** 界面按默认页码与页大小请求数据，并渲染返回的记录行与总条数

#### Scenario: 总数为字符串时仍正确分页

- **WHEN** 后端返回的总条数为字符串形式（如 `"37"`）
- **THEN** 界面仍能正确计算总页数并展示分页控件，不出现总数为 0 或分页失效

#### Scenario: 记录为空时展示空态

- **WHEN** 后端返回的记录数组为空
- **THEN** 界面展示空态提示，不渲染表头数据行也不报错

#### Scenario: 分页参数变化后重新请求

- **WHEN** 用户切换页码或改变页大小
- **THEN** 界面以新的分页参数重新请求并替换列表内容

### Requirement: 建单表单与失败保留

控制台必须提供请购单与采购订单的建单入口。提交失败时界面必须保留用户已填内容与弹窗，使用户可以就地修正后重试；只有成功后才关闭弹窗并清空表单。

#### Scenario: 建单成功后关闭弹窗并刷新列表

- **WHEN** 用户填写表单并提交，后端返回成功
- **THEN** 界面关闭建单弹窗、清空表单并刷新列表使新单据可见

#### Scenario: 建单失败时保留输入

- **WHEN** 用户提交建单表单但后端返回失败
- **THEN** 界面保持弹窗打开、保留已填写的字段值，并展示可读的失败原因

#### Scenario: 必填项缺失时前端阻止提交

- **WHEN** 用户未填写组织、币种或未添加任何明细行就点击提交
- **THEN** 界面提示缺失字段并阻止请求发送

### Requirement: 状态推进只展示允许事件

控制台必须以只读方式呈现当前状态允许执行的事件，仅展示后端允许的事件按钮，不提供任何绕过后端裁决的本地状态推进。执行失败时界面不得本地推进状态。

#### Scenario: 只展示当前状态允许的事件

- **WHEN** 用户查看一张处于特定状态的请购单或采购订单
- **THEN** 界面只展示该状态下允许的事件按钮，不展示迁移表未列出的事件

#### Scenario: 迁移失败不本地推进状态

- **WHEN** 用户点击某个状态推进按钮但后端返回失败
- **THEN** 界面展示失败原因，且不改变当前展示的状态、不提前显示推进后的状态

#### Scenario: 未知状态码原样展示

- **WHEN** 后端返回界面未识别的状态值
- **THEN** 界面原样展示该状态码并禁用状态推进，不猜测其含义也不隐藏

### Requirement: 后端错误的可读降级

控制台必须把后端业务错误翻译为用户可理解的中文提示，并对能力未启用与无权限两种情况给出明确的降级呈现，而不是抛出未处理异常或白屏。

#### Scenario: 供应商未开通交易资格时给出可读提示

- **WHEN** 建单被后端以 `31003` 拒绝
- **THEN** 界面提示该供应商未开通交易资格，并提示先完成供应商开通

#### Scenario: 法人实体不匹配时给出可读提示

```

Full source: openspec/changes/procurement-transaction-pr-po-slice/specs/procurement/transaction-console/spec.md
