# procuraAI 未实现能力诊断与优化建议（2026-11-21）

> 性质：诊断分析，不是实施变更。本轮只读源码 / 文档 / 门禁，未改产品代码、未提交、未推送、未执行生产库操作。
> 证据口径：先源码实证，再参考既有报告；关键结论给出 `file:line`。`已落地` 只代表工程载体/测试存在，不等于 AI 效果达标或生产可上线。
> Comet 说明：本请求被 Runtime 路由为 `out_of_scope`（诊断/提问，不启动或恢复 change）。写入前仅核销了上一会话遗留的未知检查动作（证据：检查记录缺失、历史 1–9 次均 `not-executed`、无存活进程），未推进任何 change 阶段。

## 0. 执行摘要

**结论：procuraAI 不是“零实现”，但离“AI 价值能力可用”还差一层 owner 前置与一层工程闭环。**

- 已落地：文档族与门禁体系、9 个 Maven 模块骨架、21 张 `t_proc_*` 表、9 个业务/治理端口的 SDK 持久化、合同 AI 审查与治理的 fail-closed 代码链、4 个前端路由。
- 未落地：真实模型效果达标（除 M2 有 LLM 网关入口外，其余 7 个能力码只有 `rules_only` 建议态）、评测转正与 200 例样本、D3-v2/LLD 契约冻结、生产 MQ/Outbox/Inbox、跨域真实 ACL、完整业务聚合（Award/Bid/Quotation/CAPA、OCR 人工复核）、HITL 工作台、G1–G16/F1–F4 能力卡片。
- 最优先的不是“再写更多功能”，而是三件事：① 默认配置下的装配/可达性断点；② owner 必须先冻结契约与评测通过线；③ 选一条跨单元事件闭环做真实端到端，而不是继续堆静态门禁。

关键数字：主门禁 **10/10**、注入探针 **154/154**、评测集 **27 例 `seededDraft`（目标 200）**、采购表 **21 张**、模块测试 **368 例**、前端路由 **4 个**、AI 能力码 **8 个（仅 M2 走 LLM 网关）**、生产 MQ/Outbox **0**。

---

## 1. 实现现状分层

| 层 | 已经落地（有证据） | 仍未落地 / 不能等同上线 |
|---|---|---|
| 文档与门禁 | 真源裁决链、A10 主门禁、注入式负向探针、三入口一致性；`README.md:129-135`、`ci.yml:133-163` | 门禁只守结构 / fail-closed 不变量，`一期边界与机制触发条件.md:99-103` 明确「CI 绿 ≠ 模型效果 / 采购模块落地」 |
| 数据持久化 | 21 张表（`bone-init.sql:4495-4980`），9 端口切 SDK 仓储（`tools/check_procura_persistence.py:20-39`），双唯一键与幂等（`ContractReviewApplicationService.java:63-111`） | 真实 MySQL DDL 未执行；静态 DDL 对账 ≠ 生产库可迁移；无 Outbox/Inbox 表 |
| AI 合规骨架 | 三态 fail-closed、双标识、PII 拒绝、总闸即时生效（`SafetyApplicationService.java:46-142`、`ContractReviewPortAdapter.java:31-38,79-105`） | F1–F4 地基层仍未验收（`采购AI原生能力蓝图.md:289-295`）；kill switch 演练 / 季度公平性复跑 / 双标识合规包缺运行证据 |
| 业务能力 | M2 合同审查有 LLM 网关入口 + HITL 路由（`ContractReviewApplicationService.java:63-112`）；寻源/准入/风险三模块骨架 + 人工终决（`生产化加固 proposal.md:9-12`） | 其余 7 码仅建议态（`AdvisoryApplicationService.java:13-16,29-55,57-71`）；G1–G16、N1–N8 全部 Proposal（`采购AI原生能力蓝图.md:232-233,253-292`） |
| 模型与评测 | 27 例 seed 覆盖 8 码、18 负向（`eval/ai-decision-evalset.json:4,923-952`） | `status=seededDraft`、`scalePlan.status=notStarted`；校验器自己声明「代码未落地 / 不守模型效果」（本轮实跑 `[SEED DRAFT]` 输出）；未冻结 precision/recall/override 通过线 |
| 跨域与事件 | Finance/Inventory ACL 有 fail-closed 桩（`FailClosedAclAdapters.java:9-30`） | 真实 Finance/Inventory 交付未闭合（`DECISION-REGISTER.md:48` D-05）；`ContractReviewed` 仅进程内直连（`ContractReviewApplicationService.java:156-160`）；无 MQ producer/listener/Outbox |
| 前端 | `/transactions`、`/suppliers`、`/fulfillment`、`/contract-review`（`App.tsx:26-32`） | 无治理控制台、AI 建议裁决页、HITL 确认工作台、寻源/准入/风险页面 |

