---
comet_change: procurement-transaction-pr-po-slice
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-17-procurement-transaction-pr-po-slice
status: final
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

### D4 状态机用显式迁移表，未列出即拒绝

```
Map<Status, Map<Event, Transition>>
```

`Requisition`：`DRAFT → SUBMITTED → APPROVED → CONVERTED`，`CANCELLED` 为终态（`DRAFT/SUBMITTED/APPROVED → CANCELLED`）。
`PurchaseOrder`：`DRAFT → SUBMITTED → APPROVED → SENT`，`CANCELLED` 为终态；`ACKNOWLEDGED`/`PARTIAL_RECEIVED`/`FULLY_RECEIVED`/`CLOSED` 保留在枚举内（供 05 履约推进），本期不提供推进入口。

非法迁移统一抛领域异常，由全局异常处理器映射为 `409 invalid_transition`，错误体带 `currentState` / `attempted` / `allowed[]`。

**理由**：fail-closed 迁移表把"跳步"（如 `DRAFT → SENT`）在编译期可枚举、运行期必拒绝，比散落的 `if` 判断可测。

### D5 并发与幂等

- 每个聚合带 `@Version`，状态迁移与版本 CAS 同事务；版本冲突映射为 `409`。
- 写端点接受 `Idempotency-Key`；本期以"业务唯一键 + 状态机幂等"为主：`convert` 以 PR 状态从 `APPROVED` 迁出为唯一性保证（并发只有一个成功），`send` 以"已存在同版本快照则复用"为幂等依据。

**理由**：PA-08 明确单库不引入分布式幂等组件；用唯一约束 + 状态机 CAS 已能在单库内保证一次成功。

### D6 价格快照：只追加 + 计算哈希

`t_proc_price_snapshot` 一行对应 `(tenant_id, po_no, po_version)`，含逐行净价结果 JSON、税额、币种、`fx_rate`/`fx_as_of`/`fx_source`、`rounding_mode`、`tax_code_version`、`computed_hash`、`computed_at`。`computed_hash` = SHA-256(规范化后：poNo + poVersion + 逐行 itemId/qty/netPrice/lineAmount + taxAmount + currency + fxAsOf + roundingMode + taxCodeVersion)。

**不可变策略**：插入后永不更新；重复触发计算时先查同版本是否存在，存在则直接返回，不重写。应用层不提供 update/delete 方法。

**`po_version` 的来源**：取下达时 `t_proc_po.version`（乐观锁版本号）的当前值，**不新增**独立的业务修订列。这样「首次下达 → 快照 1 份；后续修订再下达 → 版本号已变 → 允许新快照」，且与并发 CAS 共用同一版本语义。

**理由**：`computed_hash` 覆盖汇率取数与舍入口径，使"快照可复现"成为可机器验证的断言（LLD M4-8 的判据）。

**本期简化（如实记录）**：快照净价直接取订单行净价（不经过 PriceCondition 条件计算），因此 INV-07/08/09 与 M4-4/M4-8 的条件引擎部分不在本期验收范围。

### D7 DDL：4 张表 + 齐全必备列

`t_proc_requisition`、`t_proc_po`、`t_proc_po_line`、`t_proc_price_snapshot`，均含：`id`、`tenant_id`、审计四列（`created_at/updated_at/created_by/updated_by`，`DATETIME(3)` UTC）、`deleted`、`version`。金额列 `DECIMAL(19,4)` + `currency CHAR(3)`，数量 `DECIMAL(19,6)`。

唯一键：
- `t_proc_requisition`：`uk(tenant_id, pr_no)`
- `t_proc_po`：`uk(tenant_id, po_no)`（PO 主表按单号唯一，**不带** `po_version`）
- `t_proc_po_line`：`uk(tenant_id, po_id, line_no)`
- `t_proc_price_snapshot`：`uk(tenant_id, po_no, po_version)`，其中 `po_version` = 下达时刻 `t_proc_po.version` 的值，用于「同一单多次修订各留一份快照」。

索引：`idx(tenant_id, org_id, status)`、`idx(tenant_id, vendor_id, status)`。

DDL 手写入 `bone-init.sql`（本文件此前 `t_proc_` 零命中，属首次引入）。

### D8 单号生成

