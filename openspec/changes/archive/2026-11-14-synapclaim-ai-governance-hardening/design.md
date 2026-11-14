## Context

`synapclaim-ai-native-closeout` 已归档，`bone-frontend/apps/bone-claim-app` 的 AI 治理面板、5 个 `claim-ops` 只读端点消费、27/27 前后端 E2E 均已落地。本 change 只收口其复核结论中的编码缺口，不重做资金/决策链路。

关键现状事实（已核码）：

- `_ai_native_readiness.py:344-367` 的 `RUNTIME_MODEL_COVERAGE` 只读 `nodesScanned/participationRate/aiParticipated`；`PROVIDER_CONFIGURED` 在 `:160-168` 读 `provider_env`。
- `AiModelProvenance.of` 是黑名单：非空且非 `local-stub`/`mock*` 即 `PROVIDER`；`AiProviderProbeAdapter.configured()` 才是配置态真源 `bone.ai.gateway.base-url`。
- E2E A 组窗口写死 `2026-10-06 ~ 2026-11-05`；`AiGovernancePage` 调 `governanceWindow(7)`；E2E 产物 `nodes=88/aiParticipated=0`，面板同日窗口 `aiParticipated=8/rate≈8.79%`。
- `ReviewWorkbenchPage.tsx:288` 的 `PageHeader` 未传 `extra`；治理页只有返回按钮，反向无入口。
- `AiProviderProbeController` 已写，前端 0 引用；其 javadoc 明确要求不做高频轮询。
- `AiConfidenceCalibration.CalibrationReport` 含 `buckets`/`slices`，前端 interface 无这两个字段。
- E2E C 组三条正则依赖复核器输出字面量，不可改格式。

## Goals / Non-Goals

**Goals:** 消除局部假绿；让两个窗口成为同一真源；补齐三处「已写未接线」可观测缺口；保持 `l3Claimable=false` 的诚实结论。

**Non-Goals:** 见 proposal.md「明确不做」。特别是不改 Java 生产代码、不改 DDL/CI、不改 DAG、不伪造任何 BLOCKED 条件达标。

## Decisions

### D1：双条件放在复核器侧，不改 Java 统计口径

`AiRuntimeHealthApplicationService.aggregate` 用 `static` 纯函数，21 处单测直接调用；给它注入 `LlmGatewayProperties` 会破坏构造签名与「只读观测」语义。运行期健康度应客观反映节点自报事实；「是否算模型上场」属达标判定，放在 `_ai_native_readiness.py` 更合适，且零 Java 改动、零并发冲突。

替代方案（已否决）：改 `AiModelProvenance.of` 签名 → 6 个生产调用点 + 5 组枚举单测 + `LocalAiGatewayPortAdapterTest` 全破，且把 Spring 配置读进纯领域枚举违反分层。

`RUNTIME_MODEL_COVERAGE` 改为：`SATISFIED iff provider_env 非空 AND nodes>0 AND rate>0 AND participated>0`。`reason` 三态：`PROVIDER_NOT_CONFIGURED`（优先）/ `MODEL_PARTICIPATION_ZERO` / `ADMITTED`。`detail` 附 `providerConfigured=`。

`--self-test` 的原「运行期参与率改为 0.5」探针改为「provider 已配 + 参与率 0.5」以保证翻转；另增一条「仅伪造参与率而 provider 未配不得翻绿」的负向探针，其失败会让 `self_test` 返回 False。`scripts/tests/test_ai_native_readiness_flywheel.py` 补两条表征测试。

### D2：UTC 日界 30 天窗口，不改 C 组正则

算法：`dayMs=86400000`，`to = ceil(now/dayMs)*dayMs`（下一 UTC 日界，覆盖当日），`from = to - 30*dayMs`。长度恰 30 天 `< 31` 上限；同一 UTC 日内多次调用结果恒定，消除毫秒级漂移。

- `aiGovernance.ts` 的 `governanceWindow()` 用此算法；`AiGovernancePage` 去掉 `governanceWindow(7)` 的显式参数。
- E2E 顶部定义 `EVIDENCE_WINDOW`，A 组 `ai-runtime-health` 与 C 组前的 `result.runtime` 均取其 `from/to`；B 组新增断言：面板声明窗口与 `EVIDENCE_WINDOW` 逐字一致。
- 复核器输出三处字面量与 C 组正则**保持原样**；`result.runtime` 仅新增窗口元数据字段。
- 窗口统一后产物参与率可能非零，D1 保证 `RUNTIME_MODEL_COVERAGE` 仍 `BLOCKED`、`l3Claimable=false`。

### D3：深链按钮走 `useNavigate`，不动 IAM 菜单

`ReviewWorkbenchPage` 已有 `useNavigate`（需确认并补 import）；`PageHeader.extra` 增加「AI 治理」按钮 `navigate('/ai-governance')`。E2E 改为真实点击该按钮进入治理页，保留 `location.hash` 方式作为回退负向自检。hidden 路由仍被 `claim-single-nav-probe.mjs` 豁免，菜单双向全等不变。

### D4：探活手动触发，默认零请求

`aiGovernance.ts` 增 `providerProbe()` 与 `ProviderProbeReport` 类型、`probeMeta()` 映射（含 `NOT_CONFIGURED`/`REACHABLE`/受控降级码/未知码）。`AiGovernancePage` 的 `Promise.allSettled` 仍只拉 5 个端点；探活结果独立 `useState`，仅按钮 `onClick` 触发。「刷新」不触发探活。E2E 用 `page.on('request')` 统计 `ai-provider-probe` 请求数：进入页面为 0，点击后为 1。

### D5：校准明细按证据态渲染

`CalibrationReport` 补 `buckets: BucketStat[]`、`slices: SliceStat[]`；新增 `calibrationEvidenceMeta(verdict, buckets, slices)`，仅当存在明细且 verdict 非禁止态时允许表格渲染。无证据显示判词说明，不画空曲线、不把 `null` 写 0。E2E 加一条「NO_EVIDENCE 时不出现分桶表格」断言。

### D6：文档口径同步

`01-文档治理规约.md` 端点数 5→6；`BONE-AI能力依赖图谱.md` §9.1 第 5 条的「滚动 30 天窗口」改为 UTC 日界 30 天、并补上 D1 判据说明；两份文档机检 `_check_manifest.py` / `_check_xref.py` / `_check_numbers.py` 保持通过。

## Risks / Trade-offs

- **并发会话竞态**：并发会话正在改 `bone-ai-document` 与 `synapclaim-risk-fraud-dimensions`。本 change 零 Java 生产改动，只碰前端、Python 判据、E2E、两份文档，避免与其冲突；提交时按路径式 `git commit -- <本 change 路径>` 隔离。
- **窗口统一改变 E2E 证据数值**：`nodes`/`aiParticipated` 会随当日数据变化，属预期；D1 已保证 L3 结论不被翻绿，`outputs/claims-e2e-result.json` 会如实记录新值。
- **探活端点成本**：一次真实 upstream 调用。手动触发 + 零轮询断言把成本限定在用户显式操作。
- **复核器 `--self-test` 结构变化**：新增负向探针需同步 `test_self_test_probes_all_flip` 的预期；`gid` 字典要补标签映射。
- **不追求 L3 数字变化**：本 change 交付的是「判据更严、口径更一致、可观测缺口补齐」，预期 `SATISFIED 5 / BLOCKED 7` 不变。任何把 BLOCKED 改小的改动都是假绿。

## Open Questions

无。所有实现取舍均已由已核码事实与 proposal 的非目标界定；不存在需要用户补充才能落笔的未知项。