---

## 2. 未实现能力清单（按严重度）

### P0 — 阻断“可用 / 可上线”的六项

| # | 缺口 | 证据 | 影响 | 当前状态 | 归属 |
|---|---|---|---|---|---|
| P0-1 | 默认配置下 AI 能力不可达，且存在装配断点：交易/履约默认 false，合同审查与治理为 `havingValue=true` 缺省关闭；但 `AdvisoryController` / `AdvisoryApplicationService` 无条件装配并强依赖两个条件 Bean | `application.yml:29-33`；`ConditionalOnAiGovernanceEnabled.java:10-17`；`AiGovernanceApplicationService.java:32`；`SafetyApplicationService.java:25-26`；`AdvisoryController.java:12-20`；`AdvisoryApplicationService.java:17-27` | 未显式开启治理时建议态端点/服务无依赖 Bean；即便开启，默认也未开启合同 AI 与交易/履约 | 源码静态证据明确；本轮未启动应用复现，建议先补启动探针 | 工程侧可修；配置基线需架构师确认 |
| P0-2 | 即使开启合同 AI，默认网关是确定性 Mock：未配置 `bone.ai.gateway.base-url` 时自动注入 `MockLlmClient`，输出 `mock:true` 回显；合同审查适配器只做 PII/JSON 校验，未显式拒绝 mock 身份 | `LlmGatewayAutoConfiguration.java:140-149`；`MockLlmClient.java:13-23,31-55`；`ContractReviewPortAdapter.java:79-105` | 生产配置漏配 base-url 时可能出现“看似成功、实则无模型”的 AVAILABLE 建议；与评测转正前不得声明效果的设计冲突 | 源码直接证据；需确认契约是否允许 `AVAILABLE` | 工程侧：入口显式识别 mock 并降级 UNKNOWN；配置基线补 provider |
| P0-3 | 契约未冻结：D3-v2 / LLD / OpenAPI / AsyncAPI 仍 Proposal，禁写为承诺 SLA | `D3-v2.md:1-16,39-58`；`README.md:108`；`开发基线与开发就绪清单.md:23-33,46-59` | 继续扩 Gx 会放大需求漂移；能力码与端点登记无法判定越界 | D-09/D-16/D-14 均 `Proposed`（`DECISION-REGISTER.md:54,59,61`） | owner / 架构评审，不可由 AI 代签 |
| P0-4 | 评测集未转正且溯源断裂：27 seed、`seededDraft`、`notStarted`；8 个 `testClass` 在源码中不存在，校验器只检查字段存在与 `testSymbol` 唯一，不检查测试类可达 | `eval/ai-decision-evalset.json:4,20-795,923-952`；`check_procura_evalset.py:43-56,86-90,463-477`；本轮 `find` 实证：`ProcuraThreeWayMatchTest` / `ProcuraSupplierRiskTest` / `ProcuraAnomalyDetectionTest` / `ProcuraReconciliationTest` / `ProcuraAlgorithmicFairnessTest` / `ProcuraContentLabelingTest` / `ProcuraCapabilityCoverageTest` / `ProcuraPositiveControlTest` 均 0 命中 | CI 绿只证明结构，不能声明任何 AI 能力达标；`groundTruthRef/testClass/testSymbol` 的“可追溯”是假的 | 源码 + 实跑门禁确认 | 工程侧补源码可达性门禁；owner 做标注/通过线 |
| P0-5 | 跨域 ACL 与事件闭环未接通：Finance/Inventory 为 fail-closed 桩；`ContractReviewed` 只是进程内直连且结论存在 `ConcurrentHashMap`；无 Outbox/Inbox/生产 MQ | `FailClosedAclAdapters.java:9-30`；`ContractReviewedConsumer.java:17-30`；`ContractReviewApplicationService.java:156-160`；`bone-init.sql` 无 `t_proc_outbox`；`一期边界与机制触发条件.md:40-53` | 三单匹配、对账、跨单元审计无法端到端；不能把直连当生产 MQ | D-05 `Accepted + 外部依赖`；T2/T3 一期启用但工程未落 | owner 对接 + 工程 L3（DDL/CI） |
| P0-6 | 对外能力口径失准风险：8 个能力码有 2 个 REST 端点即可返回 `AVAILABLE`，但该服务不注入模型、不产生输出、不声明效果 | `AdvisoryCapability.java:11-19`；`AdvisoryApplicationService.java:29-55`；`AdvisoryController.java:23-37` | 调用方可能把「建议态/规则降级」误读成已上线 AI 能力 | 工程骨架按设计刻意 fail-closed；缺的是面向运行时的能力就绪度公示 | 工程侧可补 `/ai-readiness` 类只读端点 + 文档口径 |

