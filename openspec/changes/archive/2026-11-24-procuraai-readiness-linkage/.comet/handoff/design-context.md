# Comet Design Handoff

- Change: procuraai-readiness-linkage
- Phase: design
- Mode: compact
- Context hash: 7b78886539e89990a9049a824d3c90c153a965692a6d8890f1a3637dd3497d74

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-readiness-linkage/proposal.md

- Source: openspec/changes/procuraai-readiness-linkage/proposal.md
- Lines: 1-38
- SHA256: d3710c5768fc1f7aa726634188db743954eb16f311b92e73c20f2e4f4cf71671

```md
# procuraAI 就绪度加固与前后端联调

## Why

上一轮 `procuraai-production-hardening` 已把 9 个端口切到 SDK 持久化并通过门禁，但采购 AI 仍是"工程骨架可达、能力不可用"：默认配置下建议态服务强依赖条件 Bean 存在装配断点；未配置 provider 时 `MockLlmClient` 回显可被误读为模型参与；没有任何只读端点如实公示"AI 参与/模型配置/评测状态/总闸"；证照 OCR 人工复核方法直接抛 `UnsupportedOperationException`；前端只有 4 条路由，没有人工终决与治理页面可操作。诊断报告 `outputs/procuraAI-未实现能力诊断与优化建议-2026-11-21.md` 给出 P0-1/P0-2/P0-6/P1-3/P1-4 五项工程侧可自主完成的缺口，且这些是"让已有代码真正可达、可判定、可操作"的前置。用户已确认本轮范围为就绪度 + 前后端联调，验收强度为真实启动 + 浏览器 + E2E。

## What Changes

- 统一 AI 相关组件的装配条件：`AdvisoryController` / `AdvisoryApplicationService` 与治理 Bean 使用同一条件开关，默认配置下不出现"控制器注册了但依赖 Bean 缺失"的启动失败；补默认配置与开启配置双态启动探针。
- 合同 AI 审查显式识别 `MockLlmClient` 身份：响应含 `mock:true` 或 provider 为 mock 时，结果降级为 `UNKNOWN` 三态并强制人工终决，不允许进入 `AVAILABLE`；启动日志与就绪度输出显式提示 provider 未配置。
- 新增只读端点 `GET /api/v1/procurement/ai-readiness`，从配置态、能力注册、总闸、评测状态四类事实推导 `AVAILABLE / DEGRADED / UNKNOWN`，并声明"部署门禁通过 ≠ 授标/放款许可"；未知判词显示原始码，不乐观映射。
- 实现 `reviewOcrByHuman`：具名复核人、持久化读回、幂等写入与拒绝重复/跨租户复核；替换 `UnsupportedOperationException`，补负向测试。
- 前端新增 AI 就绪度页、人工终决（HITL）工作台与治理状态页，复用现有请求封装与 `AiDisclosureCard` 风格；未知态显示原始码、取数失败显示"无法确认"。
- 前后端联调：本地真实启动后端与前端 dev server，用真实浏览器完成"打开就绪度页 → 触发合同审查 → 人工终决 → 刷新读回"路径，API 调用以数据库落库作为第二证据。
- 所有新增判据按 A10.17 纪律同步三入口（CI / check.sh / check-procura-ci.sh）与门禁常量，并配"注入必红 / 还原必绿"探针；前端 lint 以 `-f json` 的 `errorCount` 判定，不看 stylish 退出码。

## Capabilities

### New Capabilities

- `procurement/ai-readiness`: 采购 AI 运行期就绪度的可判定契约——配置/总闸/评测/能力注册四类事实如何推导受控判词、mock 身份如何降级、未知判词如何 fail-closed、以及"就绪 ≠ 放款许可"的边界。
- `procurement/human-review`: 采购高风险 AI 环节的人工终决可操作契约——OCR 复核与合同审查推翻的具名操作、幂等与跨租户拒绝、前端工作台可执行的确认/驳回与读回一致性。

### Modified Capabilities

无。本轮不修改任何已归档 spec 的需求（`production-persistence` 的租户隔离/幂等/审计契约保持不变）。

## Impact

- 后端：`bone-apps/bone-procurement` 下的 `bone-procurement-ai-advisory`、`bone-procurement-ai-governance`、`bone-procurement-contract`、`bone-procurement-qualification`、`bone-procurement-app`（装配与启动探针、readiness 端点、OCR 复核）。
- 前端：`bone-frontend/apps/bone-procurement-app`（新页面、路由、API 封装、组件测试）。
- 门禁：`tools/check_procura_alignment.py`（A10 判据/常量/负向探针）、`.github/workflows/ci.yml`、`scripts/check.sh`、`doc/ai-product/procuraAI/scripts/check-procura-ci.sh`。
- 不触碰：契约冻结（D-09/D-16）、评测转正 200 例与 `seededDraft` 状态（D-15/D-10）、Outbox/MQ 与真实生产 DDL（L3/D-05）、法务结论（D-08）、G1–G16 能力卡片、Award/Bid/Quotation/CAPA 业务聚合、旧族 8 份文档与 8 个受保护锚点。

## 验收边界

- 通过：默认配置启动无 Bean 缺失；mock 身份不返回 AVAILABLE；readiness 端点对四类事实给出受控判词且负面探针被判红；OCR 人工复核可持久化读回且重复/跨租户被拒；浏览器可完成端到端路径并有落库证据；三入口门禁全绿且注入探针必红。
- 不声称：真实模型效果达标、评测转正、契约冻结、生产 MQ/Outbox 闭环、真实生产库迁移。CI 绿只证明结构与 fail-closed 不变量，不等于 AI 可上线。

```

