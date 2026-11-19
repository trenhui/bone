# 验证报告：procuraai-ai-contract-review-skeleton

- 日期：2026-11-19
- 阶段：Verify（SDK Run `classic-full`，revision 37，phase=verify）
- 验证模式：full / `build_mode: autonomous`、`tdd_mode: tdd`、`review_mode: standard`、`subagent_dispatch: null`
- 产物语言：zh-CN
- 验证对象：`openspec/changes/procuraai-ai-contract-review-skeleton/`（8 条 ADDED Requirements、16 个 tracked tasks）
- 设计修订说明：Build 收尾时因独立审查修复改写了 design.md，触发 `revise-design`；本次已重新 propose→approve→complete Design（proposalHash `9a9716a6…`），再重走 Build 计划/执行/守卫与 Verify。设计本体未变，仅补诚实边界（进程内事件闭环≠生产 MQ、groundTruthRef 不指向源码）。
- 基线收敛说明（rebase）：归档推送前 fetch 发现 `origin/dev` 有 8 个并行提交（synapClaim 归档 + 采购履约协同切片），其中 `pom.xml` / `App.tsx` 与本 change 重叠。已 stash → ff-only 到 `3a17c7d3b` → pop 并手动合并两处冲突（保留 fulfillment + contract 双方），rebase 后重新全量复验（见 §2）。因交付文件哈希变化触发 `CLASSIC_EVIDENCE_DRIFT`，已用 `reopen-verification` 在新基线重开 Verify 并重新验收。
- 建议优化说明（2026-11-19 追加）：依据独立审查的 6 条 SUGGESTION，按「业界最佳实践 + walking skeleton 内可控 + 低风险」筛选后执行 3 项（SUGGESTION-2/5/6，详见 §8）；另 3 项（SUGGESTION-1/3/4）涉及生产持久化唯一约束、评测集扩容决策、文档口径澄清，保留为 owner backlog，不混入本 change。优化后重新全量复验（见 §2）。

## 1. 结论

**PASS**。16/16 任务完成；后端/前端/门禁全绿；独立最终集成审查结论 PASS、无 CRITICAL / IMPORTANT。6 条 SUGGESTION 中 3 条（前端错误态、披露文案单一真源、snapshot 纵深断言）已按业界最佳实践执行并复验（见 §8）；另 3 条（跨租户写入覆写、评测集符号可达、openapi 元数据口径）属 owner backlog / 后续 change，在本 walking skeleton 范围内接受（见 §5）。

本 change 交付的是 **walking skeleton**，用受控夹具 + fail-closed 降级路径验证可观察行为；**不宣称模型效果达标、不宣称生产可用**（生产 MQ/Outbox/Inbox、`t_proc_*` DDL、评测集扩容、真实模型评测均属已声明 L3 非目标）。

## 2. 新鲜验证证据（2026-11-19 实跑）

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端全量单测 + ArchUnit | `mvn -o -f bone-apps/bone-procurement/pom.xml clean test`（JDK21 + spotless:apply 前置） | contract 模块 **68/68**（含 `ArchitectureTest` 13 例 + 新增纵深断言 1 例）、app **3/3**，BUILD SUCCESS；reactor 4 模块（transaction/fulfillment/contract/app）全 SUCCESS，fulfillment **46/46** 亦绿，0 Failures / 0 Errors。优化后（2026-11-19）完整复跑 RC=0 |
| 采购域主门禁 | `python3 tools/check_procura_alignment.py` | **10/10** PASS（A1–A10 全通过） |
| 门禁注入式负向反证 | `python3 tools/check_procura_alignment.py --self-test` | **146/146** 探针全部转红且命中预期 key；6 条正例未误杀 |
| AsyncAPI 零漂移 | `generate_asyncapi.py --check` | PASS |
| 评测集结构门禁 | `check_procura_evalset.py` | PASS（A14/A16/A17 等；评测集 status 仍 seededDraft） |
| 评测集负向反证 | `check_procura_evalset.py --self-test` | **69/69** 探针全部转红 |
| 前端类型检查 | `npm run typecheck --workspace=bone-procurement-app` | PASS（tsc --noEmit） |
| 前端 lint | `npx eslint ... -f json` + JSON 解析 | errorCount=0、warningCount=0（避开 stylish formatter 假绿） |
| 前端单测 | `npm test --workspace=bone-procurement-app` | **9 files / 81 tests** 全绿（披露页测试由 4 例扩至 10 例，含单一真源一致性守卫 + 403/404 错误态） |
| Runtime Build Guard | `comet guard procuraai-ai-contract-review-skeleton build --apply -- bash <check-script>` | GUARD_EXIT=0，`ALL CHECKS PASSED — phase=verify`，真实重跑完整检查脚本 |