### P1 — 价值闭环与业务完整度

| # | 缺口 | 证据 | 影响 | 归属 |
|---|---|---|---|---|
| P1-1 | 7/8 AI 能力码只有建议态；M1/M3/M4/M5/M6/M7/ORCHESTRATION 无领域算法与评测 | `AdvisoryApplicationService.java:13-16,29-55`；`采购AI原生能力蓝图.md:274-288` | 文档里的“本期接入 A 档”与工程现实不一致 | owner 先立项/冻结，再工程切片 |
| P1-2 | 业务聚合缺口：Award/Bid/Quotation/CAPA 未建；寻源仅 2 端点、准入 3、风险 2 | `SourcingController.java:26,43`；`QualificationController.java:33,47,53`；`RiskController.java:28,43`；`business-module-skeleton/proposal.md:15` | 02/03 卷 LLD 只实现最小骨架，无法覆盖完整 S2P | 工程侧按 change 分批 |
| P1-3 | OCR 人工复核方法直接抛异常：`reviewOcrByHuman` 仍是 `UnsupportedOperationException` | `QualificationApplicationService.java:35-37` | 证照 OCR 人工终决链路不可用（准入高风险点） | 工程侧：补持久化读回 + 具名复核 + 负向测试 |
| P1-4 | 前端仅 4 路由，无 HITL 确认工作台（N6）/治理控制台/建议态页面 | `App.tsx:26-32`；`开发基线与开发就绪清单.md:81-82` | 100% 人工终决缺少可操作 UI，ROI（`T_human_confirm`）无法实测 | 工程侧 + 产品 owner |
| P1-5 | 门禁覆盖仍偏静态：`check_procura_persistence.py` 只查 DDL/列/唯一键/端口实现类名，不查启动、真实库迁移、MQ、评测结果、UI 路径；evalset 也不查 `testClass` 源码可达 | `tools/check_procura_persistence.py:20-39,69-104`；`check_procura_evalset.py:86-90,463-477`；`ci.yml:133-163` | 可能出现“门禁全绿但应用起不来/能力不可用/追不到测试” | 工程侧：新增启动探针 + 源码可达门禁；按 A10.17 同步三入口 |

### P2 — 治理债与文档债

