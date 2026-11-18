# Comet Design Handoff

- Change: procurement-fulfillment-collaboration-slice
- Phase: design
- Mode: compact
- Context hash: 2d9acc078aaf4e05ae6b16c1322a29e503494790c8bbb5fad40e6692e423c291

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procurement-fulfillment-collaboration-slice/proposal.md

- Source: openspec/changes/procurement-fulfillment-collaboration-slice/proposal.md
- Lines: 1-59
- SHA256: 60c50172c62db24b58d65113b1c8c5ed7113b7ced62967613980f05926eaac87

```md
# Proposal：采购履约核心协同闭环切片

## Why

采购域已交付供应商主数据（01）、交易 PR+PO（04），平台能判定「供应商是否合格」并完成「下单」，但 PO 下达之后的协同链路在代码中零实现：`bone-init.sql` 无 `t_proc_po_ack` / `t_proc_asn` / `t_proc_invoice_submission` / `t_proc_ir_exception`，仓库无 PO 确认、发货通知、自助开票、发票例外聚合，也没有 `/api/v1/procurement` 的履约端点。结果是 S2P 主链路断在「PO 已下达」之后：供应商无法确认、无法发货、无法开票，采购方拿不到收货与发票协同凭证，端到端闭环无从谈起。

现在做的理由是依赖恰好就绪：LLD 05 卷（v0.3）已给出 PO Ack / ASN / 发票提交 / IR 例外的字段级契约、状态机与负向探针，而它们的操作对象正是上一轮已交付并验证的 `PurchaseOrder`（`SENT` 状态、`po_no`+`po_version`、行净价快照）。先有 PO 才能让履约拿到真实上游，而不是靠桩数据；同时 05 卷明确「协同全量二期、门户只读一期」，本轮取其中可独立验证的协同闭环，规模与上一轮 PR+PO 切片相当，可完整交付而非留一地桩。

## What Changes

- **新增履约业务模块**（对齐总体 §4.2 模块划分与 §8.1 单应用多模块，应用范式对齐已有 `bone-procurement-transaction`）：`bone-apps/bone-procurement/bone-procurement-fulfillment`（DDD 四层：domain / application / adapter/web / infrastructure），挂到 `bone-procurement` 聚合器与 `bone-procurement-app` 装配。
- **新增 5 张表** 写入 `bone-init.sql`：`t_proc_po_ack`、`t_proc_asn`、`t_proc_asn_line`、`t_proc_invoice_submission`、`t_proc_ir_exception`；金额列 `DECIMAL(19,4)` + `currency CHAR(3)`、数量 `DECIMAL(19,6)`、时间 UTC `DATETIME(3)`，均带租户隔离与 `@Version` 乐观锁列；唯一键覆盖幂等业务键。
- **新增内部端点**（`/api/v1/procurement`，复用现有采购权限体系：`procurement:fulfillment:read/write`）：
  - `POST /purchase-orders/{no}/ack` 行级 PO 确认（855 类比，`po_no+po_version` 幂等 + CAS，未确认终态拒绝）
  - `POST /asns` 发货通知（856 类比，`asn_no` 唯一，必须引用已确认 PO 行，包装层级/SSCC 校验）
  - `POST /invoices` 自助开票（810 类比，`(tenant_id,invoice_no,vendor_id)` 去重，金额 ≤ PO 未开票余额，只提交不裁决）
  - `GET /purchase-orders/{no}/fulfillment` 履约视图（Ack/ASN/开票/例外聚合只读）
  - `GET /ir-exceptions` 发票例外列表（cursor 分页）
  - `POST /ir-exceptions/{no}/supplement` 例外补料/申诉（无放行权限）
- **新增领域模型与状态机**（显式迁移表 + fail-closed，未列出即拒绝）：
  - `PoAck`：`ack_type` 三态 FULL/PARTIAL/REJECT + 行级确认结果；`(po_no,po_version)` 幂等，已关闭 PO 不可确认
  - `ASN`：`CREATED → TRANSMITTED → RECEIVING_MATCHED → RECONCILED`，`CANCELLED` 终态；未确认 PO 直接发 ASN 拒绝（`31503`）
  - `InvoiceSubmission`：`DRAFT → SUBMITTED → MATCH_PENDING → MATCHED / BLOCKED / REJECTED`；997 语法回执仅置「语法已确认」绝不置 `MATCHED`（`31506`）
  - `IRException`：`OPEN → SUPPLEMENTED → UNDER_REVIEW → RESOLVED / REJECTED`；采购域不可自行放行（`31505`），`RESOLVED` 只由 Finance 事件回写
- **跨上下文 INBOUND 端口（fail-closed）**：定义 `InventoryAclPort`（`GoodsReceived`）/ `FinanceAclPort`（`InvoiceMatched` / `PaymentRecorded`）端口与默认拒绝桩，运行期不触发真实上游；用领域测试夹具验证「只回写、不裁决」状态机，杜绝假绿。与上一轮 `VendorQueryPort` 做法一致。
- **容差配置化**：发票金额校验的容差按供应商/品类可配置（禁止硬编码阈值），本轮提供配置端口 + 严格默认实现；TDS=ΣIT1±SAC 逐行舍入后汇总校验。
- **前端履约页**：在现有独立子应用 `bone-frontend/apps/bone-procurement-app`（3014 / activeRule `/procurement`）增加「履约协同」页：PO 确认、ASN 登记、开票提交、例外看板与补料，复刻平台既有 qiankun + `@bone/ui` 范式，不新建独立门户、不引入外部账号体系。
- **新增权限码**：`procurement:fulfillment:read`、`procurement:fulfillment:write`（含菜单种子，绑超管，通过 G40 权限码/图标白名单门禁）。

## Capabilities

### New Capabilities

- `procurement/po-acknowledgement`: PO 行级确认（855 类比）——`po_no+po_version` 幂等 CAS、FULL/PARTIAL/REJECT 三态、已关闭 PO 拒绝确认、确认后 PO 进入可发货态。
- `procurement/asn`: 发货通知（856 类比）——`asn_no` 唯一去重、必须引用已确认 PO 行、包装层级与 SSCC 校验、显式状态机与非法迁移拒绝。
- `procurement/invoice-submission`: 供应商自助开票（810 类比）——`(tenant_id,invoice_no,vendor_id)` 去重、金额 ≤ PO 未开票余额、TDS 校验、997 语法回执不得置 `MATCHED`、只提交不裁决匹配结果。
- `procurement/ir-exception`: 发票例外台账——例外码/补料/申诉/状态，状态只由 Finance 事件回写，采购域无放行接口，重复 `exception_code` 幂等。
- `procurement/fulfillment-console`: 履约协同前端行为——PO 确认/ASN 登记/开票提交/例外看板与补料，列表分页、状态推进按钮的允许事件展示、失败与权限降级。

### Modified Capabilities

（`procurement/purchase-order` 的行为不变——本轮不修改 PO 聚合状态机，仅新增以 PO 为上游的履约能力；无需 delta spec。）

## Impact

- **新增代码**：1 个 Maven 模块（`bone-procurement-fulfillment`，DDD 四层）；`bone-procurement-app` 增加该模块的组件扫描；前端 `bone-procurement-app` 新增履约协同页与 API。
- **DDL**：`bone-init.sql` 新增 5 张 `t_proc_*` 履约表（首次引入履约业务表）。
- **API**：6 个新端点与 2 个新权限码；错误码沿用 LLD 05 卷 `31501–31506` 段（ASN/GR 不符、发票号重复、PO 未确认、超未开票余额、越权放行、997 当作业务接受）。
- **菜单/权限种子**：新增 migration SQL（权限码绑超管 + 「采购 → 履约协同」菜单叶子），须通过 G40 门禁。
- **依赖**：`bone-procurement-fulfillment` 通过 `PoQueryPort` 只读依赖 `bone-procurement-transaction` 的 PO 状态与行快照，通过 `InventoryAclPort` / `FinanceAclPort` 承接跨上下文 INBOUND（默认 fail-closed，不直连对方表、不接真实上游）。
- **不改动**：存量 `bone-apps/bone-app-purchase` 适配层（按能力基线 §7.2 路径 A 保留）；已交付的 supplier-mdm、transaction 模块与前端采购子应用宿主。
- **明确非目标（本轮留待后续 change / LLD 标注二期）**：
  - 独立 `supplier-portal` 前端 + BFF + 外部用户体系（UserType.EXTERNAL_USER 仅枚举、L23 待决）
  - 真实 EDI/X12 网关对接与 997/999 实际解析（L21 待决，本轮为 REST+JSON 语义类比）
  - `GoodsReceived` / `InvoiceMatched` / `PaymentRecorded` 真实消费与 GR 只读投影、三通道对账作业（依赖 Inventory/Finance ACL 交付 F-A，未交付前端口预留、运行期不触发）
  - HMAC Webhook 验签与四类负向探针（属门户/集成边界，二期）
  - Finance 真实裁决三单匹配与放行（PA-04，采购域只提交、只回写）
  - Outbox / Inbox / MQ / Saga（单应用单库，PA-08 明确不引入）
- **已知偏差（如实记录）**：外部账号隔离与门户安全在本轮以「内部采购角色 + 供应商维度可见性过滤」近似，不作为 B2B 外部用户体系交付；跨租户/跨供应商越权仍按 403 + 零数据 fail-closed 验证。

```

