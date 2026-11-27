# Verify Report — procuraAI 就绪度加固与前后端联调

- **change**: `procuraai-readiness-linkage`
- **base ref**: `8a009e5e58313751a4e183feac79e11888c2db1d`
- **date**: 2026-11-24
- **result**: **PASS**
- **scope**: 装配一致性、Mock 降级、只读就绪度端点、OCR 人工复核、前端三页与前后端联调；不声称真实模型达标 / 评测转正 / 契约冻结 / 生产 DDL / 生产 MQ。

## 1. 交付范围与结论

行为真源：2 份 delta spec（`procurement/ai-readiness`、`procurement/human-review`）共 6 Requirement / 11 Scenario；tasks.md 15 项全部完成。实现主体由提交 `3e5b5f77c`（"完成 procuraAI 就绪度真源化与全功能联调加固"，63 文件）落地，本轮 Verify 追加 4 文件审查修复（见 §4）。

结论：实现与规格一致，核心不变量（登录态权威身份、Mock 不返回 AVAILABLE、未知 fail-closed、人工终决幂等与跨租户拒绝）均有真实测试；两轮独立审查最终 `CRITICAL=0 / IMPORTANT=0`。本轮重跑的后端全量、前端 typecheck/vitest/eslint、6 项 procuraAI 门禁均真实通过。判定 PASS。

## 2. 本轮重跑证据（2026-11-24，当前工作区）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `mvn -o -f bone-apps/bone-procurement/pom.xml test` | BUILD SUCCESS，10 模块全绿；app 模块 Tests run 26 / 0 失败（`/tmp/readiness-mvn-final.log`、`/tmp/readiness-guard-build.log`） |
| 2 | `mvn ... -pl bone-procurement-qualification -am test`（修复后） | Tests run **47**, Failures 0, Errors 0（`/tmp/readiness-qual-final2.log`） |
| 3 | `npx tsc --noEmit -p apps/bone-procurement-app/tsconfig.json` | exit 0 |
| 4 | `npx vitest run --root . apps/bone-procurement-app` | 15 文件 / **108 例**全通过（`/tmp/readiness-vitest.log`；2026-11-24 收尾复验 `/tmp/readiness-final-vitest.log` 同为 15/108，新增 1 条 fail-closed 告警色用例） |
| 5 | `npx eslint apps/bone-procurement-app/src -f json` | **errorCount=0 / warningCount=0 / 46 文件**（JSON 解析，非 stylish 假绿） |
| 6 | `python3 tools/check_procura_alignment.py` | `=== 全部通过 (10/10) ===` |
| 7 | `python3 tools/check_procura_alignment.py --self-test` | `157/157 负向探针全部转红` |
| 8 | `python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py` | `RESULT: PASS`（status=seededDraft, cases=27, target=200） |
| 9 | `python3 tools/check_procura_persistence.py` | `[A10.29] PASS — 9 表 / 10 端口` |
| 10 | `python3 tools/check_procura_event_chain.py` | `[E-CHAIN] PASS — 3 表 / 4 负向探针` |
| 11 | `python3 scripts/check-migration-manifest.py` | OK（154 文件 / 147 脚本 / order 1..147 连续） |

全部命令退出码 0。前端判词映射子集 `readinessView.test.ts` 6 例单独复跑通过（含新增 3 条告警色断言）。`comet guard ... build --apply` 以完整采购全量测试作为 Build check 真实执行，`ALL CHECKS PASSED — phase=verify`。

## 3. 行为验收逐条核对（源码实证）

| Requirement 承诺 | 结论 | 关键证据 |
|---|---|---|
| 装配一致性：默认配置无 Bean 缺失 | 通过 | `AdvisoryController` / `AdvisoryApplicationService` 与治理 Bean 同用 `@ConditionalOnAiGovernanceEnabled`（matchIfMissing=false）；`AdvisoryAssemblyConditionTest` 双态探针 |
| Mock 身份降级，不返回 AVAILABLE | 通过 | `ContractReviewPortAdapter.isMockResponse`（mock:true / MOCK 前缀 / model 含 mock）→ 追加 `MOCK_PROVIDER_REJECTED` trace → `degrade()`→UNKNOWN；`HitlRoutingPolicy` 强制人工 |
| readiness 端点四类事实 + fail-closed | 通过 | `AiReadinessEvaluator` 聚合配置态/能力注册/总闸/评测态；判词仅 AVAILABLE/DEGRADED/UNKNOWN；`AiReadinessView.DEFAULT_NOTICE` 声明"部署门禁≠授标/放款许可" |
| OCR 人工复核幂等 + 跨租户拒绝 + 读回 | 通过 | `reviewOcrByHuman` findById→reviewByHuman→save；`SecurityContextReviewIdentityPort` 登录态权威；同复核人幂等、跨租户拒绝；持久化读回测试 |
| 前端三页：未知码原样 + 告警色 + 失败"无法确认" | 通过（本轮加固） | `readinessView.ts` 未知/未映射/null → 原始码 + `red` 告警色；三页路由已挂 |
| 状态诚实红线 | 通过 | evalset 保持 `seededDraft/notStarted/caseCount=27/target=200`；未改契约冻结、DDL、CI 三入口判据集合、owner/法务结论 |