| # | 缺口 | 证据 | 建议 |
|---|---|---|---|
| P2-1 | 当前态数字陈旧：一键脚本仍写「六条 blocking / 7 判据」，导航与资产登记仍写 frontmatter「不在 CI 三入口判据集合内」；实际已是 10 key / 8 blocking step | `check-procura-ci.sh:4-15`；`ci.yml:133-163`；`llms.txt:55`；`文档资产登记与真源裁决.md:94,96`；`AGENTS.md:66` | 改为「A10.17 三入口判据集合一致」口径，避开 A10.16 精确黑名单串；保留 README 锚点与 `gateEvidenceManifest` 单行 |
| P2-2 | 旧 POC 族物理迁 `archive/` 与 A10 反向守护语料冲突 | `DECISION-REGISTER.md:57,69` D-12 | 保持不迁移；如确需收敛，先由 owner 裁决 A10 语料替代方案 |
| P2-3 | CI 供应链 CIS-2/3/4/5、Scorecard 未落地 | `DECISION-REGISTER.md:58,71` D-13 | owner 提供官方 SHA 后按 L3 流程接入，禁止 fork 来源与伪造 SHA |
| P2-4 | SLO/容量/ATAM 排序未冻结，仍为草案 | `DECISION-REGISTER.md:46,49` D-01/D-04；`一期边界与机制触发条件.md:36` | 压测后冻结，未冻结前不得接入告警/SLA 门禁 |

---

## 3. 优化建议（推荐执行顺序）

### 3.1 P0：先让“已有代码”真正可达、可判定

1. **补启动与能力可达性探针（工程侧，优先级最高）**
   - 用 `ApplicationContextRunner` 或 `@SpringBootTest(webEnvironment=NONE)` 在默认配置下断言：`AdvisoryController` 的依赖必须存在；否则让 advisory 服务/控制器也受同一个 `@ConditionalOnAiGovernanceEnabled`（或在 `application.yml` 显式开启治理并写明理由）。
   - 同时断言交易/履约/合同审查/治理四类开关的双态：关闭时端点不注册，开启时 Bean 唯一。现有 `ProcurementCapabilityToggleTest.java:20-47` 只覆盖交易/履约，未覆盖 AI 模块。
   - 新增只读就绪度接口（建议 `GET /api/v1/procurement/ai-readiness`）：从配置、能力注册、总闸、评测状态四类事实推导 `AVAILABLE / DEGRADED / UNKNOWN`，**明确 `deploymentGatesPassed` 不等于放款/授标许可**。

2. **把 provider 身份纳入合同 AI 的参与态裁决**
   - 在 `ContractReviewPortAdapter.review()` 解析前检查响应是否含 `mock:true`；命中即走 `degrade()`（UNKNOWN + 强制人工），不允许 Mock 身份进入 `AVAILABLE`。
   - 在 readiness 与启动日志中显式提示 `bone.ai.gateway.base-url` 未配置；不要静默 fallback。

3. **把 owner 前置收敛成一个“冻结包”，不要散着催**
   - 同一次评审里闭合 D3-v2 / LLD / OpenAPI / AsyncAPI 版本戳（D-09、D-16）、评测转正门槛（D-15/D-10）、法务结论（D-08）、真实模型接入前置（D-17）。
   - 未闭合前继续坚持：不接生产模型、不声明效果、不新增 Gx 卡片、不把 `seededDraft` 改成通过态。

4. **选一条跨单元事件做真闭环，而不是全量铺开**
   - 推荐从 `ContractReviewed` 开始：`t_proc_outbox` → 中继 → 幂等 Inbox → 消费台账；保留现有进程内直连作为测试夹具，生产路径切换由配置控制。
   - 只做这一条，配“重复投递幂等 / 投递失败入 DLQ / 租户错投拒绝”三类负向探针；满足 `一期边界与机制触发条件.md:41-43` 的 T2/T3 定义后再扩。

### 3.2 P1：按价值路径补能力，不按文档卷数铺开

推荐顺序：**M2 深化 → M1/M4（挂 G15 公平护栏）→ N6 HITL 工作台 → M3/M6/M7 → 完整业务聚合**。

