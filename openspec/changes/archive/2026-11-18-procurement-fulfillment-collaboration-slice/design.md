---
comet_change: procurement-fulfillment-collaboration-slice
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-18-procurement-fulfillment-collaboration-slice
status: final
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
