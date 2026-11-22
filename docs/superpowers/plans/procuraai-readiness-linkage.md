---
change: procuraai-readiness-linkage
design-doc: openspec/changes/procuraai-readiness-linkage/design.md
base-ref: 8a009e5e58313751a4e183feac79e11888c2db1d
---

<!-- comet-task-authority: openspec/changes/procuraai-readiness-linkage/tasks.md -->

# Plan — procuraAI 就绪度加固与前后端联调

## 范围与约束

- 目标：让已有 AI 骨架可达、可判定、可操作——装配一致性、Mock 降级、readiness 端点、OCR 人工复核、前端三页 + 真实前后端联调。
- 设计真源：`openspec/changes/procuraai-readiness-linkage/design.md`（D1–D6）。
- 行为真源：2 份 delta spec（procurement/ai-readiness、procurement/human-review）共 6 Requirement / 11 Scenario；tasks.md 17 个 tracked task。
- 红线：不改契约冻结/评测转正/Outbox-MQ/真模型/生产 DDL/业务聚合；不碰两个未跟踪的 synapclaim 目录；不改 `bone-init.sql` 现有表；不擅自改数据库账号或生产库。
- 工作区：当前 `dev`，isolation=current。
- 执行配置：`build_mode=autonomous`、`tdd_mode=tdd`、`review_mode=standard`。

## 依赖顺序

```
1 装配一致性 → 2 Mock 降级 → 3 readiness 端点 ─┐
                                               ├→ 6 门禁同步 → 7 真实联调与验收
4 OCR 复核(领域→服务→端点) → 5 前端三页 ────────┘
```

- 1 组先行：装配断点不修，后续端点可达性无法判定。
- 3 组依赖 2 组的 provider 事实与 1 组的治理 Bean 条件。
- 4 组独立于 1–3 组，可与后端并行推进；5 组在 3 组端点契约稳定后开始。
- 6 组依赖全部实现；7 组依赖 6 组门禁与真实服务可启动。

## 实施步骤

### 1. 装配一致性与启动探针

<!-- comet-task-ref:2ad3372a-fc4e-4a70-841e-21026e7871ef -->
- 1.1 在 `AdvisoryController` 与 `AdvisoryApplicationService` 加 `@ConditionalOnAiGovernanceEnabled`（与治理 Bean 同条件）。TDD：先写 `ApplicationContextRunner` 双态探针（默认关闭三者 Bean 均不存在、开启时各恰好一个且依赖可满足）跑红，再加注解跑绿。验证：`mvn -o -f bone-apps/bone-procurement/pom.xml test` 相关用例通过。

<!-- comet-task-ref:217e9b37-cd95-49d5-83cd-190574f4ae75 -->
- 1.2 确认 `ProcurementApplication` 默认配置可无 Bean 缺失启动；记录启动日志与 `ConditionEvaluationReport` 条件一致性。验证：启动命令成功且条件报告无 advisory/治理不匹配。

### 2. Mock 身份降级

<!-- comet-task-ref:70494619-94a0-4efc-a62d-49a13ddd2477 -->
- 2.1 `ContractReviewPortAdapter.review()` 在 `gateway.invoke()` 后、`parseSuggestion` 前识别 mock 身份（`mock:true` / provider 名含 mock），追加 `MOCK_PROVIDER_REJECTED` trace 并走 `degrade()`。TDD：先写 mock 响应单测断言 UNKNOWN + trace 原因码跑红，再实现跑绿。验证：`ContractReviewPortAdapterTest` 新增用例通过。

<!-- comet-task-ref:49e8fe71-9d26-4f1f-8271-08ee1b361396 -->
- 2.2 未配 provider 时启动日志与 readiness 显式提示"Mock/未配置真实 provider"。验证：日志/响应断言通过，不静默回退可用。

### 3. 就绪度只读端点

<!-- comet-task-ref:0003c198-edf2-4a27-acc1-8ab99a1468f6 -->
- 3.1 新增 `AiReadinessApplicationService`（只读聚合 provider 配置 / 能力注册 / 总闸 / 评测四态）与 `GET /api/v1/procurement/ai-readiness` Controller；判词 AVAILABLE/DEGRADED/UNKNOWN，未知 fail-closed，响应分离"部署门禁通过"与"授标/放款许可"。TDD：先写四类事实组合与未知 fail-closed 单测跑红，再实现跑绿。验证：测试通过、`ApiResponse` 包装一致。