- M2 已有 LLM 网关入口（`ContractReviewPortAdapter.java:79-105`），优先补真实样本回放、条款锚点一致性、模型版本与通过线证据；不要只补端点。
- M1/M4 涉及供应商机会，必须同时挂 G15 偏见用例与人工终决，禁 AI 授标/准入定性（`采购AI原生能力蓝图.md:51,279`）。
- N6 工作台是 100% 人工终决下 ROI 的真正载体（`开发基线与开发就绪清单.md:81-82`），前端优先于更多规则卡片。
- `reviewOcrByHuman`（`QualificationApplicationService.java:35-37`）是单点可修的高风险缺口，建议放入最近的工程 change。

### 3.3 P2：清文档债，但不破坏守护语料

- 更新陈旧计数时避开 A10.16 黑名单（`tools/check_procura_alignment.py:264-273`），统一说「A10.17 三入口判据集合一致」。
- 不动旧族 8 份文档、不动 8 个受保护锚点、不手改 AsyncAPI 派生物（`AGENTS.md:68-73`、`README.md:99-108`）。
- 任何新增门禁判据必须同时改 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` + 门禁常量 + 注入探针，否则 A10.17 转红。

---

## 4. 推荐 change 拆分（本次不启动）

| 建议 change | 范围 | 前置 | 验收要点 | 性质 |
|---|---|---|---|---|
| `procuraai-startup-readiness` | 默认装配探针、AI 条件开关一致性、Mock 身份降级、`/ai-readiness` 只读端点 | 无；可先做 | 默认配置启动不因 advisory 依赖失败；四类开关双态断言；Mock 不返回 AVAILABLE；就绪度不冒充放款许可 | full（新增公共端点） |
| `procuraai-eval-freeze` | 评测 27→200、标注一致性、通过线、`testClass` 源码可达门禁、D3-v2/LLD 冻结版 | owner 指派 + 法务/架构评审 | `seededDraft` 状态变更由 owner 裁决；8 个缺失测试类补齐或改指可达夹具；不得伪造数字 | full + owner 决策 |
| `procuraai-contract-reviewed-outbox` | 一条事件的 Outbox→Inbox→幂等消费 + DLQ | DDL/CI 属 L3 授权 | 重复投递幂等、租户错投拒绝、失败入 DLQ；三入口门禁同步 | full（L3） |
| `procuraai-hitl-console` | N6 确认工作台 + 治理控制台 + 建议态展示 | 就绪度端点 | 人工终决可操作、采纳/驳回回流、`T_human_confirm` 可测 | full |
| `procuraai-business-aggregates` | Bid/Award/Quotation/CAPA、OCR 人工复核 | 契约冻结后 | 领域不变量 + REST/DDL/负向探针三对齐 | full |

---

## 5. 验收原则（避免再次假绿）

1. 机器门禁绿只证明「结构、隔离、fail-closed、判据未退化」；上线判据是评测通过线 + F1–F4 验收 + 运行期端到端证据。
2. 新增判据必须做「注入必红 / 还原必绿」；禁止只加文本不加探针。
3. 前端 lint 必须解析 `-f json` 的 `errorCount`，不能看 stylish formatter 退出码。
4. 2xx 不等于落库：接口调用必须配 MySQL 直查或持久化读回测试作第二证据。
5. 本轮 P0-1 是源码静态强证据但**未做运行期复现**；P0-2/4/5、P1-3 已逐条读源码/实跑门禁确认；Gx/Fx/Nx 未实现以文档真源为准，代码侧仅 M2 有真实 LLM 网关入口。
6. 评测集 `testClass` 缺失由本轮文件系统实测确认：8 个类 0 命中，只有 `ProcuraPromptInjectionGuardTest` / `ProcuraAiDegradationTest` / `ProcuraAiDisclosureTest` / `ProcuraContractReviewTriageTest` 真实存在。

---

## 6. 当前态一句话

**已有：合规骨架、持久化、门禁与最小 AI 审查链路；未有：评测转正、契约冻结、真模型效果、跨域事件闭环、HITL 工作台、G1–G16 能力卡。** 建议先修装配/就绪度与 owner 冻结包，再按价值路径逐条补能力。
