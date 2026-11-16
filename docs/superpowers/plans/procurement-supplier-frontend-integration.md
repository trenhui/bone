---
change: procurement-supplier-frontend-integration
design-doc: openspec/changes/procurement-supplier-frontend-integration/design.md
base-ref: 190cad738638ab0540b3b0661d57ba3379de46db
---

<!-- comet-task-authority: openspec/changes/procurement-supplier-frontend-integration/tasks.md -->

# Plan — 采购供应商前端治理与真实联调

## 范围与约束

- 目标：把已交付的后端 `supplier-mdm` 5 端点变成浏览器可操作、可验证的前端能力；完成契约级与真实 HTTP 级的前后端联调。
- 设计真源：`openspec/changes/procurement-supplier-frontend-integration/design.md`（D1–D6）。
- 行为真源：`openspec/changes/procurement-supplier-frontend-integration/specs/procurement/supplier-mdm-console/spec.md`（6 Requirement / 14 Scenario）。
- 红线：不建 Maven 模块、不写 DDL、不接生产库、不改 CI 判据、不改后端领域规则；生产 adapter / DDL / 已裁决偏差留 owner。
- 工作区：`comet/procurement-supplier-mdm-slice` worktree（后端 API 的唯一落点）；不碰主仓 dev 的并行在途文件。

## 依赖顺序

1 组（类型/API）→ 2 组（页面，依赖 1）→ 3 组（测试，依赖 1+2）→ 4 组（联调验证，依赖全组）。

## 实施步骤

### 1. 前端类型与 API 客户端

<!-- comet-task-ref:debb3249-e972-4396-961f-acd981ab08bf -->
- 1.1 新增 `types/supplier.ts`：SupplierStateDTO（supplierState/vendorState/版本/历史摘要）、RegistrationResultDTO、TransitionResultDTO、SupplierPageQuery；白名单字段，禁止 PII；验证 `tsc --noEmit`。

<!-- comet-task-ref:fd8528d8-dfc5-49ae-bd7d-dba813ec46a3 -->
- 1.2 新增 `services/supplierApi.ts`：`register / transitionSupplier / transitionVendor / current / page`；路径 `/api/v1/masterdata/suppliers`；分页用 records/total 归一 + `normalizeTotal`；验证契约单测锁 5 路径与 body 形状。

### 2. 供应商治理页面

<!-- comet-task-ref:5519150c-74a3-421c-8351-e962e7641775 -->
- 2.1 `pages/SupplierManagement.tsx`：登记表单 + 双角色分区状态 + 迁移摘要时间线 + 允许事件按钮；`success=false` 按 message 呈现、不因 2xx 误判；验证渲染测试双状态独立/未知码原样。

<!-- comet-task-ref:94e47a90-dc25-4876-b639-81119e604145 -->
- 2.2 分页列表：空态、`total` 字符串归一算页数、点击行加载详情；验证 `records=[]/total="0"` 与 `total="23"` 两类无类型错误。

<!-- comet-task-ref:31b25696-e609-4bf0-a2d0-45a121fea84d -->
- 2.3 降级：404→"能力未启用"空态不渲染迁移按钮；403→无权限且不发写请求；验证渲染测试注入 404/403 断言文案与无后续写调用。

<!-- comet-task-ref:28abaae4-a763-4e17-9821-30fc0fce6152 -->
- 2.4 PII/未知码：白名单渲染、未知码原样、摘要异常显示原始值；验证注入 phone/uscc 意外字段断言 DOM 不含。

<!-- comet-task-ref:05a0adbb-c004-4df7-b4ee-c8d62cabe1ed -->
- 2.5 路由/菜单：`App.tsx` 加 `/suppliers`，`Layout.tsx` "采购治理"分组加入口；验证 tsc 通过、菜单指向 `/suppliers`。

### 3. 契约测试与前端门禁

<!-- comet-task-ref:c428e669-91f1-467a-a4f2-ce40dada42dc -->
- 3.1 `services/supplierApi.test.ts`：断言 5 方法的 method/path/body（事件码+来源声明）/响应归一；`vitest run` 全绿。

<!-- comet-task-ref:ad528e5d-5d12-4639-b1a4-164a35971602 -->
- 3.2 `pages/SupplierManagement.test.tsx`：覆盖 2.1–2.4 呈现与降级；vitest 全绿。

<!-- comet-task-ref:4f48bbec-99ea-40f7-b4d5-a2a30f7a3306 -->
- 3.3 前端门禁：worktree 内 `tsc --noEmit` + `eslint -f json`（解析 errorCount=0）+ vitest 全绿。

### 4. 真实前后端联调与验证

<!-- comet-task-ref:f253a7ec-0778-44e7-840b-2b71d27648f6 -->
- 4.1 后端真实 HTTP：`mvn -o -f bone-platform/bone-masterdata/pom.xml clean test -Dtest=SupplierHttpIntegrationTest` 全绿，记录全链路 HTTP 断言。

<!-- comet-task-ref:95a97cc7-ac74-4663-92a3-5e877eb7f837 -->
- 4.2 契约对齐：逐项比对 supplierApi 与 SupplierController 5 端点 path/method/字段，差异清单为空，写入 Verify 报告。

（Verify 报告生成为 Verify 阶段产物，见 tasks.md 的 Workflow follow-up，不在 Build 勾选。）

## 验收方式

- TDD：先写失败测试（RED）再实现转绿（GREEN）；端到端以 vitest + Testing Library + 后端 clean test 为证据。
- Build 检查入口：`mvn -o -f bone-platform/bone-masterdata/pom.xml clean test`（后端模块）；前端 `npm run typecheck --workspace=bone-masterdata-app` + vitest + eslint(json)。
- 独立审查（standard）：Build 收尾对最终 diff 做一次风险任务与集成审查，CRITICAL/IMPORTANT 必须闭环。

## 回退

纯前端新增，删除新增文件与路由/菜单即可回退；后端零改动。
