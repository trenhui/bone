---
comet_change: synapclaim-ai-governance-hardening
role: technical-design
canonical_spec: openspec
---

# synapClaim AI 治理收口技术设计

OpenSpec 契约真源：`openspec/changes/synapclaim-ai-governance-hardening/proposal.md`、`design.md`、`tasks.md` 与 `specs/` 下三项能力规格。本文件是 full workflow 要求的正式技术设计记录，不另立口径。

## Context

`synapclaim-ai-native-closeout` 已归档；AI 治理面板、5 个 `claim-ops` 只读端点消费、27/27 前后端 E2E 均已落地。循环复核暴露三处编码可修的缺口，以及一个必须先堵住的假绿风险：

- 证据窗口分裂：E2E 产物固定窗口 `nodes=88 / aiParticipated=0`，治理面板滚动 7 天窗口同日实测 `aiParticipated=8 / rate≈8.79%`。
- `_ai_native_readiness.py` 的 `RUNTIME_MODEL_COVERAGE` 只看三个数；`AiModelProvenance` 用黑名单判定（非空且非 `local-stub`/`mock*` 即 `PROVIDER`），provider 未配时也可能留下这类节点。窗口一换，判据会单独翻绿。
- `ai-provider-probe` 端点前端 0 引用；`CalibrationReport.buckets/slices` 已计算但前端未消费；`/ai-governance` hidden 路由无业务页面入口。

## Decisions

### D1 三条件放在复核器侧

`RUNTIME_MODEL_COVERAGE` 判 `SATISFIED` 需同时满足：① `provider_env` 非空；② `runtime.probeVerdict == REACHABLE`；③ `nodes>0 / rate>0 / participated>0`。reason 四态：`PROVIDER_NOT_CONFIGURED` > `PROBE_NOT_REACHABLE` > `MODEL_PARTICIPATION_ZERO` > `ADMITTED`，detail 附 `providerConfigured=` 与 `probeVerdict=`。

**第 10 轮循环复核增量（2026-11-07）**：原双条件残留一个假绿面——`AI技术方案 §11.7` 记录过「探活绿灯、业务全量降级」（业务请求把逻辑别名当模型名发给上游，探针却用配置模型），即「配了 ≠ 通了 ≠ 契约对」。故补第二条件：E2E 从**同一次手动点击**的 `/ai-provider-probe` 响应读取受控判词并写入 `runtime.probeVerdict`，不额外发请求、不引入自动轮询；判词缺失或非 `REACHABLE` 一律 fail-closed。`--self-test` 增「配了但探活失败」「判词缺失」「判词与参与率一起伪造」等负向探针，`RuntimeModelCoverageTest` 增「摘除探活条件须被自测判红」变异用例。

不改 `AiRuntimeHealthApplicationService.aggregate`：它是 static 纯函数、被单测直接调用，注入配置会破坏构造签名；运行期健康度应保持客观观测，「是否算模型上场」属达标判定。也不改 `AiModelProvenance.of`：6 个生产调用点与枚举单测全破，且把 Spring 配置读进纯领域枚举违反分层。

`--self-test` 把「参与率改为 0.5」探针改为「provider 已配 + 参与率 0.5」以保证翻转，另增「仅伪造参与率不得翻绿」负向探针；`scripts/tests/test_ai_native_readiness_flywheel.py` 补两条表征测试。

### D2 UTC 日界 30 天单一真源

`to = ceil(now/86400000)*86400000`（下一 UTC 日界，覆盖当日），`from = to - 30*86400000`。同日多次调用恒定，消除毫秒级漂移；长度恰 30 天，小于端点 31 天上限。

前端 `governanceWindow()` 与 E2E `EVIDENCE_WINDOW` 同算法；E2E B 组新增「面板声明窗口与 EVIDENCE_WINDOW 逐字一致」断言。复核器输出三处字面量与 C 组三条正则保持原样，`result.runtime` 只加窗口元数据。窗口统一后参与率可能非零，D1 保证 L3 结论不被翻绿。

**实跑修正（2026-11-07）**：E2E 首轮 32/34，两处窗口同源断言判红——`toISOString()` 输出带毫秒的 `.000Z`，后端 `windowFrom/windowTo` 是 Jackson 序列化的 `Instant`，回显为秒级 `Z`。单一真源因此补上「秒级 ISO、无毫秒」的字符串规范，前端与 E2E 同函数产出，并加 1 条钉住该格式的负向用例；复跑 34/34。没有采用「比较时再归一化」的宽松做法，逐字一致仍是真断言。