## openspec/changes/procuraai-readiness-linkage/design.md

- Source: openspec/changes/procuraai-readiness-linkage/design.md
- Lines: 1-97
- SHA256: 0f81ad367758c344f50bdec4048b0eeeed5bd600af1da205f71d8d941d0f31b0

[TRUNCATED]

```md
---
comet_change: procuraai-readiness-linkage
role: technical-design
canonical_spec: openspec
---

# Design：procuraAI 就绪度加固与前后端联调

## Context

见 `proposal.md` 的 Why。`procuraai-production-hardening` 已把仓储切到 SDK 持久化并通过门禁，但运行期仍有四处断点：

- `AdvisoryController`（`:13-21`）无条件注册并强依赖 `AdvisoryApplicationService`，后者强依赖 `AiGovernanceApplicationService` 与 `SafetyApplicationService`（`AdvisoryApplicationService.java:20-27`）；这两个治理 Bean 受 `@ConditionalOnAiGovernanceEnabled`（`matchIfMissing=false`）控制。默认配置下控制器依赖的 Bean 是否存在，取决于治理开关是否打开，存在装配不确定。
- `ContractReviewPortAdapter.review()`（`ContractReviewPortAdapter.java:79-105`）已做注入/PII/JSON 护栏，但未识别 provider 身份；未配 `bone.ai.gateway.base-url` 时网关注入 `MockLlmClient`，其 `mock:true` 回显会被 `parseSuggestion` 当作正常结论解析。
- 没有任何只读端点把配置、总闸、能力注册、评测状态拼成可判定判词。
- `QualificationApplicationService.reviewOcrByHuman`（`:35-38`）直接抛 `UnsupportedOperationException`。
- 前端 `App.tsx` 只有 `/transactions`、`/suppliers`、`/fulfillment`、`/contract-review` 四条路由（`:26-32`），无可操作的人工终决/治理页。

现有规约：`决策` 三态 + `AiAvailability`、总闸即时生效、PII 禁入、双标识（显式 + 隐式）、只读证据引用（不回传 output 原文）、人工终决；诊断报告已认定 G1–G16/F1–F4 与 D-08/D-15/D-16/D-17 属 owner 前置，本轮不碰。

## Goals / Non-Goals

**Goals:**

- 默认配置与开启配置下都能明确判定装配状态，不出现"控制器在、依赖 Bean 不在"。
- Mock 身份被识别并降级为 `UNKNOWN` + 强制人工；未配置 provider 有显式提示。
- 以只读端点公示 AI 就绪度，判词可被前端诚实渲染。
- OCR 人工复核可具名、幂等、跨租户安全地写入并读回。
- 前端有可操作的就绪度/终决/治理页，并用真实浏览器完成一次端到端联调且以落库为第二证据。

**Non-Goals:**

- 不接真实模型、不改 evalset 下界与 `seededDraft` 状态、不冻结契约。
- 不新增 Outbox/Inbox/MQ、不执行真实生产库 DDL、不改 `bone-init.sql` 中现有表结构。
- 不实现 Award/Bid/Quotation/CAPA 聚合、不新增 Gx 能力卡片、不迁旧族 8 份文档。
- 不把"部署门禁通过"等同于授标/放款许可。

## Decisions

### D1：装配一致性用"同一条件 + 启动探针"，而不是放宽条件

在 `AdvisoryController` 和 `AdvisoryApplicationService` 上与治理 Bean 使用同一个 `@ConditionalOnAiGovernanceEnabled`，使三者同生共死；同时保留"关闭配置下 /ai-advisory 端点不注册"的行为。用 `ApplicationContextRunner` 做双态探针：默认关闭时 advisory 相关 Bean 均不存在；开启时恰好各一个且依赖可满足。

- 备选 A：把治理 Bean 改为无条件装配 → 破坏"配置关闭即无 AI"的合规约束，否决。
- 备选 B：在 advisory 内用 `ObjectProvider` 空依赖兜底 → 掩盖装配错误，否决。
- 取舍：端点在关闭时不可用是预期行为；就绪度端点始终可用，用于解释为什么不可用。

### D2：Mock 身份在合同审查端口内识别，复用现有降级通道

在 `ContractReviewPortAdapter.review()` 的 `gateway.invoke()` 之后、`parseSuggestion` 之前插入身份检查：`response.model()` 或结构化标识命中 mock（例如 `mock`、`mock-llm`、response 中 `mock:true`）时，追加 `ReviewTraceEntry`（原因码 `MOCK_PROVIDER_REJECTED`）并返回现有 `degrade()`（UNKNOWN + 强制人工），不新增异常类型、不入域、不计入参与率。同时把"provider 未配置"作为独立事实输入 readiness。

- 备选：在网关层全局拒绝 mock → 影响其他依赖 Mock 做本地联调的模块，否决。
- 取舍：只在采购高风险链路拒绝，保留 Mock 在其它模块的联调价值。

### D3：readiness 端点用只读聚合，事实来源现成且彼此独立

新增 `AiReadinessApplicationService`（只读），聚合四类事实：

1. provider 配置态：`bone.ai.gateway.base-url` 是否配置、当前客户端是否 mock。
2. 能力注册态：治理能力注册表中已启用能力数。
3. 总闸态：`SafetyApplicationService.globalAllowsAi()`。
4. 评测态：读取 `ai-decision-evalset.json` 的 `status` 与 `needsOwnerReview`（只读，不改），仅用于声明"未转正不得声称效果达标"。

判词规则：任一事实未知 → `UNKNOWN`；provider 未配或总闸关闭或评测未转正 → 至少 `DEGRADED` 且绝不 `AVAILABLE`；全部通过 → `AVAILABLE`。响应带 `deploymentGatesPassed` 与 `authorizationNotice` 明确"门禁通过 ≠ 授标/放款许可"。Controller 用 `@PreAuthorize("hasAuthority('procurement:ai-readiness:read')")` 与现有权限风格一致，错误走 `ApiResponse` 统一包装；未知判词原样透传。

### D4：OCR 人工复核走"实体自我状态迁移 + 现有仓储 save"

`Qualification` 实体新增 `reviewByHuman(reviewer, accurate, now)` 领域方法（幂等：已复核则复用原结论/版本，不产生新记录），`QualificationApplicationService.reviewOcrByHuman` 改为 `findById` → 领域方法 → `repository.save`，缺租户/越权 fail-closed。新增 Controller 端点（如 `POST /api/v1/procurement/qualifications/{id}/ocr-review`）与幂等键字段，配套持久化读回测试与跨租户负向测试。

- 备选：新建独立复核表 → 本轮 DDL 冻结且无必要，否决。
- 取舍：复用 `Qualification` 现有持久化，字段落在既有记录上，避免改 `bone-init.sql`。

### D5：前端新增三页，复用现有请求封装与反假绿约定

在 `bone-procurement-app` 新增 `AiReadiness`（只读卡片，判词/事实/提示）、`HumanReviewConsole`（待决列表 + 采纳/驳回 + 读回）、`GovernanceStatus`（总闸/能力态）三页并挂路由。沿用 `useMessage`、现有 services 的 baseURL/租户头；封装修订失败显示"无法确认"、未知码显示原始码、不把 `false` 显示成达标。AI 披露信息复用现有 `ContractReviewDisclosure` 的展示约定，不重复造组件。

### D6：验收证据分层，避免"2xx 即成功"假绿

- 机器层：`mvn -o -f <module>/pom.xml test`、前端 `tsc` + `vitest` + `eslint -f json` 解析 `errorCount`、主门禁与自检探针。
- 运行层：本地真实启动 `ProcurementApplication`（H2/内存或本地 MySQL 配置）与前端 dev server；用浏览器打开 readiness 页、触发一次合同审查建议、在终决工作台执行采纳/驳回并刷新。

```

