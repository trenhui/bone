---
change: procurement-transaction-pr-po-slice
design-doc: openspec/changes/procurement-transaction-pr-po-slice/design.md
base-ref: b4634656ff4deebae3d3c562d8eff80d62c1ac46
archived-with: 2026-11-17-procurement-transaction-pr-po-slice
---

<!-- comet-task-authority: openspec/changes/procurement-transaction-pr-po-slice/tasks.md -->

# Plan — 采购交易 PR/PO 可验证切片

## 范围与约束

- 目标：交付 04 交易模块最小可验证闭环——请购单建单/审批/转单 + 采购订单建单/提交/下达/详情 + 冻结净价快照 + 前端交易控制台。
- 设计真源：`openspec/changes/procurement-transaction-pr-po-slice/design.md`（D1–D10）。
- 行为真源：三个 spec 共 19 Requirement / 58 Scenario；`tasks.md` 24 个 tracked task。
- 红线：不改 `bone-app-purchase`、不搬迁供应商模块、不写 Outbox/MQ/Saga、不引入条件价格引擎/GR/发票/付款、不改 CI 判据、不连生产库。
- 工作区：`comet/procurement-supplier-mdm-slice` worktree；`isolation=current`，只改本 change 相关文件。
- 执行配置：`build_mode=autonomous`、`tdd_mode=tdd`、`review_mode=standard`。

## 依赖顺序

```
1 骨架 → 2 DDL/持久化 → 3 领域不变量 → 4 应用服务/端口 → 5 HTTP 契约
                                                            ↓
                                     7 门禁/负向探针 ← 6 前端控制台
```

- 2–5 严格串行（领域 → 应用 → 适配）。
- 6 组（前端）在 5 组端点契约冻结后开始。
- 7 组依赖全部实现；负向探针注入必红/还原必绿为最后一道证据。

## 实施步骤

### 1. 模块骨架与构建接入

<!-- comet-task-ref:83602e67-4b34-4a5b-a2e1-22f929670a03 -->
- 1.1 建 `bone-apps/bone-procurement/pom.xml`（`packaging=pom`，modules=app/transaction），`bone-apps/pom.xml` 加 module。验证：`mvn -o -f bone-apps/pom.xml validate` reactor 含新模块。

<!-- comet-task-ref:7f3691aa-7595-45ea-98f2-925af697229a -->
- 1.2 建 `bone-procurement-transaction` 模块与 `com.bone.procurement.transaction` 四层包骨架。验证：离线 `compile` 通过。

<!-- comet-task-ref:0c68b0e0-2127-41eb-aadd-29804c87b056 -->
- 1.3 建 `bone-procurement-app`：`ProcurementApplication`（`@EnableSqlRepositories` + `@Import(MetadataAutoConfiguration, SqlRepositoryAutoConfiguration)`）、`application.yml`（`BONE_DB_*` + `BONE_PROCUREMENT_PORT`）、开关配置。验证：编译通过且 Spring 上下文可加载。

### 2. DDL 与持久化映射

<!-- comet-task-ref:4555e3e2-c66d-41f3-973b-889490c6b936 -->
- 2.1 `bone-init.sql` 追加 4 张 `t_proc_*` 表；按 design.md D7 修订后的唯一键建约束（`t_proc_po` 仅 `uk(tenant_id,po_no)`，快照表才带 `po_version`）。验证：DDL 在测试库执行无语法错误。

<!-- comet-task-ref:459c5989-61cb-42f0-be7e-a707d9fbb8af -->
- 2.2 metadata-sdk 实体映射 + 领域仓储继承 SDK `Repository`；审计列用 `java.util.Date`。验证：DDL 列 ↔ `@Column` 对账测试（任一列未映射即失败）+ 往返读写测试。

### 3. 领域模型与不变量

<!-- comet-task-ref:d62c69ad-0c47-491b-93db-b7f1a25f1ece -->
- 3.1 `Money`：scale=4/HALF_UP、跨币种抛错、逐行舍入再汇总。

<!-- comet-task-ref:5651e379-637c-4921-8f64-5634bf748459 -->
- 3.2 `Requisition` 显式迁移表，未列出即拒绝；`CANCELLED` 终态。

<!-- comet-task-ref:071a51d6-5163-4745-8534-37aa87cc7151 -->
- 3.3 `PurchaseOrder` 显式迁移表 + `po_amount = Σ line_amount` 强一致。

<!-- comet-task-ref:8fe26667-f25a-4329-97f6-26f9cbb51de4 -->
- 3.4 `PriceSnapshot` 与 `computed_hash`；只追加不可变，重复触发复用同版本。

### 4. 应用服务与端口

<!-- comet-task-ref:26b58f6c-a088-4a83-826f-56cc6393faa4 -->
- 4.1 `VendorQueryPort` + `VendorQueryPortAdapter`（注入 `SupplierApplicationService.currentState`）+ 内存测试实现。

<!-- comet-task-ref:79a9ffe1-60e6-4d28-8d90-c313ba4ff14d -->
- 4.2 `RequisitionApplicationService`：建单/提交/审批/转 PO/取消，转单失败保持 `APPROVED`。

<!-- comet-task-ref:04099025-2bda-463a-898f-20b700a1e90e -->
- 4.3 `PurchaseOrderApplicationService`：Vendor 闸门、法人隔离、金额一致、下达冻结快照、详情查询。