### D3 深链入口

`ReviewWorkbenchPage` 的 `PageHeader.extra` 增加「AI 治理」按钮 `navigate('/ai-governance')`；E2E 改为真实点击进入，保留改 hash 作为回退负向自检。hidden 路由不进 IAM 菜单，单导航探针的可见路由双向全等不变。

### D4 探活手动触发

`aiGovernance.ts` 增 `ProviderProbeReport`、`providerProbe()`、`probeMeta()`；页面 `Promise.allSettled` 仍只拉 5 个端点，探活结果独立 state、仅按钮 `onClick` 触发，「刷新」不触发。E2E 用 `page.on('request')` 断言进入页面 0 次、点击后 1 次。端点每次真实消耗一次 provider 用量，禁止轮询。

### D5 校准明细按证据态渲染

`CalibrationReport` 补 `buckets`/`slices`；`calibrationEvidenceMeta()` 仅在存在明细且 verdict 非禁止态时允许表格。`NO_EVIDENCE` 等态不画曲线、不把 `null` 写 0。E2E 加「NO_EVIDENCE 时无分桶表格」断言。

### D6 文档口径同步

治理规约端点数 5→6；能力依赖图谱 §9.1 第 5 条改为 UTC 日界 30 天单一真源并补 D1 说明。

## 第 11 轮循环复核增量（2026-11-07）

① **`current_level()` 防假绿**：原实现只看 `PROVIDER_CONFIGURED` 与 `PERCEPTION_MODEL_DRIVEN` 两个配置态判据，配好 provider + 打开 `llm` 即判 L2（AI 承担部分主负载），哪怕 E2E 证明模型从未上场。改为 `RUNTIME_MODEL_COVERAGE` 非 SATISFIED 时一律回落 L0 并带原因码；三条件齐备且感知由模型驱动才判 L2。`MaturityLevelTest` 7 例（含「退化成只看配置态」源码变异）守护。

② **E2E 终态对账**：C 组对账用的是中途快照（当时还没有探活判词），最终产物若被污染就无人复核。现在最终产物落盘后再跑一次复核器，断言输出回显本轮 `probeVerdict`、非 REACHABLE 时 `RUNTIME_MODEL_COVERAGE` 仍 BLOCKED 且 `l3Claimable=false`；产物新增 `probeVerdictCapturedAt` 与 `finalReadinessSnapshot`。

③ **文档族收尾**：交付包编号 `00`–`10` → `00`–`11`，`aiRouting` 补 `delivery.11` / `review.round9` 两条 guide 路由，全套人工检查清单补 `_gen_ai_index.py --check`。

## 运维手册 AI 链路补齐增量（2026-11-07 · 运行手册缺口表第 1 项闭合）

`07-delivery/08-部署运维手册.md` 补齐 AI 链路运维（此前 `BONE_AI_GATEWAY_*` / `ai-provider-probe` / `ai-runtime-health` / `synap-claim-e2e.cjs` 全文 0 命中）：① §1.3 「三态开关」修正为 §11.12 四档阶段机口径（调档唯一路径 = `feature_toggle.config_json`、全局开关 ≠ 自动出金、未配置按 SHADOW fail-closed）；② 新增 §1.4 环境变量表（含诚实降级缺省行为与门控默认关）；③ 新增 §2.4 值班巡检速查（探活不缓存不轮询、UTC 日界 30 天窗口、E2E 产物不可复现须登记才可版本化）；④ L28/L176「三态」措辞同步修正。缺口表第 2 项（`BONE_CLAIM_DOCUMENT_PARSE_MODE` 入 AI技术方案）已由并行会话在未提交改动中补齐，本轮不重复。只做文档口径同步，零代码改动，判据与 `l3Claimable` 不变。

**证据版本化（同轮追加）**：按运行手册 §五建议建立 `09-records/evidence/`（L9，role=evidence/currency=frozen），把 2026-11-07 实跑的 E2E 产物（passed、37 条断言、`probeVerdict=NOT_CONFIGURED` 诚实降级、4×503 与判词同源）与复核器输出逐字固化为时点取证，登记 manifest（`records.evidence.index/e2e-20261107/readiness-20261107`），README 载明采集 HEAD（d953da0fc）与复现纪律；运行手册 §五与运维手册 §2.4 同步改为指向已建证据目录。JSON 以 md 围栏承载以过 M9 front-matter 机检。

## 盲标标注工作台落地增量（2026-11-07 · §11.13 交付排期项）