<!-- comet-task-ref:32006124-4189-4b82-b3dd-d18700968d30 -->
- 3.2 Web 层测试：默认配置不返回 AVAILABLE、评测未转正不声称达标、取数失败返回 UNKNOWN、未知判词原样透传。验证：测试通过。

### 4. OCR 人工复核

<!-- comet-task-ref:8f888c67-8b45-40dd-bb26-a4e11056631e -->
- 4.1 `Qualification` 实体加幂等 `reviewByHuman(reviewer, accurate, now)`；服务层 findById → 领域方法 → save 替换 `UnsupportedOperationException`；新增 Controller 端点。TDD：先写"具名复核后读回一致"测试跑红，再实现跑绿。验证：持久化读回测试通过，方法不再抛未实现异常。

<!-- comet-task-ref:cef1c366-5df4-461c-b134-ad9ce60597e9 -->
- 4.2 负向测试：缺租户/跨租户拒绝、重复提交幂等不新增记录。验证：测试通过。

### 5. 前端页面与反假绿

<!-- comet-task-ref:911fdaf2-d458-4503-8bf2-8a96801b4fab -->
- 5.1 `bone-procurement-app` 新增 `AiReadiness` / `HumanReviewConsole` / `GovernanceStatus` 三页并挂路由，复用现有 services/useMessage。TDD：先写"未知码显示原始码、失败显示无法确认、AI 建议不渲染为已生效"组件测试跑红，再实现跑绿。验证：组件测试通过、tsc 通过。

<!-- comet-task-ref:062ccbde-0c72-4c5d-9e4f-159279ee9a4f -->
- 5.2 前端质量门禁：eslint `-f json` 解析 `errorCount=0`、vitest 用例数不低于基线且 0 失败。验证：命令输出可核验。

### 6. 门禁同步与负向探针

<!-- comet-task-ref:769f4cd2-f59f-40b2-acaf-f190b33c4887 -->
- 6.1 若新增机器判据，按 A10.17 同步 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` + 判据常量 + 注入探针；否则报告"三入口集合未变"。验证：自检探针全绿、注入必红/还原必绿。

<!-- comet-task-ref:dd41eea0-6b9d-4892-9958-2a90f95a1a08 -->
- 6.2 跑 procuraAI 主门禁与自检：`check_procura_alignment.py` / `check_procura_persistence.py` / `check_procura_evalset.py` 及 `--self-test` 全绿。验证：命令退出码 0。

### 7. 真实前后端联调与验收

<!-- comet-task-ref:bb72099c-2248-47ee-9050-4446541584ba -->
- 7.1 本地真实启动后端（内存/H2 或已确认本地 MySQL 配置）与前端 dev server；真实浏览器打开 readiness 页、在终决工作台执行采纳/驳回并刷新。验证：浏览器可访问、页面渲染判词与终决结果、截图与日志留存。

<!-- comet-task-ref:ef4c55d2-c516-4e25-a4bf-ad54481a83bb -->
- 7.2 写操作以 DB 直查或持久化读回作第二证据；断言 Mock/未配 provider 不出现 AVAILABLE。验证：落库证据存在、API 与 DB 一致。

<!-- comet-task-ref:b1dc3a0f-a022-4e1a-8eba-7fd549a3dccc -->
- 7.3 完整验证 + 独立审查（review_mode=standard 最终集成审查）+ Verify 报告 + 归档。验证：报告 PASS、无 CRITICAL/IMPORTANT。

## 失败处理与回退

- 任一组 TDD 红灯无法转绿：保留失败证据，按 debug-gate 调查根因，不用 mock 掩盖。
- 本地 DB 无法启动：优先内存/H2 test profile；仍不可行则 7 组降级为"API 层 + 读回测试"，如实记录 E2E 未完成，不伪造浏览器证据。
- A10.17 三入口不同步：先修门禁常量与探针，不绕过。
- 独立审查 CRITICAL/IMPORTANT：回到对应实现组修复后重验。