Full source: openspec/changes/procuraai-readiness-linkage/design.md

## openspec/changes/procuraai-readiness-linkage/tasks.md

- Source: openspec/changes/procuraai-readiness-linkage/tasks.md
- Lines: 1-42
- SHA256: 5852bfeb89032b993121d77968a1444c09de918e3912decf205585a317a8ebaf

```md
# Tasks

## 1. 装配一致性与启动探针

- [x] 1.1 给 `AdvisoryController` 与 `AdvisoryApplicationService` 加 `@ConditionalOnAiGovernanceEnabled`，与治理 Bean 同条件；用 `ApplicationContextRunner` 双态测试断言：默认关闭时三者 Bean 均不存在、开启时各恰好一个且依赖可满足（`mvn -o -f bone-apps/bone-procurement/pom.xml test` 相关测试通过） <!-- comet-task:2ad3372a-fc4e-4a70-841e-21026e7871ef -->
- [x] 1.2 确认 `bone-procurement-app` 默认配置下 `ProcurementApplication` 可无 Bean 缺失启动；记录启动日志与条件报告（启动命令成功、`ConditionEvaluationReport` 中 advisory/治理条件一致） <!-- comet-task:217e9b37-cd95-49d5-83cd-190574f4ae75 -->

## 2. Mock 身份降级

- [x] 2.1 在 `ContractReviewPortAdapter.review()` 的 `gateway.invoke()` 后、`parseSuggestion` 前识别 mock 身份（响应 `mock:true` / provider 名含 mock），追加 `MOCK_PROVIDER_REJECTED` trace 并走 `degrade()`；单测断言 mock 响应返回 `UNKNOWN`、不产生 `AVAILABLE`、trace 有原因码（`ContractReviewPortAdapterTest` 新增用例通过） <!-- comet-task:70494619-94a0-4efc-a62d-49a13ddd2477 -->
- [x] 2.2 未配置 provider 时在启动日志与 readiness 输出显式提示"Mock/未配置真实 provider"；负向测试断言不静默回退为可用（日志/响应断言通过） <!-- comet-task:49e8fe71-9d26-4f1f-8271-08ee1b361396 -->

## 3. 就绪度只读端点

- [x] 3.1 新增 `AiReadinessApplicationService`（只读聚合 provider 配置态、能力注册态、总闸态、评测态）与 `GET /api/v1/procurement/ai-readiness` Controller，判词 `AVAILABLE/DEGRADED/UNKNOWN`，响应含部署门禁与授标/放款许可分离说明；单测覆盖四类事实组合与未知 fail-closed（新增测试通过，`ApiResponse` 包装一致） <!-- comet-task:0003c198-edf2-4a27-acc1-8ab99a1468f6 -->
- [x] 3.2 为 readiness 端点写 Web 层测试：默认配置不返回 AVAILABLE、评测未转正不声称达标、取数失败返回 UNKNOWN、未知判词原样透传（测试通过） <!-- comet-task:32006124-4189-4b82-b3dd-d18700968d30 -->

## 4. OCR 人工复核

- [x] 4.1 在 `Qualification` 实体实现 `reviewByHuman(reviewer, accurate, now)` 幂等领域方法；`QualificationApplicationService.reviewOcrByHuman` 改为 findById → 领域方法 → save，替换 `UnsupportedOperationException`；新增Controller 端点；持久化读回测试断言结论一致（测试通过，方法不再抛未实现异常） <!-- comet-task:8f888c67-8b45-40dd-bb26-a4e11056631e -->
- [x] 4.2 为复核补负向测试：缺租户/跨租户拒绝、重复提交幂等不新增记录（测试通过） <!-- comet-task:cef1c366-5df4-461c-b134-ad9ce60597e9 -->

## 5. 前端页面与反假绿

- [x] 5.1 在 `bone-procurement-app` 新增 `AiReadiness`、`HumanReviewConsole`、`GovernanceStatus` 三页并挂路由，复用现有 services/useMessage；未知判词显示原始码、取数失败显示"无法确认"、AI 建议不渲染为已生效（组件测试断言通过，`tsc` 通过） <!-- comet-task:911fdaf2-d458-4503-8bf2-8a96801b4fab -->
- [x] 5.2 前端质量门禁：eslint 以 `-f json` 解析 `errorCount=0`、`vitest` 用例数不低于现有基线且 0 失败（命令输出可核验，不看 stylish 退出码） <!-- comet-task:062ccbde-0c72-4c5d-9e4f-159279ee9a4f -->

## 6. 门禁同步与负向探针

- [x] 6.1 若新增机器判据，按 A10.17 同步 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` + 判据常量 + 注入探针；若无需新增判据则在报告中说明"现有 A10.17 三入口集合未变"（自检探针全绿，注入必红/还原必绿） <!-- comet-task:769f4cd2-f59f-40b2-acaf-f190b33c4887 -->
- [x] 6.2 跑 procuraAI 主门禁与自检：`check_procura_alignment.py` / `check_procura_persistence.py` / `check_procura_evalset.py` 及 `--self-test` 全绿（命令实际执行且退出码 0） <!-- comet-task:dd41eea0-6b9d-4892-9958-2a90f95a1a08 -->