`PR-{yyyy}-{6 位序列}`、`PO-{yyyy}-{6 位序列}`，序列按 `(tenant_id, 类型, 年份)` 取当前最大值 +1，插入时依赖唯一约束兜底，冲突重试最多 3 次。

**理由**：可读、可排序、符合 LLD 示例格式；唯一约束保证并发下不会产生重复号。

### D9 能力开关与端点注册

`bone.procurement.transaction.enabled` 默认 `false`；关闭时两个 Controller 不注册（与供应商 `bone.procurement.supplier-mdm.enabled` 同口径），使未配置环境不暴露半成品端点。

### D10 前端落点：新建独立子应用 `bone-procurement-app`（修订 2）

**2026-11-17 修订**：原决策为「暂放 `bone-masterdata-app`」，经用户否决并要求重新设计。复核发现原决策的理由（上一轮 1 个供应商页、新建子应用无业务必要）在本期已不成立——本期新增交易控制台，后续还有 02 寻源 / 05 履约 / 06 编排的前端；且上一轮供应商页在 masterdata-app 的「采购治理」菜单（`components/Layout.tsx:58-63`）实为死代码（全仓无 `import AppLayout`），IAM 菜单种子亦无 `/suppliers`，即该页连侧边栏入口都未真正接通。继续沿用等于把两个业务域塞进一个壳且导航不通。

**决策**：新建 `bone-frontend/apps/bone-procurement-app`，qiankun 名 `bone-procurement-app`，端口 `3014`，activeRule `/procurement`；页面路由 `/procurement#/transactions`（交易控制台）与 `/procurement#/suppliers`（供应商治理，从 masterdata-app 迁入）。

**必改清单（源码实证，非估计）**：
- 新建 app 骨架 10 文件（`package.json` / `vite.config.ts` / `index.html` / `tsconfig.json` / `tsconfig.node.json` / `src/main.tsx` / `src/qiankun-entry.ts` / `src/App.tsx` / `src/components/Layout.tsx` / `src/services/api.ts`）。`vite.config.ts` 用 `createQiankunViteConfig('bone-procurement-app', 3014, {proxyTarget:'http://localhost:8888'})`（`packages/shared-config/src/index.ts:35-71`）。入口照抄 `bone-iam-app/src/main.tsx:96-133`：一份 lifecycle 对象同时供 `renderWithQiankun` 与命名导出，`setQiankunToken` 注入，unmount 时置 null。
- shell 注册 4 处（`apps/bone-shell/src/App.tsx`）：`microApps` 数组 `:663`、`MICRO_APP_ROUTE_PREFIXES` `:249`、`MICRO_APP_BASES` `:980`、`<Route path="/procurement/*" element={null} />` `:1396`。漏一处即菜单可见但白屏（claim 的事故注释在 `:258-261`、`:1403-1405`）。
- 硬编码清单 6 处 + 拓扑图：`bone-frontend/package.json:16`（`build:apps`，漏则 CI 不构建）、`scripts/dev-frontend-matrix.sh:28`、`scripts/dev/up-all-frontend-e2e.sh:28`、`bone-frontend/restart-all-apps.sh:15`（含循环上界）、`scripts/check-menu-route-consistency.py:25`、`scripts/e2e/ui/micro-app-matrix-probe.mjs:51`、`doc/design/assets/readme-runtime-topology.svg`（`check.sh:684` 运行时拓扑门禁守护）。
- 新增 2 个 migration SQL：权限种子（参照 `0059_synap_claim_permissions.sql`）播种 `procurement:transaction:read/write`、`procurement:requisition:read/write`、`procurement:purchase-order:read/write` 并绑超管；菜单种子（参照 `0064_seed_synap_claim_menus.sql:58-131`）播种「采购」顶级目录 + `/procurement#/transactions` 与 `/procurement#/suppliers` 两个叶子。权限码必须在 `iam_permission` 存在（`check.sh:603-607` G40），图标须在 shell `MENU_ICON_MAP`（`App.tsx:294-334`）白名单内，用 `ShoppingOutlined`。
- 供应商页迁入：把 `bone-masterdata-app/src/{types/supplier.ts, services/supplierApi.ts(+test), pages/supplierView.ts(+test), pages/SupplierManagement.tsx(+test)}` 共 7 文件移到新应用对应目录；`SupplierManagement.tsx:18` 的 `useMessage from '../App'` 需解耦——新应用提供同样 `MessageContext`/`useMessage`（与 `bone-masterdata-app/src/App.tsx:26-31` 同构），避免改动页面逻辑。