> 上述命令由 `/tmp/procuraai-contract-review-build-check.sh` 串接，Guard 以 `--apply` 重新真实执行并绑定收据。

## 3. 完整验证 7 项检查

1. **tasks.md 全部完成**：PASS —— 16/16 勾选，任务 UUID 稳定；`planMapping.status: ready`、`unmappedCount: 0`；plan 中「17 项/17/17」笔误已修正为 16，与 Runtime `total=16, completed=16` 一致。
2. **实现符合 design.md 高层决策**：PASS —— D1 独立模块、D2 端口复用 bone-ai-*、D3 快照引用+7 字段事件（含进程内闭环诚实边界）、D4 确定性 HITL、D5 披露三态、D6 复用门禁不改 `FULL_GATE_KEYS`，逐项有源码与测试支撑。
3. **能力规格场景一致性**：PASS —— spec 8 条 Requirement 逐条有实现 + 测试覆盖（见 §4 覆盖清单）；`ai_available` 三态与 UNDETERMINED 不压布尔、model 版本快照不作参与证据、PII 禁入、隐藏指令 fail-closed、租户 fail-closed 均由负向探针锁定。
4. **proposal.md 目标达成度**：PASS（限 walking skeleton 范围）—— 端到端审查链路、能力开关、披露页、门禁对齐均落地；地基层 F1–F4、组 5 决策治理、模型选型、评测集扩容等已声明非目标，不计为缺失。
5. **delta spec 与 design doc 无矛盾**：PASS —— 无 spec 有而 design 未记录的增量；design frontmatter（`comet_change`/`role`/`canonical_spec`）齐备，哈希与 Run 证据一致（`06e2dae…`）。
6. **关联设计与计划文档可定位**：PASS —— `docs/superpowers/plans/2026-11-18-procuraai-ai-contract-review-skeleton.md` 存在，`<!-- comet-task-authority -->` 与 16 个 `<!-- comet-task-ref -->` 齐备。
7. **门禁与 CI 口径**：PASS —— 本族门禁 10/10 + 146/146 + evalset 69/69 + AsyncAPI 零漂移；未改 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` 三入口判据集合；openapi +4 端点与 `EXTRA_OPENAPI_ENDPOINTS` +4 双向一致；未引入 A10.16 黑名单串。`CI 绿 ≠ AI 可上线` 结论不变。

## 4. 独立最终集成审查

`review_mode: standard` 要求独立 reviewer 对最终 diff 完成一次审查。本次以独立只读子代理对当前 change 全部源码/测试/契约执行审查（非实现者自评）。关键论断由本 Verify 会话亲自读源码复核。

**审查结论：PASS（CRITICAL 0 / IMPORTANT 0 / SUGGESTION 6）。**

规格覆盖清单（8 条 Requirement）：

| Requirement | 结论 | 关键证据 |
|---|---|---|
| 合同解析失败 fail-closed | ✓ | `ContractParsePortAdapter` + `ContractReviewApplicationService` 解析异常转人工 |
| 风险报告字段与 PII 禁回传 | ✓ | `ContractReviewedEvent` 严格 7 字段、`ReviewEventAssembler` 仅出 hash |
| risk_level UNKNOWN 不降级 | ✓ | `RiskLevel` 独立态、`HitlRoutingPolicy`、`ContractReviewedConsumer` 隔离 |
| HITL 置信度分层路由 | ✓ | `HitlRoutingPolicy` 确定性规则 + 人工推翻留痕 |
| AI 参与披露三态 | ✓ | `AiParticipation` 四态、`AiDisclosureService`「无法确认」、前端原样显示 |
| 不可信输入隔离 | ✓ | `ContractReviewPortAdapter` 入口 `injectionGuard.check` |
| 审查留痕、幂等与追溯链 | ✓ | `ContractReviewedConsumer.putIfAbsent` 幂等 + `ReviewTracePort` |
| 能力开关与权限边界 | ✓ | `@ConditionalOnContractAiReviewEnabled(matchIfMissing=false)`、`@PreAuthorize`、租户从登录态取 |

Build 阶段修复点复核（均仍有效）：状态机后门已剔除且单测锁定；PII 入口扫描 + `PII_REJECTED` 告警到位；openapi 漂移已消除且 `FULL_GATE_KEYS` 未改；事件生产消费进程内闭环与 design D3 诚实边界一致、7 字段锁定；ArchUnit 仅排除测试类、无业务包排除；groundTruthRef 全部指向真源文档锚点、无一指向源码 `.java`（A16 口径成立）。

## 5. 已接受偏差与遗留项（Accepted Deviations）

| 编号 | 级别 | 事实 | 处置 |
|---|---|---|---|
| SUGGESTION-1 | SUGGESTION（安全前置条件） | `submit` 接受请求体 `reviewId`，`InMemoryContractReviewStore.save` 用 `Map.put` 直接覆盖；知悉他人 reviewId 的租户可覆写其记录，致原租户读取转为跨租户拒绝。当前能力开关默认关闭、仅受控内存实现；`existsGlobally` 接口注释明确用于读取侧区分「跨租户拒绝/不存在」，非写入冲突检查。spec 场景仅约束「命令自带租户与登录态不一致」，提交命令不含自报租户，故不构成 spec gap | walking skeleton 范围内接受；**接生产持久化前 MUST 加 `(tenant_id, review_id)` 唯一约束或服务端生成 reviewId**。不属本 change 缺陷，转 owner backlog |
| SUGGESTION-2 | SUGGESTION（已修复） | 前端披露页 403/404 时空 `.catch()` + `loading={!data}` 导致永久 loading | ✅ 2026-11-19 已执行：新增 `error` state + `Alert` 错误态、`disclosureErrorMessage()` 403/404 降级文案，`loading={!data && !error}`；见 §8 |
| SUGGESTION-3 | SUGGESTION | 评测集 2 条中文 `testSymbol` 源码可达性存疑（`check_procura_evalset.py` A17 只校唯一性） | 评测集本次未改（历史 seededDraft），下一 change 对齐 design D6「符号源码可达」口径 |
| SUGGESTION-4 | SUGGESTION | `openapi.yaml` 元数据 `a10_9_external-whitelist-size` 仍为 5，实际白名单 9 项 | 门禁不校该元数据，不影响绿态；建议澄清口径（是否仅指跨域只读投影） |
| SUGGESTION-5 | SUGGESTION（已修复） | 披露文案后端 `disclosureText` 与前端 `KNOWN_TEXT` 双真源，当前一致 | ✅ 2026-11-19 已执行：`resolveDisclosureText()` 以后端文本为真源、参与码作一致性守卫（冲突即 fail-closed 兜底），消除双真源漂移且不放松「未知态=无法确认」红线；见 §8 |
| SUGGESTION-6 | SUGGESTION（已修复） | `snapshot()` 未强制 `humanFinalized=true` 断言 | ✅ 2026-11-19 已执行：`snapshot()` 在 HUMAN_REVIEW 迁移前校验 `humanFinalized`，补纵深断言；负向探针（未经人工终决不得快照）注入必红/还原必绿；见 §8 |
| L3 非目标 | 已声明 | 生产 MQ/Outbox/Inbox 未接线（进程内直连）；`t_proc_*` DDL 未落生产库；评测集未扩容 ≥200；未做真实模型效果评测 | design.md 已显式声明，不计为缺失；另立 owner change |

## 6. 并行会话与工作区说明

工作区存在 `openspec/changes/synapclaim-architecture-convergence/` 等本 change 无关的在途文件，以及已修改的 `bone-apps/bone-procurement/pom.xml` 等本 change 文件。验证仅归因本 change 相关文件，不把无关在途工作计入成果。模块种子 SQL（0127/0128）仅做静态契约校验，**未在真实 MySQL 执行**；权限/菜单落库验证属集成验收 L3。

## 7. 变更说明

`change_walkthrough: true` 已生成：`openspec/changes/procuraai-ai-contract-review-skeleton/walkthrough.html`（含主路径流程图与验证结果）。

## 8. 建议优化（2026-11-19 追加，基于业界最佳实践）

独立审查给出 6 条 SUGGESTION。按「walking skeleton 内可控 + 低风险 + 有明确最佳实践」筛选，执行 3 项；其余 3 项涉及 owner 决策或后续 change（见 §5）。三项均采用 TDD（先红后绿）并全量复验（见 §2）。

### 8.1 SUGGESTION-2 — 前端错误态消除永久 loading

- **问题**：披露页 `.catch(() => {})` 吞掉 403/404/网络异常，`loading={!data}` 永远为真 → 永久转圈。
- **优化**：新增 `error` state 与 antd `<Alert type="error">`；`disclosureErrorMessage(err)` 将 403/404 映射为人话提示、其余保留原始状态码；`loading={!data && !error}`，失败时不再显示自动结论（fail-closed 保持）。范式对齐同应用 `FulfillmentConsole` 的 `fromApiError`/`degradeMessage`。
- **证据**：`types/contractReview.ts:disclosureErrorMessage`、`pages/ContractReviewDisclosure.tsx`；前端测试 +6 例（403/404/未知错误）。

### 8.2 SUGGESTION-5 — 披露文案单一真源

- **问题**：后端 `AiDisclosureView.disclosureText` 与前端 `KNOWN_TEXT` 各维护一份，未来漂移会让展示与后端裁决不一致。
- **优化**：`resolveDisclosureText(code, backendText)` 以后端文本为真源，但用参与码做一致性守卫——仅当后端文本与按码生成的本地文案一致时透传，否则回退本地兜底。这样既消除双真源漂移，又确保后端文本被篡改/与码冲突时立即 fail-closed，**不放宽「UNKNOWN/UNDETERMINED = 无法确认」红线**。
- **证据**：`types/contractReview.ts:resolveDisclosureText`；前端测试 +3 例（一致透传/冲突回退/缺失兜底）。

### 8.3 SUGGESTION-6 — snapshot 纵深断言

- **问题**：状态机允许 `HUMAN_REVIEW→SNAPSHOTTED`，但 `routeToHuman()` 不置 `humanFinalized`；缺断言时可在无人工推翻留痕的情况下直接快照。
- **优化**：`snapshot()` 在 `HUMAN_REVIEW` 迁移前校验 `humanFinalized`，否则抛「必须先完成人工终决留痕才能快照」。两层防护分工：状态机先裁决迁移合法性（IN_REVIEW/PARSED→SNAPSHOTTED 仍抛含状态名的原错误，保持原契约），断言只守「人工终决留痕」这一层。生产链路 `override()` 的 `recordHumanOverride→snapshot` 顺序本就正确，不受影响。
- **证据**：`domain/model/ContractReview.java:snapshot`；新增负向测试「未经人工终决不得快照_纵深断言」——注入断言前 red（Expecting code to raise a throwable）、加断言后 green；同步修正 2 处直接走 `routeToHuman→snapshot` 的测试夹具为真实链路。contract 模块 **68/68** 全绿。

### 8.4 未执行项（留给 owner）

| 编号 | 原因 |
|---|---|
| SUGGESTION-1 | 需生产持久化 `(tenant_id, review_id)` 唯一约束或服务端生成 reviewId，属接生产前 MUST，不属 walking skeleton 缺陷 |
| SUGGESTION-3 | 评测集 2 条中文 `testSymbol` 可达性，评测集为历史 seededDraft，本 change 未改，下一 change 对齐 design D6 |
| SUGGESTION-4 | `openapi.yaml` 元数据 `a10_9_external-whitelist-size` 口径需澄清（门禁不校、不影响绿态） |