## 7. 真实前后端联调与验收

- [x] 7.1 本地真实启动后端（H2/内存或已确认的本地 MySQL 配置）与前端 dev server；用真实浏览器打开 readiness 页、进入人工终决页执行一次采纳/驳回并刷新；截图与日志作为证据（浏览器可访问、页面渲染判词与终决结果） <!-- comet-task:bb72099c-2248-47ee-9050-4446541584ba -->
- [x] 7.2 对写操作以数据库直查或持久化读回作第二证据，并断言 Mock/未配 provider 场景不出现 AVAILABLE（落库证据存在、API 与 DB 一致） <!-- comet-task:ef4c55d2-c516-4e25-a4bf-ad54481a83bb -->
- [x] 7.3 运行完整验证与独立审查，产出 Verify 报告并归档（报告 PASS，无 CRITICAL/IMPORTANT，归档完成） <!-- comet-task:b1dc3a0f-a022-4e1a-8eba-7fd549a3dccc -->

## Workflow follow-up

- 归档后按用户授权决定是否提交与推送；不触碰两个未跟踪的 synapclaim 目录。
- 遗留 owner 事项（法务结论、评测转正、契约冻结、真模型、Outbox/MQ）在报告中列明，不伪造完成。

```

## openspec/changes/procuraai-readiness-linkage/.openspec.yaml

- Source: openspec/changes/procuraai-readiness-linkage/.openspec.yaml
- Lines: 1-2
- SHA256: 5354b7028230e74b1f9295986352ad4a8253375220377f8a7a98bdbaf9251dc9

```md
schema: spec-driven
created: 2026-11-21

