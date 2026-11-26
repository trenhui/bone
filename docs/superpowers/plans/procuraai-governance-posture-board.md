---
archived-with: 2026-11-26-procuraai-governance-posture-board
status: final
---
# procuraAI 治理态势聚合看板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps are numbered prose; task tracking lives only in tasks.md.

**Goal:** 把 procuraAI 已就绪的只读治理信号（能力裁决/漂移/监督/全域总闸/就绪度）聚合为单一可复核看板，fail-closed 渲染，不改变人工终决红线。

**Architecture:** 后端在 ai-governance 模块内新增只读聚合端点 `/posture`（领域确定性汇总 + 应用聚合既有 service），readiness/kill switch 由前端从已有端点补取以避免反向依赖；前端 `GovernanceStatus.tsx` 消费聚合端点与 readiness 端点统一渲染。

**Tech Stack:** Java 21 / Spring Boot 3.5 / Maven（-o 离线，模块 pom 直接构建）；React 18 + TypeScript + Vite + Ant Design v5 / vitest。

**Spec:** `openspec/changes/procuraai-governance-posture-board/specs/procurement/ai-governance-posture/spec.md`；Design：`openspec/changes/procuraai-governance-posture-board/design.md`

<!-- comet-task-authority: openspec/changes/procuraai-governance-posture-board/tasks.md -->

## Global Constraints

- 只读：聚合端点 MUST NOT 写追溯链、改能力状态、落库；除 `read` 权限外无写操作。
- fail-closed：任一必填分项不可判定或缺失 → 汇总 `UNDETERMINED`；阈值只取 `AiGovernanceProperties`，不接受客户端参数。
- 敏感信息：视图与前端只含 code/state/判词文本/计数/原因码，MUST NOT 回传 reason 原文、AI output、PII；`modelHealthClaim` 恒为 `NONE`。
- 权限与开关：端点 `hasAuthority('procurement:ai-governance:read')` + `@ConditionalOnAiGovernanceEnabled`，开关关闭时端点不注册。
- 既有判词语义：`DriftVerdict` / `OversightVerdict` 三态 `UNDETERMINED` / `NO_ACTIONABLE_SIGNAL` / `REVIEW_REQUIRED`，仅 `REVIEW_REQUIRED.isActionable()==true`；不新建重复判词。
- 构建：Maven 不在 PATH，用 `mvn -o -f <module>/pom.xml`；前端 eslint 必须 `-f json` 解析 errorCount（默认 stylish formatter 假绿）。
- 不改 DDL、不改评测集下界、不改 CI 三入口判据集合、不改任何既有主 spec。

## Review Focus

- 空能力清单：聚合端点返回 `ALL_CLEAR` 还是 `UNDETERMINED`？——本设计取 `ALL_CLEAR`（无可疑能力且分项可判定），由 Task 1.2 测试固定。
- 阈值未配置：drift/oversight 分项为 `UNDETERMINED`，汇总 MUST 为 `UNDETERMINED`，不得因计数为 0 显示通过——Task 2.2 测试。
- 客户端注入阈值/判词参数：端点无参数，注入被忽略——Task 3.1 契约测试。
- 未知判词码：前端 MUST 显示原始码，不压成「正常」——Task 4.1 视图映射测试。
- 取数失败：前端 MUST 显示「无法确认」，不默认通过——Task 4.2 页面测试。
- 披露误读：disclosure 为 per-review，看板 MUST 仅给入口、不虚构全局状态——Task 4.2 页面测试。

---

### Task 1: PostureVerdict 枚举（task 06ce3fbc）

**Files:**
- Create: `bone-apps/bone-procurement/bone-procurement-ai-governance/src/main/java/com/bone/procurement/governance/domain/model/capability/PostureVerdict.java`
- Test: `.../src/test/java/com/bone/procurement/governance/domain/model/capability/PostureVerdictTest.java`

**Interfaces:**
- Produces: `enum PostureVerdict { UNDETERMINED, ALL_CLEAR, ATTENTION_REQUIRED; boolean isActionable() }`（仅 ATTENTION_REQUIRED 为 true）

<!-- comet-task-ref:06ce3fbc-895b-4db1-a063-b49b2d6508d4 -->

**Step 1: 写失败测试** PostureVerdictTest 断言三态存在与 `isActionable()` 仅 ATTENTION_REQUIRED 为真。
**Step 2: 跑测试确认 FAIL**（枚举不存在）。
**Step 3: 实现枚举**（三态 + isActionable）。
**Step 4: 跑测试确认 PASS** `mvn -o -f bone-apps/bone-procurement/bone-procurement-ai-governance/pom.xml -Dtest=PostureVerdictTest test`。
**Step 5: commit** `feat(procurement): 新增治理态势汇总判词三态`。