§11.13 边界明示「标注工作台前端属交付排期」；用户定向后落地。后端 `BlindLabelController` 5 端点已就绪，本轮纯前端（claim-app）：
- **`services/blindLabel.ts`**：契约类型（`BlindLabelTaskView` 与后端 record 逐字段对应）+ 5 端点客户端（复用 `createApiClient('/api/v1/claim-ops')`）+ 诚实元数据（`statusMeta` 未登记状态码按原文降级、`agreementMeta` 区分 null=未解盲 与 AI_ABSENT）+ 载荷预检 `scanPayload`（禁区 14 键与 `BlindLabelTask.FORBIDDEN_PAYLOAD_KEYS` 逐字一致、上限 8000 同步；后端 validatePayload 仍是唯一权威，前端只做 UX 提前提示）。
- **`pages/BlindLabelWorkbenchPage.tsx`**：标注人 ID（localStorage 持久化）→ 最近任务表（上限 50）→ 派发表单（载荷禁区预检反馈）→ 任务详情按状态分流：PENDING 看载荷提交结论（一次性警示）、LABELED 解盲 Popconfirm（不可逆警示）、UNBLINDED 双栏配对（盲标结论 vs AI 快照；AI_ABSENT 无快照占位为诚实文案，不编造）。
- **路由与入口**：`routes.tsx` 增 hidden 路由 `/blind-labels`（同 `/ai-governance` 形态，探针豁免菜单全等）；`ReviewWorkbenchPage` PageHeader 增「盲标」深链按钮（与「AI 治理」并列）。
- **验证**：claim-app `tsc --noEmit` 通过；改动文件 eslint 0 告警；vitest 83/83（新增 `blindLabel.test.ts` 12 条：未登记状态降级、null≠AI_ABSENT、禁区键数量/锚点断言防两份漂移、结论 JSON 结构校验）。**check.sh [10/59] 前端 ID 类型门禁曾判红 7 处**（`id`/`claimId` 误声明 number，雪花 Long→String 契约超 2^53 会被静默截断），已全部改 string 并复跑转绿（0 处）。全量 lint 失败项与门禁残留红项均为并行会话在制品（第 11 轮文档类名漂移 / R6 接线 / G40 幽灵菜单探针与种子未重生成），非本轮引入。E2E 流程扩展（派发→提交→解盲断言）留待并行会话 e2e 在制品合入后追加，避免同文件冲突。

Risks / Non-Goals 不变：7 个 BLOCKED 外部条件一个都没动，`l3Claimable` 保持 false。

## 第 12 轮循环复核增量（2026-11-07）

① **命令行自检补第四类探针**：`l3Claimable` 只回答「能不能宣称 L3」，`current_level()` 是另一处可假绿的出口。`--self-test` 新增 3 条档位探针（配置态全齐但探活失败 ⇒ L0；配置态全齐但无 E2E 产物 ⇒ L0；三条件齐备 + llm ⇒ L2 防恒假），并补 1 例「把档位退化成只看配置态时 `--self-test` 必须判红」的源码变异测试，证明探针不是恒真。

② **文档族一致性收口**：并发会话已把 `03-contract/第10轮循环复核-零DDL闭环落地.md` 提交入库但未登记 manifest，按第 10 轮处理 `review.round9` 的同一逻辑补 `review.round10` 条目与 `aiRouting` 路由，重跑生成器后六道机检全绿。三份文档去掉「5 条 / 8 例 / 10 条」这类会漂移的硬编码计数，统一为「四类探针、条数以脚本为准」。

Risks / Non-Goals 不变：7 个 BLOCKED 外部条件一个都没动，`l3Claimable` 保持 false。

## Risks

- 并发会话正在改 `bone-ai-document` 与另一 change；本设计零 Java 生产改动，只碰前端、Python 判据、E2E、两份文档，提交按路径式隔离。
- 窗口统一会改变 E2E 证据数值，属预期；D1 保证 `l3Claimable=false` 不被翻绿。
- 探活一次真实 upstream 调用，手动触发 + 零请求断言把成本限定在用户显式操作。
- 本设计交付「判据更严、口径更一致、可观测缺口补齐」，预期 `SATISFIED 5 / BLOCKED 7` 不变；任何把 BLOCKED 改小的改动都是假绿。

## Non-Goals

不改 7 个 BLOCKED 外部条件、不改 `l3Claimable`、不改 baseline 状态 / V15 默认值 / `application.yml` / go-live 分册 draft、不改决策 DAG、不改 DDL/CI。