```

## openspec/changes/procuraai-readiness-linkage/specs/procurement/ai-readiness/spec.md

- Source: openspec/changes/procuraai-readiness-linkage/specs/procurement/ai-readiness/spec.md
- Lines: 1-56
- SHA256: 2bfb103d4a50aeee0f57f2ebb78f6b4cc17d44151ab5931b7d8ac4afe44319c7

```md
# Spec Delta: 采购 AI 运行期就绪度

## Purpose

以只读、可证伪的方式公示采购 AI 在运行期是否可判定可用，防止配置缺失、Mock 身份或评测未转正被误读为"AI 已上场、能力已达标"。

## ADDED Requirements

### Requirement: 就绪度只读端点必须从四类事实推导受控判词

系统 MUST 提供只读端点 `GET /api/v1/procurement/ai-readiness`，判词 ONLY 由四类运行期事实推导：provider 配置态、AI 能力注册态、全域 kill switch 态、评测集状态。端点 MUST NOT 执行模型调用或写入状态，判词取值 ONLY 为 `AVAILABLE`、`DEGRADED`、`UNKNOWN`。响应 MUST 分离"部署门禁通过"与"授标/放款许可"两种语义。

#### Scenario: 默认配置下不得显示可用

- **WHEN** provider 未配置且能力注册存在、总闸开启、评测为 seededDraft
- **THEN** readiness 判词 MUST NOT 为 `AVAILABLE`，且响应 MUST 标识 provider 未配置这一事实