### Task 2: GovernancePosturePolicy 聚合策略（task fb3342c9）

**Files:**
- Create: `.../domain/service/GovernancePosturePolicy.java`
- Test: `.../src/test/java/.../domain/service/GovernancePosturePolicyTest.java`

**Interfaces:**
- Consumes: `PostureVerdict`、`DriftVerdict`、`OversightVerdict`（均 `isActionable()`）
- Produces: `PostureVerdict aggregate(List<PostureItemVerdict> items)`；`record PostureItemVerdict(String code, boolean decisionAllowed, DriftVerdict drift, OversightVerdict oversight)`（同包或领域 model 内定义，纯数据）。规则：任一 drift/oversight 为 UNDETERMINED 或 decisionAllowed 语义缺失 → UNDETERMINED；否则任一 actionable → ATTENTION_REQUIRED；否则 ALL_CLEAR。空清单 → ALL_CLEAR。

<!-- comet-task-ref:fb3342c9-874d-4baa-ba3f-3b19da9c4a37 -->

**Step 1: 写失败测试**：不可判定优先、actionable→需关注、全清、空清单四例。
**Step 2: 跑测试确认 FAIL**。
**Step 3: 实现纯函数策略**（无 Spring 依赖、无 I/O）。
**Step 4: 跑测试确认 PASS** `-Dtest=GovernancePosturePolicyTest test`。
**Step 5: commit** `feat(procurement): 治理态势确定性汇总策略`。

### Task 3: GovernancePostureView 投影（task 66f34f85）

**Files:**
- Create: `.../application/GovernancePostureView.java`（含内嵌 `record CapabilityPostureEntry`）

**Interfaces:**
- Consumes: `CapabilityView`、`GovernanceDecision`、`DriftMetricsView`、`OversightMetricsView`、`PostureVerdict`
- Produces: `record GovernancePostureView(PostureVerdict verdict, List<CapabilityPostureEntry> capabilities, long attentionCount, long undeterminedCount, String modelHealthClaim)`；`CapabilityPostureEntry(String code, String displayName, String state, boolean decisionAllowed, String decisionReasonCode, String driftVerdict, String oversightVerdict, boolean requiresOwnerReview)`。静态工厂 `from(...)` 不含 reason 原文/PII/output。

<!-- comet-task-ref:66f34f85-506a-423a-a3b3-be1eeb4d0e06 -->

**Step 1: 写失败测试** 断言视图字段不含敏感文本（以字符串匹配 reason 原文不出现）。
**Step 2: 跑测试确认 FAIL**。
**Step 3: 实现 record 与静态工厂**。
**Step 4: 跑测试确认 PASS**。
**Step 5: commit** `feat(procurement): 治理态势只读投影视图`。

### Task 4: 应用聚合 posture()（task 156f6d62）

**Files:**
- Modify: `.../application/AiGovernanceApplicationService.java`（新增 posture 方法 + 注入 threshold 来源）
- Test: `.../src/test/java/.../application/GovernancePostureApplicationTest.java`

**Interfaces:**
- Consumes: Task 2/3 输出；既有 `listCapabilities()/decide(code)/driftSignal(code,thresholds)/oversightSignal(code,thresholds)`；`AiGovernanceProperties`
- Produces: `GovernancePostureView posture()`。对每个能力：decide→decisionAllowed/reasonCode；driftSignal/oversightSignal 用服务端阈值；组装 entry 列表交给 policy + view。阈值缺省 → `unconfigured()` → 分项 UNDETERMINED。无副作用（不写 trace）。

<!-- comet-task-ref:156f6d62-2442-4cd3-90ee-7ed1f87d85bd -->

**Step 1: 写失败测试**：缺省阈值→UNDETERMINED、配置阈值且触阈→ATTENTION_REQUIRED、全清、不写 trace（tracePort 计数不变）四例。
**Step 2: 跑测试确认 FAIL**。
**Step 3: 实现 posture()**（复用既有 service 方法，只读取阈值）。
**Step 4: 跑测试确认 PASS**。
**Step 5: commit** `feat(procurement): 治理态势应用聚合`。

### Task 5: 只读聚合端点（task 64284baa）

**Files:**
- Modify: `.../adapter/web/controller/AiGovernanceController.java`
- Test: `.../src/test/java/.../adapter/web/AiGovernanceControllerContractTest.java`（追加用例）

**Interfaces:**
- Consumes: `AiGovernanceApplicationService.posture()`
- Produces: `GET /api/v1/procurement/ai-governance/posture` → `ApiResponse<GovernancePostureView>`，无参数。

<!-- comet-task-ref:64284baa-2d48-40ab-a423-defe5fc45db9 -->