**前端分层契约**（不变）：`types/purchase.ts` 白名单投影、`services/purchaseApi.ts`（7 端点：2 PR + 5 PO 含列表分页 + `normalizeTotal` 分页归一）、`pages/purchaseView.ts` 纯逻辑（node 可测，只做只读投影不内置守卫裁决）、`pages/PurchaseConsole.tsx` 页面 + 组件测试。未知状态码原样展示，取数失败显示「无法确认」，金额按币种四位小数。

**理由**：采购是与主数据并列的业务域，前端必须与后端 `bone-procurement-*` 模块边界对齐；新建子应用后 shell 注册、菜单、权限、dev 脚本全走平台既有范式（claim 已验证），后续 02/05/06 前端直接在同类应用内扩展，不再产生跨域耦合。

**代价**：比原方案多约 1 组任务、新增 2 个 migration SQL、动 shell 与 6 处脚本清单；供应商页迁移触碰上一轮已归档代码（7 文件移动 + 1 处 useMessage 解耦），通过门禁全绿与组件测试保证不回归。

## Risks / Trade-offs

- **供应商模块命名不符 §4.2** → 本期不搬迁，报告中明确记为偏差；后续独立 change 偿还（搬迁 60+ 文件 + pom + ArchUnit 规则）。
- **快照不含条件引擎**，`31401`（价格有效期重叠）与 INV-09 的条件部分无法在本期验收 → 在 Verify 报告中列为偏差，不含糊宣称"价格链已完成"。
- **预算占用为占位**（只记 `budget_ref` 与金额，不做跨系统占用/释放）→ 在 proposal 与报告中标注 L19 待决，避免被误读为已实现预算管控。
- **单号序列靠"取最大值+1 + 唯一约束重试"**，高并发下可能多次重试 → 重试上限 3 次后返回可读错误，不静默失败；若后续出现热点，再引入独立序列表。
- **前端独立子应用（D10 修订 2）** → 新建 `bone-procurement-app`（3014）并迁入供应商页，需同步 shell 4 处注册 + 6 处硬编码清单 + 2 个 migration SQL；以「菜单可达性探针（点菜单不白屏）+ 前端门禁全绿 + G40 权限码校验」兜底，不接受只改路由不改 shell 的半成品。
- **供应商页迁移触碰已归档代码** → 7 文件纯移动 + 1 处 `useMessage` 解耦，原 masterdata-app 的 3 个测试随文件迁移；迁移后 masterdata-app 相关 vitest 仍需全绿，证明未回归。
- **`bone-init.sql` 首次引入 `t_proc_*`** → DDL 与 SDK 实体列必须逐列对齐（漏声明 `@Column` 会在运行期 500），以「DDL 列 ↔ 实体字段」对账测试兜底。

## Migration Plan

1. 新模块与聚合器加入 `bone-apps/pom.xml` 的 `<module>`；`bone-procurement-app` 以 autoconfiguration/jar 形态产出，不影响现有 `MetadataApplication` 与 `bone-boot`。
2. `bone-init.sql` 追加 4 张表（只新增，不改既有表）。
3. 部署时 `bone.procurement.transaction.enabled=true` 才注册端点；默认 `false`，未开启环境无行为变化。
4. 前端新建 `bone-procurement-app`（3014）并注册到 shell；菜单/权限通过 migration SQL 播种；供应商页从 masterdata-app 迁入，masterdata-app 不再含采购页面。
5. 回滚：将开关置 `false` 即停止暴露端点；新区表保留不影响既有链路，如需清理按 DDL 反向执行；前端回退为撤销 shell 注册、删除新 app 与菜单种子、把供应商页移回（本步属设计回退，非运行期开关）。

## Open Questions

- PR 单号与 PO 单号是否需要支持客户自定义前缀/规则（影响 `OrderNoGenerator` 扩展点，但不改变本期行为契约）。
- 预算系统真实对接契约（L19）确定后，`budget_ref` 是否需要拆成"占用/释放"两阶段事件。