#### Scenario: 评测未转正时不得声称效果达标

- **WHEN** 评测集状态为 `seededDraft` 或通过线未冻结
- **THEN** 就绪度响应 MUST NOT 出现"效果达标""模型准确"之类断言，即使其余事实均为通过

#### Scenario: 取数失败 fail-closed

- **WHEN** 四类事实中任意一类读取失败或返回未知码
- **THEN** 判词 MUST 为 `UNKNOWN`，MUST NOT 回落到 `AVAILABLE` 或 `DEGRADED`

### Requirement: Mock 模型身份必须降级为不可判定并强制人工终决

合同 AI 审查链路在调用 provider 时 MUST 识别响应是否来自确定性 Mock（例如响应含 `mock:true` 或等价 provider 标识）。命中 Mock 身份时，审查结果 MUST 降级为 `UNKNOWN` 三态并强制人工终决，MUST NOT 返回 `AVAILABLE`，MUST NOT 把 Mock 回显当作模型参与证据。

就绪度输出与启动日志 MUST 在 provider 未配置时显式提示"当前为 Mock / 未配置真实 provider"，不得静默回退到可用语义。

#### Scenario: Mock 回显不得被当成模型结论

- **WHEN** provider 返回带 `mock:true` 的响应
- **THEN** 审查结果 MUST 为 `UNKNOWN` 且要求人工终决，MUST NOT 计入任何"模型参与"或"效果达标"统计

#### Scenario: 未配置 provider 的启动提示

- **WHEN** 应用以默认配置启动且 `bone.ai.gateway.base-url` 未设置
- **THEN** 启动日志或就绪度响应 MUST 出现"provider 未配置/Mock"提示，且 readiness 判词 MUST NOT 为 `AVAILABLE`

### Requirement: 判词对前端必须诚实可渲染

前端就绪度与治理页面 MUST 以受控映射渲染判词：未知判词 MUST 显示原始码并告警，MUST NOT 乐观映射为可用；取数失败 MUST 显示"无法确认"，MUST NOT 显示成"就绪"或数值 0；`false` MUST NOT 渲染为达标。

#### Scenario: 未知判词显示原始码

- **WHEN** 后端返回不在受控集合内的判词
- **THEN** 前端 MUST 显示原始判词字符串并给出告警样式，不得折叠为"可用"或默认成功态