**Step 1: 写失败契约测试**：read 权限成功、无权限 403、开关关闭 404、无客户端阈值参数、响应不含敏感字段。
**Step 2: 跑测试确认 FAIL**。
**Step 3: 实现端点**（`@GetMapping("/posture")` + `@PreAuthorize` + 类级 `@ConditionalOnAiGovernanceEnabled`）。
**Step 4: 跑测试确认 PASS**。
**Step 5: commit** `feat(procurement): 治理态势只读聚合端点`。

### Task 6: 前端视图映射（task 0e6bee59）

**Files:**
- Create: `bone-frontend/apps/bone-procurement-app/src/services/governanceApi.ts`
- Create: `bone-frontend/apps/bone-procurement-app/src/types/governancePostureView.ts`
- Test: `bone-frontend/apps/bone-procurement-app/src/types/governancePostureView.test.ts`

**Interfaces:**
- Produces: `GovernancePostureDTO`（verdict/capabilities/attentionCount/undeterminedCount/modelHealthClaim）、`verdictTextOrRaw(code, dict)`、`verdictColor(code)`、`postureFetchErrorText = '无法确认'`、`governanceApi.getPosture()`（GET `/api/v1/procurement/ai-governance/posture`）。

<!-- comet-task-ref:0e6bee59-1e3a-4c8c-af9b-82611c2d8a74 -->

**Step 1: 写失败测试**：未知码 `SOME_NEW_VERDICT` 返回原始码；已知码返回中文；错误文本为「无法确认」。
**Step 2: 跑 `vitest run governancePostureView` 确认 FAIL**。
**Step 3: 实现类型与映射函数**（复用 `readinessView.ts` 范式）。
**Step 4: 跑测试确认 PASS**。
**Step 5: commit** `feat(procurement): 前端治理态势类型与 API`。

### Task 7: 前端治理态势看板（task 73fe7765）

**Files:**
- Modify: `bone-frontend/apps/bone-procurement-app/src/pages/GovernanceStatus.tsx`
- Test: `bone-frontend/apps/bone-procurement-app/src/pages/GovernanceStatus.test.tsx`

**Interfaces:**
- Consumes: Task 6 输出 + 既有 `readinessApi`。
- Produces: 看板渲染：汇总判词卡 + 能力明细表（状态/裁决/漂移/监督）+ 全域总闸 + 就绪度 + 披露入口（链接至 ContractReviewDisclosure 页）；无写操作。

<!-- comet-task-ref:73fe7765-c1c1-458f-a52d-5bd9f692d695 -->

**Step 1: 写失败页面测试**：未知码显原始串、取数失败显「无法确认」、披露仅入口无全局结论、无写按钮。
**Step 2: 跑 `vitest run GovernanceStatus` 确认 FAIL**。
**Step 3: 改造页面**（保留既有 readiness 展示、新增态势区块）。
**Step 4: 跑测试确认 PASS**。
**Step 5: commit** `feat(procurement): 治理态势看板前端呈现`。

### Task 8: 前端静态检查（task ffdc6de7）

<!-- comet-task-ref:ffdc6de7-a145-4bbb-ac72-a29065bdc587 -->

**Step 1: vitest 全绿**：`cd bone-frontend && npx vitest run`。
**Step 2: tsc 无错**：`npx tsc --noEmit -p apps/bone-procurement-app/tsconfig.json`（按工程实际脚本）。
**Step 3: eslint json 零错误**：`npx eslint apps/bone-procurement-app/src -f json > /tmp/eslint.json` 后解析 `errorCount===0`（禁用默认 stylish）。

### Task 9: 后端模块与采购聚合测试（tasks 26709502 + bcc741bb）

<!-- comet-task-ref:26709502-826c-40dc-8d09-11585f9599bc -->
<!-- comet-task-ref:bcc741bb-ca63-4f12-a9be-13f25af2cead -->

**Step 1: spotless 先 apply**：`mvn -o -f bone-apps/bone-procurement/bone-procurement-ai-governance/pom.xml spotless:apply`（validate 绑定会挡 test）。
**Step 2: ai-governance `clean test` 全绿**，记录用例计数。
**Step 3: 采购聚合与五套门禁复验**：`ProcuraCapabilityCoverageTest` + evalset/alignment/frontmatter/persistence/event-chain 五套全 PASS，记录命令与结果。

### Task 10: 内部 POC 走查（task 3e4f6d65）

<!-- comet-task-ref:3e4f6d65-41f0-4e4f-b97c-6d276e63e45c -->

**Step 1: 五场景走查**：以真实能力清单投影渲染，验证「不可判定优先 / 需关注 / 全清 / 取数失败 / 未知码」五场景符合 spec。
**Step 2: 写 POC 报告** `docs/superpowers/reports/2026-11-25-procuraai-governance-posture-board-poc.md`，逐场景记录实际输入/输出/证据（file:line 或命令输出）。
