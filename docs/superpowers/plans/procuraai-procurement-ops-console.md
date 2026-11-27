---
change: procuraai-procurement-ops-console
design-doc: openspec/changes/procuraai-procurement-ops-console/design.md
base-ref: e64ad175a504b33a3ec51c2fbc8e1b4cc34f19e7
archived-with: 2026-11-26-procuraai-procurement-ops-console
---

<!-- comet-task-authority: openspec/changes/procuraai-procurement-ops-console/tasks.md -->

# 实施计划：procuraAI 采购运营工作台补齐

## 范围与约束

在 `bone-frontend/apps/bone-procurement-app` 新增寻源、准入、风险三条可操作路由与共享导航，只消费 `procuraai-durable-business-aggregates-rebaseline` 已交付的 `/api/v1/procurement/business/*` 端点及既有准入/风险端点。采用 autonomous + tdd + standard：每个客户端/映射/页面任务先写失败测试，再实现到转绿；最终由独立 reviewer 审查身份边界、失败态与 PII 白名单。不改 Java 代码、DDL、权限种子或 CI 判据集合；不执行生产库操作；不把 HTTP 2xx 当成功。权威门禁是限定 app 的 vitest、`tsc --noEmit`、`eslint -f json` 解析 errorCount。

## 1. API 客户端

### 1.1 寻源 API 客户端
<!-- comet-task-ref:4823367f-ac44-46d0-be16-732bd29dd269 -->
先写 `services/sourcingApi.test.ts`：mock `createApiClient`，断言列表/详情/授标调用的方法与路径、顶层 `success=false`（2xx）判为失败、请求 body 不含 `tenantId`/`operator`/`confirmedBy`，确认 RED；再实现 `services/sourcingApi.ts` 消费 `/api/v1/procurement/business/*`，统一返回带降级原因的结果，转绿。

### 1.2 准入与风险 API 客户端
<!-- comet-task-ref:53871774-24e3-4a15-a462-bae3e8677594 -->
先写 `services/supplierRiskApi.test.ts` 覆盖 403/404/网络失败/`success=false` 四类，确认 RED；再实现 `services/supplierRiskApi.ts` 把四类映射为明确降级原因并转绿。

## 2. 寻源工作台页面

### 2.1 寻源展示映射
<!-- comet-task-ref:70ef0207-3545-4c19-bf09-d38f54364253 -->
先写 `types/sourcingView.test.ts` 覆盖未知状态保留原始码、不可比报价带原因、AI 建议只作建议字段，确认 RED；再实现 `types/sourcingView.ts` 纯映射转绿。

### 2.2 寻源页面组件
<!-- comet-task-ref:5278db3f-5d57-4a35-8189-867941d0415d -->
先写 `pages/SourcingConsole.test.tsx` 验证冒名/匿名不显示成功、404 显示能力未启用，确认 RED；再实现 `pages/SourcingConsole.tsx` 的 RFx/投标列表、报价比较与授标操作区转绿。

## 3. 准入与风险页面

### 3.1 风险展示映射
<!-- comet-task-ref:de172cb5-60b7-468b-b2cc-d980227c8b8f -->
先写 `types/supplierRiskView.test.ts` 覆盖未知严重度保留原始码、绩效缺失显示“无法确认”、PII 字段白名单剔除，确认 RED；再实现 `types/supplierRiskView.ts` 转绿。

### 3.2 准入页面组件
<!-- comet-task-ref:839249e8-3161-48b5-9760-f32653e6d535 -->
先写 `pages/SupplierOnboarding.test.tsx` 验证前端不发送身份字段、过期证照不呈现复核通过，确认 RED；再实现 `pages/SupplierOnboarding.tsx` 转绿。

### 3.3 风险页面组件
<!-- comet-task-ref:2947cc2e-25f8-4029-b417-112dbb3de894 -->
先写 `pages/SupplierRisk.test.tsx` 验证未知严重度转人工提示、取数失败显示“无法确认”且不显示“无风险”，确认 RED；再实现 `pages/SupplierRisk.tsx` 转绿。

## 4. 导航与集成验收

### 4.1 共享导航组件
<!-- comet-task-ref:73a5d41d-ef63-4f8c-96f7-2d56fc7ec2bd -->
先写 `components/ProcurementWorkbenchNav.test.tsx` 验证 403/404 显示降级提示，确认 RED；再实现共享导航组件串接 `/contract-hitl`、`/human-review`、`/ai-readiness`、`/governance` 转绿，不缓存既有页面判词。

### 4.2 路由注册
<!-- comet-task-ref:3d301afd-bb3b-42ab-8f0f-7e296e3115c0 -->
在 `App.tsx` 注册 `/sourcing`、`/supplier-onboarding`、`/supplier-risk` 并保留既有路由；路由渲染测试验证三条新路由可达且不破坏旧路由。

### 4.3 权威门禁三件套
<!-- comet-task-ref:0fe2440d-8cdf-43e5-bf61-b182ef102af5 -->
运行 `npx vitest run --root . apps/bone-procurement-app`、`npx tsc --noEmit`、`npx eslint -f json`（解析 `errorCount`），三项全绿并记录到验证报告；测试进程用后台任务避免前台超时 SIGTERM。

### 4.4 独立审查
<!-- comet-task-ref:4a2ac000-62c1-40a8-923e-6bb4e6fd3001 -->
由独立 reviewer 对最终 diff 审查身份边界（请求不带自报身份）、失败态（不把 2xx/success=false 当成功）、PII 白名单；修复全部 CRITICAL/IMPORTANT 后复测。

## 失败处理与回退

- 端点响应形状与假设不符：按实现遗漏在当前范围内修正映射层，不改后端契约、不扩需求。
- vitest 前台超时被 SIGTERM：改用后台任务并读日志确认，不用退出码 0 的假绿。
- 回滚：撤销本 change 的新增前端文件与 `App.tsx` 三条路由注册即可，后端与数据零改动。