## 4. 独立审查与修复闭环（review_mode=standard）

**第一轮**（Explore 独立 reviewer）：`CRITICAL=0, IMPORTANT=1, WARNING=1, SUGGESTION=3`。
- IMPORTANT-1：新增 `findOne` 端点用请求参数 `query.tenantId()` 而非登录态权威租户，持有 `procurement:qualification:read` 者篡改 URL 参数可跨租户读证照（正是 HITL 工作台读回依赖）。
- WARNING-2：未知判词 `verdictColor` 返回灰色 `default`，不满足 spec"告警样式"。
- SUGGESTION-3：`reviewOcr` 登录态字段校验弱于 `approve`。

**修复（TDD RED→GREEN，均真实跑过）**：
1. `QualificationController.findOne` 改为取 `reviewIdentity.current().tenantId()`，无登录态/tenantId 为 null 即拒绝，请求参数与登录态不一致即拒绝；新增 3 条负向/正向测试。RED：`证照读回无登录态/跨租户` 2 例失败；GREEN：全绿。
2. `readinessView.ts`：`UNKNOWN` 及 null/undefined/未映射码 → `red`；新增 3 条告警色断言，RED 3 失败→GREEN 6 通过。
3. `reviewOcr` 补齐 `tenantId/reviewer/isBlank()` 字段校验，与 `approve` 对齐；新增 2 条负向测试。
4. 采纳复审建议补 `FindQuery(null)` 走登录态用例（锁定"缺省也走登录态"）。

**第二轮复审**（独立 reviewer，核对 `git diff HEAD`）：`CRITICAL=0, IMPORTANT=0`，可 PASS。确认 findOne 缺口闭合、与 reviewOcr/approve 安全模型一致、无新 fail-open、前端反假绿语义未破坏。

修复 diff 仅 4 文件的源码/测试：`QualificationController.java`、`ReviewIdentityBindingTest.java`、`readinessView.ts`、`readinessView.test.ts`（81 增 7 删），未触碰 ci.yml / check.sh / check-procura-ci.sh / 门禁工具 / evalset / bone-init.sql。spotless 已 apply。

## 5. 证据边界（诚实记录）

- 提交 `3e5b5f77c` 说明记录"后端 398 项、前端 97 项门禁及全功能 E2E 均通过"；`scripts/e2e/internal-poc-verify.py` 是可复现的内部模拟 POC 取证器（诚实标注"技术验证，不构成真实商业 POC 通过"）。
- 本轮 Verify **未重新执行浏览器实操/MySQL 直查**：tasks 7.1/7.2 的浏览器与落库证据由上述提交记录与该取证脚本承接，本轮工作区无新增截图文件；如验收需要当前时点的浏览器证据，应单独补做并留存，不以本轮通过结论替代。
- 生产部署注意：`AiReadinessApplicationService.DEFAULT_EVALSET_PATH` 为相对路径，非仓库根目录启动会读取失败→UNKNOWN（fail-closed 安全）；生产须配置绝对路径 `bone.procurement.ai-readiness.evalset-path`（SUGGESTION，不阻塞）。

## 6. 仍需 owner / 法务 / L3（不属于本 change）

真实模型接入与效果达标、200 例评测转正与通过线冻结、契约冻结、生产 DDL/MQ、Outbox 闭环、法务书面结论、供应链 owner 验证 SHA。CI 绿仅证明结构与 fail-closed 不变量，不等于 AI 能力可上线。

## 7. 最终判定

**PASS — 可进入 Comet Archive。** 本 change 边界是"让已有 AI 骨架可达、可判定、可操作且安全"，所有未授权结论保持待填；审查发现的跨租户读缺口已按安全模型修复并复验。