#### Scenario: 请求失败显示无法确认

- **WHEN** readiness 端点请求失败或无响应
- **THEN** 前端 MUST 显示"无法确认"，不得显示 0、空白或成功态

```

## openspec/changes/procuraai-readiness-linkage/specs/procurement/human-review/spec.md

- Source: openspec/changes/procuraai-readiness-linkage/specs/procurement/human-review/spec.md
- Lines: 1-62
- SHA256: 9ff1ec834f1b6bf83423f3ea67fb626d4587dc645d0255f7e26310c40b1cf68f

```md
# Spec Delta: 采购人工终决可操作契约

## Purpose

保证采购高风险 AI 环节（证照 OCR 复核、合同审查推翻）在"AI 仅辅助、人工最终决策"的前提下可被具体操作、可持久化读回、可审计，并防止重复或跨租户篡改。

## ADDED Requirements

### Requirement: 证照 OCR 人工复核必须可用且具名

系统 MUST 提供证照 OCR 人工复核能力，由具名复核人提交结论；MUST NOT 以 `UnsupportedOperationException` 或等价未实现异常响应正常业务请求。复核结论 MUST 持久化并可被读回，读回值 MUST 与提交值一致。

复核记录 MUST 携带租户上下文、复核人身份、复核时间、被复核对象标识与结论；缺失租户上下文时 MUST fail-closed 拒绝，MUST NOT 使用默认租户或空值落库。

#### Scenario: 具名复核后可读回

- **WHEN** 具名复核人提交一条 OCR 复核结论
- **THEN** 系统 MUST 持久化该记录，且后续读回 MUST 返回相同结论、复核人与租户

#### Scenario: 缺租户时拒绝

- **WHEN** 复核请求缺少租户上下文
- **THEN** 系统 MUST 拒绝该请求，MUST NOT 以默认租户或空租户写入

### Requirement: 人工复核必须幂等且拒绝跨租户篡改

同一复核对象在同一租户下重复提交 MUST 幂等：MUST NOT 产生第二条记录、MUST NOT 报错为内部异常；幂等键与对象标识 MUST 唯一约束。跨租户对同一对象提交复核 MUST 被拒绝，MUST NOT 覆盖原记录。

#### Scenario: 重复提交幂等

- **WHEN** 相同租户、相同对象以相同幂等键重复提交复核
- **THEN** 系统 MUST 返回首次结果，且持久化记录数 MUST 保持为 1

#### Scenario: 跨租户覆写拒绝

- **WHEN** 另一租户尝试复核同一对象
- **THEN** 系统 MUST 拒绝，原租户记录 MUST 保持不变

### Requirement: 前端必须提供可操作的人工终决工作台

前端 MUST 提供人工终决工作台页面，展示待决项及其 AI 建议/披露信息，允许具名用户执行采纳或驳回，并把结果写回后端后刷新读回。MUST NOT 只允许只读查看；MUST NOT 将 AI 建议态渲染为已自动生效的最终结论。

未知状态 MUST 显示原始码，取数失败 MUST 显示"无法确认"，MUST NOT 乐观映射为成功或达标。

#### Scenario: 人工终决可写回并读回

- **WHEN** 用户在终决工作台对某待决项执行采纳或驳回
- **THEN** 前端 MUST 提交到后端，刷新后 MUST 显示后端读回的终决结果，而非本地乐观值

#### Scenario: AI 建议不得被渲染为自动生效

- **WHEN** 待决项只有 AI 建议态且尚未人工终决
- **THEN** 工作台 MUST 显示其为待决/建议，MUST NOT 显示为已生效结论

### Requirement: 前后端联调必须以落库为第二证据

前后端联调的验收 MUST 包含真实启动后端与前端、真实浏览器操作以及数据库落库直查或持久化读回。仅 2xx 响应 MUST NOT 单独判定成功。

#### Scenario: 端到端操作有落库证据

- **WHEN** 通过浏览器完成一次人工终决并提交
- **THEN** 数据库或持久化读回 MUST 出现对应记录，且字段与提交一致

```