<!-- comet-task-ref:93c2162d-8cb3-47a9-b894-140afdb13cca -->
- 4.4 `OrderNoGenerator`：`PR/PO-{yyyy}-{6位}`，取最大值 +1，唯一键冲突重试 ≤3 次。

### 5. HTTP 端点与契约

<!-- comet-task-ref:ad2996cc-be0b-4a43-9135-79c1e62e89c9 -->
- 5.1 `RequisitionController` 2 端点（建单、转单）。

<!-- comet-task-ref:d988635e-fdcc-4b22-a7fb-e9168a07b34e -->
- 5.2 `PurchaseOrderController` 4 端点（建单、提交、下达、详情）。

<!-- comet-task-ref:21a5d644-a6a2-4ce9-a43d-a38c2e938e5f -->
- 5.3 全局异常映射：`31003`/`31004`/`31402`/`31403`/`31405` + `409 invalid_transition`（带 `currentState`/`attempted`/`allowed[]`）。

<!-- comet-task-ref:3656e201-0c0f-446c-8093-b70a57fb9094 -->
- 5.4 能力开关接入，默认 false 不注册端点。

### 6. 前端：独立子应用 + 交易控制台 + 供应商页迁入

<!-- comet-task-ref:d9eb0918-0c60-4e30-b344-64e70522fcc5 -->
- 6.1 新建 `bone-procurement-app`（3014 / `/procurement`，10 文件骨架），`createQiankunViteConfig` + iam 式 lifecycle。验证：typecheck + build 通过。

<!-- comet-task-ref:a7759d95-ba8c-41e2-a3c1-0b7e1cbb9838 -->
- 6.2 shell 注册 4 处 + 硬编码清单 6 处 + 拓扑图。验证：菜单可达性探针（点 `/procurement#/transactions` 不白屏）+ G32 门禁。

<!-- comet-task-ref:c3c989ab-32a4-4b89-822b-234326d5d2c0 -->
- 6.3 权限种子（6 码绑超管）+ 菜单种子（「采购」目录 + 2 叶子，`ShoppingOutlined`）2 个 migration SQL。验证：G40 + 图标白名单 + 幂等。

<!-- comet-task-ref:09377c3d-4ce8-49e7-8588-10f28369fda7 -->
- 6.4 迁入供应商页 7 文件 + `useMessage` 解耦；新增 `types/purchase.ts`、`services/purchaseApi.ts`、`pages/purchaseView.ts`、`pages/PurchaseConsole.tsx` + 组件测试。验证：4 类组件测试 + 供应商原测试随迁全绿。

<!-- comet-task-ref:5962fbdf-3441-49c1-b13a-e5ac119bcd7c -->
- 6.5 前端门禁全绿：全 workspace `tsc --noEmit` RC=0、`eslint -f json` errorCount=0、vitest 全绿。

### 7. 门禁与交付验证

<!-- comet-task-ref:9a5d400e-763e-48ee-8a5f-0de8685dfd21 -->
- 7.1 后端 `clean test` 全绿（含 ArchUnit 分层）。

<!-- comet-task-ref:59c2954e-a41b-4a0a-8472-84f0fc8f3c62 -->
- 7.2 负向探针注入必红/还原必绿：M4-1、M4-5、M4-6（另加 M4-2/M4-10 用例）。

<!-- comet-task-ref:dab82009-c544-4009-a9ec-f66eb4ff0ce6 -->
- 7.3 前后端 7 端点契约逐项对齐（2 PR + 5 PO 含列表分页），差异清单为空或如实写入报告。

（Verify 报告为 Verify 阶段产物，见 tasks.md 的 Workflow follow-up，不在 Build 勾选。）

## 验收方式

- TDD：每组先写失败测试（RED）再实现转绿（GREEN），保留两次结果；不得回退代码伪造 RED。
- Build 检查入口：`mvn -o -f bone-apps/bone-procurement/pom.xml clean test`；前端 `npm run typecheck --workspaces --if-present` + `vitest run` + `eslint -f json`（新 app 与迁入供应商页均纳入）。
- 独立审查（standard）：Build 收尾对最终 diff 做一次风险任务与集成审查，CRITICAL/IMPORTANT 必须闭环。

## 高风险部分与失败处理

| 风险 | 失败模式 | 处理 |
|---|---|---|
| DDL ↔ 实体列漂移 | 运行期 500 | 对账测试注入一列缺失验证必红，再补齐 |
| 金额舍入漂移 | `po_amount ≠ Σ line` | 逐行舍入后求和的断言 + M4-5 负向探针 |
| 状态跳步 | `DRAFT→SENT` 通过 | 显式迁移表 + 注入必红探针 |
| 跨租户/跨法人 | 数据越权 | `tenant_id`/`legalEntityId` 双比对，M4-2 负向探针 |
| 前端菜单可见但白屏 | shell 4 处注册漏一处 | `MICRO_APP_ROUTE_PREFIXES` / `MICRO_APP_BASES` / microApps / 容器 Route 逐项核对 + 真实浏览器探针 |
| 前端假绿 | eslint formatter 崩溃退出 0 | 强制 `-f json` 解析 errorCount |
| 供应商页迁移回归 | `useMessage` 跨应用耦合漏解 | 新 App 提供同构 `MessageContext` + 原组件测试随迁全绿 |

## 回退

新增模块、4 张表、新前端子应用、页面与菜单种子均为纯新增；能力开关默认 false。回退时置开关 false 即停止暴露端点，删除新增模块/表/app/菜单种子并将供应商页移回 masterdata-app 即可完全还原；不改既有表与既有端点。