## openspec/changes/procurement-fulfillment-collaboration-slice/design.md

- Source: openspec/changes/procurement-fulfillment-collaboration-slice/design.md
- Lines: 1-68
- SHA256: 28ffdc848cf401bef70f99c0e1102da0635b4d7c0fff2a7acf32dd170305bf04

```md
---
comet_change: procurement-fulfillment-collaboration-slice
role: technical-design
canonical_spec: openspec
---

# Design：采购履约核心协同闭环

## Context

上游已交付：`bone-procurement-transaction` 提供 `PurchaseOrder`（状态机 `DRAFT→SUBMITTED→APPROVED→SENT`，`SENT` 及 `ACKNOWLEDGED`/`PARTIAL_RECEIVED`/`FULLY_RECEIVED`/`CLOSED`/`CANCELLED` 保留态当前**无推进入口、fail-closed**）、行净价快照、租户/权限/终态守卫；前端独立子应用 `bone-procurement-app`（3014 / `/procurement`）已承载交易控制台与供应商治理页。

约束（见 proposal.md Why）：LLD 05 卷为 v0.3 Proposal，「协同全量二期、门户只读一期」，外部账号/真实 EDI/Inventory·Finance ACL 均未就绪（L21–L25 待决）。本轮取可独立验证的协同闭环，规模对齐上一轮 PR+PO（53 后端例 + 48 前端例 + 全仓门禁 + 浏览器 E2E 的验证强度）。

## Goals / Non-Goals

**Goals:**
- 在 PO 之后建立可核对的文档链：PO 确认（Ack）→ 发货通知（ASN）→ 自助开票（InvoiceSubmission）→ 发票例外（IRException）台账，形成端到端协同闭环。
- 复用现有 `bone-apps/bone-procurement` 应用范式与 `bone-procurement-app` 前端宿主，不新建独立门户/应用。
- 以显式状态机 + 幂等键 + 终态守卫实现 fail-closed；跨上下文 INBOUND 以端口 + 默认拒绝桩承接，杜绝假绿。
- 保持采购域「只提交、只回写、不裁决/不放行」的 Finance 边界（PA-04）。

**Non-Goals:**
- 独立 `supplier-portal` 前端/BFF、外部用户体系、真实 EDI/X12 网关与 997 解析、HMAC webhook、GR 投影真实消费、三通道对账作业、Finance 真实裁决（详见 proposal Impact「明确非目标」）。
- 不改动 `bone-procurement-transaction` 已冻结的 PO 状态机语义与快照逻辑（仅新增一条受控迁移入口，见 Decisions D1）。

## Decisions

**D1 — PO 履约状态从 fail-closed 中受控解锁 `SENT → ACKNOWLEDGED`。**
现有 `PoStatus` 对 `SENT` 及履约保留态故意不注册任何迁移以「避免提前实现 05 履约语义」。本轮 PO 确认能力需要把 `SENT` 推进到 `ACKNOWLEDGED`。做法：在 `PoEvent` 新增 `ACKNOWLEDGE`，仅为 `SENT → ACKNOWLEDGED` 新增一条迁移；`ACKNOWLEDGED` 及其余保留态（`PARTIAL_RECEIVED`/`FULLY_RECEIVED`/`CLOSED`）仍注册空迁移表，保持 fail-closed。备选：在履约模块另建独立确认态、不回写 PO——否决，因 ASN 必须以「PO 已确认」为前提，独立态会形成双真源。迁移写入同一事务并带 `@Version` CAS。

**D2 — 履约模块独立成 Maven 模块，经 `PoQueryPort` 只读依赖 transaction。**
新增 `bone-procurement-fulfillment`（DDD 四层），由 `bone-procurement-app` 统一装配（复用 `@EnableSqlRepositories` + `MetadataAutoConfiguration`，与 transaction 同构）。履约对 PO 状态/行快照的访问经 `PoQueryPort`（application service 实现只读查询），**不直连 `t_proc_po*` 表、不反向依赖 transaction 的 domain**。备选：把履约塞进 transaction 模块——否决，违反限界上下文与总体 §4.2 模块划分。

**D3 — 跨上下文 INBOUND 用「端口 + fail-closed 桩」，运行期不触发。**
定义 `InventoryAclPort`（`GoodsReceived`）、`FinanceAclPort`（`InvoiceMatched`/`PaymentRecorded`）与 `ToleranceConfigPort`。默认实现返回「不可用/拒绝」，状态机测试用领域测试夹具注入事件验证「只回写、不裁决」。所有依赖未就绪时保持待态（`MATCH_PENDING`/`TRANSMITTED`），绝不推断通过。与上一轮 `VendorQueryPortAdapter` 反射调用 + fail-closed 的做法一致。备选：直接引入 MQ/Outbox——否决（PA-08 单应用单库不引入，且上游未交付）。

**D4 — 金额/数量严格遵循 LLD §14 类型基线，容差配置化。**
金额 `DECIMAL(19,4)` + `currency CHAR(3)`、汇率 `DECIMAL(19,8)`、数量 `DECIMAL(19,6)`、时间 UTC `DATETIME(3)`，禁用 float/double；`TDS=Σ IT1±SAC` 逐行舍入后汇总。容差经 `ToleranceConfigPort` 按供应商/品类读取，禁止硬编码阈值。DDL 写完用 `ProcurementEntityDdlCoverageTest` 同构的列级对账测试守护。

**D5 — 权限与可见性：复用内部采购角色，供应商维度强制绑定。**
端点用 `@PreAuthorize` + 现有 `procurement:*` 体系，本轮新增 `procurement:fulfillment:read/write` 两个权限码。请求不得接受客户端传入的 `vendor_id` 伪造归属；服务层从上下文取供应商身份并与单据 `vendor_id` 比对，越权 `403` + 零数据（M5-1）。备选：本轮就做 B2B 外部账号体系——否决（L23 未决、独立门户属二期）。

**D6 — 前端在 `bone-procurement-app` 内新增履约页，不新建子应用。**
新增 `/transactions/fulfillment` 路由与 4 个视图，复用 `purchaseApi.ts` 同构的 `fulfillmentApi.ts`（6 端点）、`@bone/ui` 表格与 qiankun 范式；shell 注册/硬编码清单不变（仍 3014 / `/procurement`），仅加菜单叶子。备选：独立 `supplier-portal`——否决（非目标）。

**D7 — 错误码与迁移语义对齐 LLD 05 卷。**
新增 `31501–31506`（ASN/GR 不符、发票号重复、PO 未确认、超未开票余额、越权放行、997 当业务接受）；非法迁移 `409 invalid_transition`（带 current/attempted/allowed），沿用 transaction 的 `ProcurementExceptionHandler` + RFC 9457 兼容错误体范式。

## Risks / Trade-offs

- **[PO 状态机首次解锁] → Mitigation**：`SENT→ACKNOWLEDGED` 是唯一新增迁移，其余保留态仍空表 fail-closed；用 `PoStatusTest` 增补断言守住「只允许这一条新迁移」，防止误开履约保留态导致提前实现或越级。
- **[跨上下文桩被误当真实能力] → Mitigation**：端口默认 `UNKNOWN/不可用`，所有端点在依赖未就绪时保持待态并在响应中如实标注；测试用负向探针（M5-4/M5-6/M5-11）验证不推断通过。
- **[权限近似外部账号] → Mitigation**：明确登记为已知偏差，不宣称 B2B 外部用户体系；越权仍 `403`+零数据，PII 不原文回传。
- **[openapi.yaml 契约漂移] → Mitigation**：先查 `detailed-design/contracts/openapi.yaml` 是否已登记 05 卷端点；本轮内部端点若与 LLD 门户路径（`/api/v1/portal/*`）不同，须在 proposal/notes 说明「内部实现路径 vs LLD 契约路径」的偏差，不谎称契约一致，并特别检查 A10.16 黑名单避免转红。
- **[前端构建/门禁假绿] → Mitigation**：eslint 以 `-f json` 解析 errorCount（默认 stylish formatter 因 ansiRegex 崩溃且退出码 0）；typecheck + vitest 全绿；浏览器 CDP E2E 验证真实挂载与按钮降级。

## Migration Plan

1. `bone-init.sql` 追加 5 张 `t_proc_*` 履约表（幂等 DDL，带租户/版本列）。
2. 新增 migration SQL：权限码绑超管 + 「采购 → 履约协同」菜单叶子（遵循 0100 保留字别名教训，避免 `groups` 等 MySQL 8 保留字）。
3. `PoStatus` / `PoEvent` 新增受控迁移（`SENT→ACKNOWLEDGED`），其余保留态不变。
4. 新模块与新前端页随 `bone-procurement-app` 装配/路由发布；存量 supplier-mdm/transaction 代码不改动。
5. 回滚：履约表与菜单/权限种子可独立撤销；`PoStatus` 新迁移可通过移除 `ACKNOWLEDGE` 注册回退到 fail-closed；无数据迁移，无跨库副作用。

## Open Questions

- LLD `contracts/openapi.yaml` 中 05 卷端点是 `/api/v1/portal/*`（外部用户 BFF），本轮内部实现采用 `/api/v1/procurement/*`。该路径偏差在 Build 阶段以「内部实现路径、非 BFF」如实记录，不修改 LLD 契约真源；若门禁 A8/A10.9 要求机器一致，则该部分端点不声称契约覆盖，仅保证行为符合 spec。（属实现层记录，不改变 specs 行为与任务分解。）

```

## openspec/changes/procurement-fulfillment-collaboration-slice/tasks.md

- Source: openspec/changes/procurement-fulfillment-collaboration-slice/tasks.md
- Lines: 1-60
- SHA256: a999989928073a60c8ea71051ce96408a47c0043aab48d3fd1a3d5110840798e

```md
# Tasks：采购履约核心协同闭环

## 1. 模块脚手架与装配

- [ ] 1.1 新建 `bone-apps/bone-procurement/bone-procurement-fulfillment` Maven 模块（pom 依赖对齐 transaction），加入 `bone-procurement/pom.xml` 聚合器；验证 `mvn -o -f bone-procurement/pom.xml validate` 通过且模块被识别 <!-- comet-task:36071334-d5a9-4026-9c95-befb46980aa4 -->
- [ ] 1.2 `bone-procurement-app` 装配履约模块扫描与依赖；验证 `ProcurementApplication` 启动类测试（能力开关）仍绿、应用能加载到履约包 <!-- comet-task:cc774fd4-9346-4a67-a7a9-e2cfbad6933b -->
- [ ] 1.3 建立 DDD 四层包骨架（domain/application/adapter/web/infrastructure）与 `ArchitectureTest`（依赖方向、Controller `@PreAuthorize`、仓储白名单）；验证架构测试通过且注入违规转红 <!-- comet-task:4deb3a2f-d0ea-4468-a9b1-a089da703c69 -->

## 2. DDL 与领域模型

- [ ] 2.1 `bone-init.sql` 新增 5 张履约表（`t_proc_po_ack`、`t_proc_asn`、`t_proc_asn_line`、`t_proc_invoice_submission`、`t_proc_ir_exception`），金额/数量/时间遵循 LLD §14 类型基线，唯一键覆盖幂等业务键；验证建表脚本可执行且 `ProcurementEntityDdlCoverageTest` 同构列级对账通过 <!-- comet-task:2cc2eace-f0cc-4ab7-b99f-650ac7b5ddf8 -->
- [ ] 2.2 实现 `PoAck` 聚合与 ack_type 三态、行级确认建模（`(po_no,po_version)` 幂等）；验证 `PoAckTest` 覆盖幂等/终态拒绝 <!-- comet-task:7df46352-31c3-4319-9eb3-b53f2a2ed667 -->
- [ ] 2.3 实现 `ASN` 聚合（`CREATED→TRANSMITTED→RECEIVING_MATCHED→RECONCILED`，`CANCELLED` 终态）与 `AsnLine`/SSCC 包装引用校验；验证 `AsnStatusTest` 显式迁移表 + 非法迁移 fail-closed 用例通过（注入必红） <!-- comet-task:da88a7b2-546b-46a3-b4d1-e0e6107921dc -->
- [ ] 2.4 实现 `InvoiceSubmission` 聚合与状态机（`DRAFT→SUBMITTED→MATCH_PENDING→MATCHED/BLOCKED/REJECTED`），997 语法回执不越级；验证 `InvoiceSubmissionStatusTest` 覆盖 `31506` 拒绝 <!-- comet-task:77e485e9-e8d0-418c-97a7-a01f00e9972c -->
- [ ] 2.5 实现 `IRException` 聚合与状态机（`OPEN→SUPPLEMENTED→UNDER_REVIEW→RESOLVED/REJECTED`），采购域无 `RESOLVED` 入口；验证 `IrExceptionStatusTest` 覆盖 `31505` 越权拒绝 <!-- comet-task:67ff4551-34a6-41c9-abcd-91fd9c348ef5 -->
- [ ] 2.6 实现 `Money` 复用/对齐 transaction 的 Money 语义与 `DECIMAL(19,4)` 精度；验证金额/币种/D1–D6 精度用例通过 <!-- comet-task:670b0086-5d66-458d-a649-e4f28e357960 -->

## 3. PO 履约状态受控解锁

- [ ] 3.1 `PoEvent` 新增 `ACKNOWLEDGE`，`PoStatus` 仅为 `SENT→ACKNOWLEDGED` 新增一条迁移，其余履约保留态仍注册空表 fail-closed；验证 `PoStatusTest` 增补断言「仅此一条新迁移、保留态仍拒绝」 <!-- comet-task:8557b7c5-6a95-4145-84a9-56b629c42d82 -->
- [ ] 3.2 确认锚点：PO 标记为已确认后成为 ASN 可引用的上游；验证 transaction 模块既有用例全绿、无越级迁移 <!-- comet-task:2d3f3d48-cb9a-487d-b569-a450658434c2 -->

## 4. 端口与 fail-closed 桩

- [ ] 4.1 定义 `PoQueryPort`（只读 PO 状态/行/净价快照）、`InventoryAclPort`（GoodsReceived）、`FinanceAclPort`（InvoiceMatched/PaymentRecorded）、`ToleranceConfigPort`；验证端口 default 方法 fail-closed（返回不可用/UNKNOWN） <!-- comet-task:1de44504-ff99-40af-84c3-7e0c543a3d61 -->
- [ ] 4.2 `PoQueryPortAdapter` 只读实现（经 transaction application service）；验证集成测试能读到真实 PO 与行快照 <!-- comet-task:bd2d4506-392c-4593-a27d-727a291486f5 -->
- [ ] 4.3 跨上下文默认桩实现（运行期不触发真实上游）+ 领域测试夹具注入事件；验证 M5-4/M5-6/M5-11 负向探针：上游不可用时状态保持待态、不推断通过 <!-- comet-task:88cd6e79-7f27-47f5-84f3-15daf6f44af6 -->
- [ ] 4.4 `ToleranceConfigPort` 默认严格实现 + 按供应商/品类的可配置读取（禁止硬编码阈值）；验证 TDS 校验 M5-12 注入必红、还原必绿 <!-- comet-task:0bb8eae0-06ab-42ee-9cac-71c025ecfa7f -->

## 5. 应用服务与命令

- [ ] 5.1 `PoAckApplicationService.ack`：供应商身份绑定 + po_no+po_version CAS 幂等 + 已关闭 PO 拒绝 + 驱动 PO `ACKNOWLEDGED`；验证 `PoAckApplicationServiceTest` 覆盖 M5-1/INV-01/INV-02 <!-- comet-task:6c9a275e-9c6b-46a0-b4fd-b78b808c7d5b -->
- [ ] 5.2 `AsnApplicationService.create/transmit`：asn_no 唯一 + 行引用已确认 PO + 数量/包装校验 + 显式迁移；验证 `AsnApplicationServiceTest` 覆盖 M5-3（未确认拒绝）、INV-03/04、包装引用 <!-- comet-task:8a9a8454-eb0f-4185-826d-d6e87385e89d -->
- [ ] 5.3 `InvoiceSubmissionApplicationService.submit`：去重 + 未开票余额校验 + TDS 校验 + 只提交不裁决；验证 `InvoiceSubmissionApplicationServiceTest` 覆盖 M5-2/M5-5/M5-12/M5-7 <!-- comet-task:ff682fa0-1650-46ea-b021-304ec2eeb3df -->
- [ ] 5.4 `IrExceptionService` 登记/补料 + Finance 事件回写（只回写不裁决）；验证 `IrExceptionServiceTest` 覆盖 M5-6、重复码幂等、Finance 事件置位 <!-- comet-task:38992773-de03-4101-92db-1d3e7b32bf23 -->
- [ ] 5.5 履约只读聚合查询（PO Ack/ASN/开票/例外）与例外 cursor 分页列表；验证查询测试覆盖分页与跨供应商过滤零数据 <!-- comet-task:3ec32de2-24e7-4822-beef-dd7aacb3f7a1 -->

## 6. Web 契约与权限

- [ ] 6.1 6 个内部端点 Controller（`/api/v1/procurement/*`），`@PreAuthorize` + `procurement:fulfillment:read/write`，错误码 `31501–31506` + `409 invalid_transition`；验证 `ProcurementControllerContractTest` 同构扩展全绿 <!-- comet-task:4f963f5a-f61d-4a85-8dc9-c5c15a2ba2db -->
- [ ] 6.2 `ProcurementExceptionHandler` 扩展履约异常到 RFC 9457 兼容错误体；验证契约测试断言错误码与 problem+json 结构 <!-- comet-task:147ae7a1-c999-42c8-9c15-24c87fdde4e4 -->
- [ ] 6.3 新增权限码 migration SQL（绑超管）+「采购 → 履约协同」菜单叶子（避开 MySQL 保留字别名）；验证 migration 在 MySQL exit=0、G40 权限码/图标门禁通过、无残留临时表 <!-- comet-task:37784fa5-1035-4802-9539-d11bc967220b -->

## 7. 前端履约协同页

- [ ] 7.1 `fulfillmentApi.ts`（6 端点）+ `fulfillment` types；验证 `fulfillmentApi.test.ts` 覆盖请求/响应映射 <!-- comet-task:b198ac5f-f688-4985-a5ef-fc326030f829 -->
- [ ] 7.2 履约协同页 4 视图（待确认 PO / ASN 登记 / 发票提交 / 例外看板与补料）+ 路由 `/transactions/fulfillment` + 菜单；验证 `FulfillmentConsole.test.tsx` 挂载/空态/按钮按态展示 <!-- comet-task:3771a426-fa9b-423b-8a9e-91c13642ae76 -->
- [ ] 7.3 失败与权限降级（错误码可读提示、只读降级、能力未启用提示）；验证 vitest 覆盖 31504 保留表单、只读隐藏写按钮、403 无权提示 <!-- comet-task:0a9a56ed-6c70-4b3f-97e6-cb2d7aec0ada -->
- [ ] 7.4 前端 typecheck + eslint(json errorCount=0) + vitest 全绿；验证三条命令 exit=0 <!-- comet-task:b1134621-0d19-4fbb-bfc9-7d597810a07a -->

## 8. 集成验收与证据

- [ ] 8.1 后端 `mvn -o -f bone-procurement/pom.xml clean test` 全绿（transaction + fulfillment 全部用例）；验证退出码 0、用例数与基线一致 <!-- comet-task:dab49fea-4d8b-4e52-a7b4-61bb298ec32d -->
- [ ] 8.2 全仓门禁 G32/G40/G41 + procuraAI 对齐门禁未转红；验证相关门禁脚本 exit=0，A10.16 黑名单未命中 <!-- comet-task:14ce3733-592e-4a64-8bf0-725cf7070186 -->
- [ ] 8.3 真实浏览器 CDP E2E：登录 → 采购菜单 → 履约协同页 → 确认/ASN/开票/例外交互（含降级）；验证截图与 evidence JSON 落 `outputs/`，无白屏 <!-- comet-task:027977d0-b29d-45e2-89b1-3c3a8a5962f7 -->

## Workflow follow-up

- Verify 阶段完成独立复审与最终集成审查后归档 change。
- 归档后按用户授权完成 commit / push（当前 worktree 分支 `comet/procurement-fulfillment-collaboration-slice`）。

```

## openspec/changes/procurement-fulfillment-collaboration-slice/.openspec.yaml

- Source: openspec/changes/procurement-fulfillment-collaboration-slice/.openspec.yaml
- Lines: 1-2
- SHA256: b75d296d3b54880b00a14fd17257d690f593cd5d3db17201d597c19528a9f905

```md
schema: spec-driven
created: 2026-11-17

```

## openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/asn/spec.md

- Source: openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/asn/spec.md
- Lines: 1-76
- SHA256: 6b7cf7aec105e77334ed2d4a53468a795058642b1ad5bdb52f57f5c5afd704dd

```md
# procurement/asn Specification

## Purpose
定义供应商发货通知（856 语义类比）的行为契约。该能力以已确认 PO 为上游，保证 ASN 编号唯一、发货行引用有效、包装层级与 SSCC 完整，并通过显式状态机拒绝非法迁移，使收货对齐有可信凭证。

## ADDED Requirements

### Requirement: ASN 创建与唯一性（INV-03）
系统必须以 `asn_no` 作为发货通知的业务唯一键（租户内唯一）。重复编号的重传必须被幂等吸收，不产生第二条 ASN。

#### Scenario: 创建 ASN 成功
- **WHEN** 供应商提交带唯一 `asn_no`、引用已确认 PO 的发货通知
- **THEN** 系统创建 ASN，初始状态 `CREATED`，返回 ASN 号与状态

#### Scenario: 重复 asn_no 幂等
- **WHEN** 同一租户下重复提交相同 `asn_no` 的发货通知
- **THEN** 系统只保留一条 ASN，重复请求返回首次结果（重传去重，不以事件 ID 代替业务键）

### Requirement: 发货行必须引用已确认 PO 行（INV-04 / M5-3）
系统必须校验 ASN 的每一行都引用该供应商已确认 PO 的有效行。未确认的 PO、不存在或超量的 PO 行不得作为发货依据。

#### Scenario: 未确认 PO 直接发 ASN 被拒绝
- **WHEN** 供应商对尚未完成 PO 确认（或确认未通过）的订单提交 ASN
- **THEN** 系统拒绝并返回错误码 `31503`，不创建 ASN

#### Scenario: 引用不存在的 PO 行被拒绝
- **WHEN** ASN 明细引用了 PO 上不存在的行号
- **THEN** 系统拒绝 ASN 并指出无效行号，不创建 ASN

#### Scenario: 发货数量超过确认数量被拒绝
- **WHEN** ASN 某行的发货数量超过该 PO 行已确认的接受数量
- **THEN** 系统拒绝 ASN，返回数量越界错误，不创建 ASN

### Requirement: 包装层级与 SSCC 校验
系统必须校验 ASN 携带的包装层级引用（SSCC）完整且自洽；每个 `AsnLine` 的 `pack_ref` 必须指向一个存在的 SSCC 包装。

#### Scenario: pack_ref 指向不存在的 SSCC
- **WHEN** ASN 明细的 `pack_ref` 未在 ASN 的 SSCC 列表中声明
- **THEN** 系统拒绝 ASN 并返回包装引用错误

#### Scenario: 包装层级完整时通过
- **WHEN** ASN 的每条明细 `pack_ref` 均指向已声明的 SSCC
- **THEN** 系统接受 ASN 并保持初始状态 `CREATED`

### Requirement: ASN 显式状态机与非法迁移拒绝
系统必须以显式迁移表管理 ASN 状态：`CREATED → TRANSMITTED → RECEIVING_MATCHED → RECONCILED`，`CANCELLED` 为终态。迁移未列出即拒绝（fail-closed）。

#### Scenario: 合法传输迁移
- **WHEN** 状态为 `CREATED` 的 ASN 执行 `transmit`（PO 已确认、行引用有效、包装完整）
- **THEN** 状态变为 `TRANSMITTED`

#### Scenario: 跳过传输直接对账被拒绝
- **WHEN** 状态为 `CREATED` 的 ASN 尝试 `reconciled`
- **THEN** 系统返回 `invalid_transition`（含当前状态、尝试事件与允许事件），状态不变

#### Scenario: 终态 ASN 不可再迁移
- **WHEN** 状态为 `CANCELLED` 或 `RECONCILED` 的 ASN 尝试任何推进事件
- **THEN** 系统拒绝，状态不变

### Requirement: ASN 与 GR 差异进例外台账（INV-05 / M5-4）
系统必须把 ASN 数量与后续收货（GR）的差异记入例外台账，不得静默改写 ASN 数量以对齐 GR。GR 只由 Inventory 事件投影生成（本轮端口预留、运行期不触发）。

#### Scenario: ASN 与 GR 数量不符
- **WHEN** 收货投影的实收数量与该 ASN 行数量不一致
- **THEN** 系统生成差异例外记录，ASN 数量保持原值不被改写

#### Scenario: Finance/Inventory 上游不可用时保持待态
- **WHEN** `GoodsReceived` 事件未到达（`InventoryAclPort` 默认 fail-closed）
- **THEN** ASN 状态停在 `TRANSMITTED`，不推断收货、不推进到 `RECEIVING_MATCHED`

### Requirement: 跨供应商越权 ASN 零数据（M5-1）
系统必须将 ASN 与请求方供应商强制绑定，越权创建或查询返回 `403` 且零数据。

#### Scenario: 供应商 A 查供应商 B 的 ASN
- **WHEN** 供应商 A 请求查看仅属于供应商 B 的 ASN
- **THEN** 系统返回 `403` 且不返回该 ASN 字段

```

## openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/fulfillment-console/spec.md

- Source: openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/fulfillment-console/spec.md
- Lines: 1-54
- SHA256: 10617bba6f6e8e67148fa21fd847c7f094bd692a384abd49e4480ee83cd1eb3f

```md
# procurement/fulfillment-console Specification

## Purpose
定义采购履约协同前端的行为契约。该能力在现有采购子应用内提供 PO 确认、ASN 登记、发票提交、例外看板与补料的界面交互，保证状态推进按钮按领域允许事件展示、失败与权限降级可预期，不引入独立门户或外部账号体系。

## ADDED Requirements

### Requirement: 履约协同页分区展示
系统必须在采购子应用内提供履约协同页面，至少包含「待确认 PO」「ASN 登记」「发票提交」「例外看板」四个视图，数据取自后端履约端点。

#### Scenario: 履约页正常挂载
- **WHEN** 具备 `procurement:fulfillment:read` 权限的用户进入履约协同页
- **THEN** 系统渲染四个视图并从后端加载数据，无白屏、无未捕获异常

#### Scenario: 无履约数据时展示空态
- **WHEN** 后端返回空的履约列表
- **THEN** 系统展示空态提示，不报错、不显示假数据

### Requirement: 状态推进按钮按允许事件展示
系统必须仅在领域状态机允许当前操作时展示对应的确认/发货/开票/补料按钮；终态或不允许的事件必须禁用或隐藏，不得让用户触发注定失败的迁移。

#### Scenario: 已确认 PO 不再显示确认按钮
- **WHEN** 某 PO 已完成确认
- **THEN** 系统隐藏或禁用该 PO 的确认按钮，仅展示与当前态匹配的后续动作

#### Scenario: 匹配待定的发票不显示放行入口
- **WHEN** 发票处于 `MATCH_PENDING` 或 `BLOCKED`
- **THEN** 系统不展示任何「放行/裁决」入口（采购域本无此权限）

### Requirement: 提交失败与降级处理
系统必须在写操作失败时保留用户输入并展示服务端返回的错误码与可读信息；权限不足或能力未启用时降级为只读/提示，不得白屏或静默吞错。

#### Scenario: 提交被服务端拒绝
- **WHEN** 开票提交返回错误码 `31504`（超未开票余额）
- **THEN** 系统保留表单输入并展示对应的可读错误，不跳转、不清空

#### Scenario: 缺少写权限时降级只读
- **WHEN** 用户只有 `procurement:fulfillment:read` 而无 write 权限
- **THEN** 系统隐藏所有写按钮并以只读模式展示数据

#### Scenario: 后端能力未启用时提示降级
- **WHEN** 履约端点返回「能力未启用」或不可达
- **THEN** 系统展示明确的降级提示，不渲染误导性成功态

### Requirement: 列表分页与越权不渲染
系统必须以服务端游标分页渲染列表，且不得在前端伪造或回显不属于当前供应商的数据；越权请求返回 `403` 时前端展示无权提示。

#### Scenario: 游标分页加载更多
- **WHEN** 用户滚动或点击加载更多
- **THEN** 系统按 cursor 请求下一页并追加渲染，不重复、不丢行

#### Scenario: 越权访问展示无权提示
- **WHEN** 前端请求返回 `403`
- **THEN** 系统展示无权访问提示且不渲染任何越权数据

```

## openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/invoice-submission/spec.md

- Source: openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/invoice-submission/spec.md
- Lines: 1-80
- SHA256: a00fcff53ba299b98b1a0ebcd28dd090e34a1a27d86899ef0b3e2ec00e443e3d

```md
# procurement/invoice-submission Specification

## Purpose
定义供应商自助开票（810 语义类比）的行为契约。该能力以 PO 与确认/收货投影为上游，保证发票号去重、金额不超 PO 未开票余额、TDS 金额自洽，并严格区分「语法回执」与「业务接受」——采购域只提交发票、只回写匹配结果，不裁决三单匹配与放行。

## ADDED Requirements

### Requirement: 发票去重（INV-07 / M5-2）
系统必须以 `(tenant_id, invoice_no, vendor_id)` 作为发票唯一键。同一供应商重复提交相同发票号必须被去重，不产生第二条提交记录。

#### Scenario: 重复发票号被去重
- **WHEN** 同一租户、同一供应商重复提交相同 `invoice_no`
- **THEN** 系统返回错误码 `31502` 且只保留一条发票提交记录

#### Scenario: 不同供应商相同发票号允许
- **WHEN** 两个不同供应商提交相同 `invoice_no`
- **THEN** 系统视为两条独立发票并分别保存（去重键含 `vendor_id`）

### Requirement: 发票金额不超未开票余额（INV-08 / M5-5）
系统必须校验发票金额不超过所引用 PO 的未开票余额（本域投影）。超出时必须拒绝提交。

#### Scenario: 发票金额超未开票余额
- **WHEN** 提交的发票金额大于引用 PO 的未开票余额
- **THEN** 系统拒绝并返回错误码 `31504`，发票状态不变、不产生提交记录

#### Scenario: 金额在余额内可提交
- **WHEN** 提交的发票金额等于或小于引用 PO 的未开票余额
- **THEN** 系统接受并创建发票提交，初始状态 `DRAFT`

#### Scenario: 金额与币种按 Money 语义保存
- **WHEN** 发票金额形如 `4250.00`、币种 `CNY`
- **THEN** 系统以四位小数保存金额并随行保存币种，不出现浮点漂移

### Requirement: TDS 金额自洽校验（M5-12）
系统必须校验发票 TDS 等于各行金额按容差配置汇总的结果（Σ IT1 ± SAC，逐行舍入后汇总）。超出按供应商/品类配置的容差时必须拒收并生成例外单。容差必须可配置，禁止硬编码阈值。

#### Scenario: TDS 与行合计一致
- **WHEN** 发票 `TDS` 与逐行金额汇总的差额在配置容差内
- **THEN** 系统接受发票提交

#### Scenario: TDS 差额超容差被拒收
- **WHEN** 发票 `TDS` 与行合计差额超过该品类配置的容差
- **THEN** 系统拒收发票并生成例外单，不得将其置为 `MATCH_PENDING` 后放行

### Requirement: 语法回执不等于业务接受（M5-7 / 31506）
系统必须把 Finance 的语法回执（997/999 类比）仅用于标记「已收到」，绝不据此将发票置为 `MATCHED`。业务接受只由 Finance 的匹配结果事件决定。

#### Scenario: 997 语法回执到达
- **WHEN** 收到仅表示语法确认的回执事件
- **THEN** 发票状态仅从 `SUBMITTED` 推进到 `MATCH_PENDING`，不得变为 `MATCHED`

#### Scenario: 把语法回执当作业务接受被拒绝
- **WHEN** 有人试图以语法回执直接把发票置为 `MATCHED`
- **THEN** 系统拒绝并返回错误码 `31506`，发票状态保持不越级

### Requirement: 采购域只提交、只回写，不裁决匹配（PA-04 / M5-6）
系统必须保证采购域不提供发票匹配放行接口。`MATCHED` / `BLOCKED` 状态只由 Finance 的 `InvoiceMatched` 事件回写（本轮端口预留、运行期不触发）；采购域不得自行把 `BLOCKED` 改为 `MATCHED`。

#### Scenario: 采购域越权放行被拒绝
- **WHEN** 有人通过采购接口尝试把 `BLOCKED` 发票改为 `MATCHED`
- **THEN** 系统返回错误码 `31505`，发票状态保持不变

#### Scenario: Finance 匹配结果回写
- **WHEN** `FinanceAclPort` 收到 `InvoiceMatched` 事件（match_status=MATCHED/EXCEPTION/REVIEW）
- **THEN** 系统按事件结果回写发票状态，EXCEPTION/REVIEW 只生成或更新 IR 例外，不放行

#### Scenario: Finance 不可用时保持待态（M5-11）
- **WHEN** `InvoiceMatched` 事件未到达（Finance 不可用）
- **THEN** 发票保持 `MATCH_PENDING`，系统不推断匹配通过

### Requirement: 跨供应商越权开票零数据（M5-1）
系统必须将发票提交与请求方供应商强制绑定，不得接受客户端伪造 `vendor_id`。开票后状态与详情只读投影必须脱敏，不返回 PII 原文。

#### Scenario: 供应商 A 用 B 的 PO 开票
- **WHEN** 供应商 A 引用供应商 B 的 PO 提交发票
- **THEN** 系统返回 `403` 且零数据，不创建发票

#### Scenario: 发票响应不含 PII 原文
- **WHEN** 查询发票提交详情
- **THEN** 系统不返回银行账号、税号、联系人等 PII 原文（仅返回引用或脱敏值）

```

## openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/ir-exception/spec.md

- Source: openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/ir-exception/spec.md
- Lines: 1-65
- SHA256: 8f27b3581a48537ae9db6b01f549b4665a579d2f05b082dd3a19101e746dd71a

```md
# procurement/ir-exception Specification

## Purpose
定义发票例外（Invoice Reconciliation Exception）台账的行为契约。该能力承载发票匹配差异的登记、补料与申诉，严格保持「采购域不裁决、不放行」边界：状态只由 Finance 事件回写，采购方仅能补料/申诉，重复例外码幂等，差异单列台账不并入净额等式。

## ADDED Requirements

### Requirement: 例外登记与幂等（INV-11）
系统必须为发票匹配差异、TDS 不符、ASN/GR 差异生成例外记录，并以 `(tenant_id, exception_no)` 与 `(tenant_id, invoice_no, exception_code)` 保证唯一。重复同一例外的触发必须幂等，不产生重复台账行。

#### Scenario: 匹配差异生成例外
- **WHEN** 收到发票匹配结果为 `EXCEPTION`（或 TDS 校验超容差）
- **THEN** 系统生成一条 IR 例外记录，初始状态 `OPEN`

#### Scenario: 重复 exception_code 幂等
- **WHEN** 同一 `invoice_no` + `exception_code` 被重复触发
- **THEN** 系统只保留一条例外记录，重复触发返回首次结果

### Requirement: 状态只由 Finance 事件回写（INV-10 / M5-6）
系统必须以显式状态机管理 IR 例外：`OPEN → SUPPLEMENTED → UNDER_REVIEW → RESOLVED / REJECTED`。`RESOLVED` 只能由 Finance 的匹配结果事件置位，采购域不得自行放行或关闭。

#### Scenario: 补料推进到已补充
- **WHEN** 供应商对 `OPEN` 的例外提交补料
- **THEN** 状态推进到 `SUPPLEMENTED`，记录补料引用

#### Scenario: 采购域无法自行置 RESOLVED
- **WHEN** 采购角色尝试把任何状态的例外直接置为 `RESOLVED`
- **THEN** 系统拒绝并返回错误码 `31505`，状态不变

#### Scenario: Finance 事件回写解决
- **WHEN** `FinanceAclPort` 收到该例外对应的放行/解决事件
- **THEN** 系统按事件把状态置为 `RESOLVED`，记录 Finance 事件引用

### Requirement: 例外补料/申诉无放行权限
系统必须允许供应商对例外补料与申诉，但补料接口不得提供任何匹配放行、金额调整或状态跳过能力。

#### Scenario: 补料成功
- **WHEN** 供应商对本供应商的例外提交补料材料
- **THEN** 系统记录补料并保持例外处于待审状态，不改动匹配结果

#### Scenario: 跨供应商补料被拒绝
- **WHEN** 供应商 A 尝试对供应商 B 的例外补料
- **THEN** 系统返回 `403` 且零数据

### Requirement: 差异单列台账，不并入净额等式（INV-12 / M5-10）
系统必须把预付、红冲、部分付款、RTV、汇兑差异等单列例外台账，禁止并入三通道对账的净额等式。对账归因分 SUPPLY / INVOICE / PAYMENT 三通道独立成账。

#### Scenario: 差异进入独立台账
- **WHEN** 出现部分付款或红冲差异
- **THEN** 系统以独立例外记录登记差异类型与金额，不计入净已付或净应付等式

#### Scenario: 把差异并入净额等式被拒绝
- **WHEN** 有人试图把差异金额并入净额对账结果
- **THEN** 系统按台账约束拒绝该合并，差异仍单列

### Requirement: 例外列表可分页且越权零数据
系统必须提供例外的只读列表（cursor 分页 + 服务端 max_page_size），且按供应商身份过滤；大表禁止精确 `COUNT(*)`。

#### Scenario: 分页查询例外
- **WHEN** 采购方按 cursor 查询例外列表
- **THEN** 系统返回当页记录与 `has_more`，不返回全量精确计数

#### Scenario: 越权查询例外零数据
- **WHEN** 供应商查询不属于自己的例外
- **THEN** 系统返回 `403` 且不返回例外字段

```

## openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/po-acknowledgement/spec.md

- Source: openspec/changes/procurement-fulfillment-collaboration-slice/specs/procurement/po-acknowledgement/spec.md
- Lines: 1-46
- SHA256: 1947b5437c1d96ce938889055b3e634efae0aa50194f5427611a27e9554e8dfc

```md
# procurement/po-acknowledgement Specification

## Purpose
定义供应商对已下达采购订单的行级确认行为契约（855 语义类比）。该能力以 PO 为上游，保证确认动作的幂等性、版本一致性与终态保护，并把 PO 推进到「可发货」前提，使后续 ASN 有可信依据。

## ADDED Requirements

### Requirement: 行级 PO 确认
系统必须支持供应商按行对采购订单给出确认结果，取值为 `FULL`（全部接受）、`PARTIAL`（部分接受/改期）、`REJECT`（拒收），并记录每行的确认明细。确认必须针对具体的 `po_no` + `po_version`。

#### Scenario: 对已下达的 PO 做全额确认
- **WHEN** 供应商对状态为 `SENT` 的采购订单以 `ack_type=FULL` 提交确认
- **THEN** 系统记录确认并返回幂等键 `po_no+po_version` 对应的确认结果，PO 进入「已确认、可发货」态

#### Scenario: 部分确认带行级结果
- **WHEN** 供应商以 `ack_type=PARTIAL` 提交，且附带每行接受数量或改期信息
- **THEN** 系统逐行保存确认结果，未接受的行不进入后续 ASN 可引用集合

### Requirement: 确认幂等与版本一致（INV-01）
系统必须以 `po_no+po_version` 作为确认的幂等键与乐观并发依据。同一版本的重复确认不得产生第二条记录；版本落后于当前 PO 的确认必须被拒绝。

#### Scenario: 重复确认同一版本只记一条
- **WHEN** 供应商对同一 `po_no+po_version` 重复提交确认两次
- **THEN** 系统只保留一条确认记录，第二次返回首次结果，不产生重复数据

#### Scenario: 陈旧版本确认被拒绝
- **WHEN** 提交的 `po_version` 小于该 PO 当前版本
- **THEN** 系统拒绝确认并返回 `invalid_transition`（含当前状态、尝试版本与允许版本），不改写确认记录

### Requirement: 已关闭 PO 不可确认（INV-02）
系统必须拒绝对手工关闭或终态 PO 的确认。终态不可回写。

#### Scenario: 关闭态 PO 确认被拒绝
- **WHEN** 供应商对状态为 `CANCELLED`（或其他终态）的采购订单提交确认
- **THEN** 系统拒绝并返回 `invalid_transition`，PO 状态不变，不产生确认记录

### Requirement: 跨供应商越权确认零数据（M5-1）
系统必须将确认单据的供应商与请求方身份强制绑定，不得接受客户端传入的 `vendor_id` 伪造归属。越权请求必须返回 `403` 且不泄露任何订单数据。

#### Scenario: 供应商 A 确认供应商 B 的 PO
- **WHEN** 供应商 A 用供应商 B 的 `po_no` 提交确认
- **THEN** 系统返回 `403` 且不读取、不返回该 PO 的任何字段

#### Scenario: 缺失身份的确认被拒绝
- **WHEN** 请求未携带可解析的供应商身份却尝试确认
- **THEN** 系统 fail-closed 返回 `403`，不执行确认逻辑

```
