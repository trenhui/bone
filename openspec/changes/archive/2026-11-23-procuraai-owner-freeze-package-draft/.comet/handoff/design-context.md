# Comet Design Handoff

- Change: procuraai-owner-freeze-package-draft
- Phase: design
- Mode: compact
- Context hash: 75f4ef758806dbc6f8edc374713b75ad985c46b1ab8cda03c96946d933a91e70

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-owner-freeze-package-draft/proposal.md

- Source: openspec/changes/procuraai-owner-freeze-package-draft/proposal.md
- Lines: 1-30
- SHA256: 8e3788a287df895f69f26792755c15da7b74742615b0ca4f70c3ceba1e7ad2d4

```md
# Proposal

## Why

procuraAI 方案里仍有一批**只能由 owner / 法务 / 监管拍板**的开口项，散落在 `decisions/DECISION-REGISTER.md` 的 D-01…D-18、`eval/ai-decision-evalset.json` 的 `scalePlan`、以及多份 ADR 中。这些项有一个共同特征：AI 可以准备好材料，但**绝不能代替 owner 签署、冻结契约、出具法务结论或宣称模型效果达标**。当前缺一份把这些材料集中、按统一格式（Draft / owner / 验收判据 / 未闭合风险）组织的可交付待签清单，导致 owner 无从逐项拍板，也容易被误当成"已冻结"。

## What Changes

- **新增 owner 冻结包草案**：在 change 目录产出一份 `owner-freeze-package-draft.md`，按四类组织——① 契约冻结评审清单（D3-v2 / LLD / OpenAPI / AsyncAPI 差异表）；② 评测扩容计划（27→200 标注、Cohen's Kappa ≥ 0.8、通过线冻结）；③ 法务书面结论输入清单（六条红线 + 主体适用性 + 场景射程）；④ SLO / 会签 / ACL / 立项（D-01…D-18 逐项）。
- **统一待签格式**：每一项 MUST 显式标注 `Draft / 待 owner 批准`、owner（未指派就写"未指派"，不编造）、可机器验证的验收判据、未闭合风险、真源锚点与待裁决动作六个字段。
- **状态诚实护栏**：草案 MUST NOT 把 `evalset.status=seededDraft` 改为 `frozen`，MUST NOT 伪造 owner 签名 / 法务结论 / 模型效果达标，MUST NOT 直接冻结契约或扩大 CI 判据集合。
- **主门禁保持通过**：新增草案文档须通过现有 frontmatter / 门禁扫描，措辞避开 A10.16 黑名单精确串，不触发任何判据集合变化。

## Capabilities

### New Capabilities

- `procurement/owner-freeze-package`：文档治理能力，产出 owner 待签冻结包草案；不新增运行时接口、事件、权限点或数据库对象。

### Modified Capabilities

（无。既有 capability 边界、事件 payload、评测集状态、契约冻结状态均不修改。）

## Impact

- **文档**：新增 `openspec/changes/procuraai-owner-freeze-package-draft/owner-freeze-package-draft.md`（核心交付物）；新增本 proposal / design / spec delta / tasks。
- **代码**：无。
- **DDL / CI / 门禁判据集合**：无。不接 CI 三入口、不改 `FULL_GATE_KEYS`、不改任何门禁常量。
- **评测集 / 契约**：不改 `eval/ai-decision-evalset.json` 的 `status`（保持 `seededDraft`）、不改 D3-v2 / OpenAPI / AsyncAPI 的冻结状态。
- **范围外（绝不越界）**：不伪造 owner 签名、法务书面结论、模型效果达标声明；不执行生产数据库操作；不冻结契约；不扩大 CI 判据集合。

```

## openspec/changes/procuraai-owner-freeze-package-draft/design.md

- Source: openspec/changes/procuraai-owner-freeze-package-draft/design.md
- Lines: 1-98
- SHA256: 1b0a4301a25ed8701233b7f103f427433cc73248a51fe5e16d0f828c0e3bfcb9

[TRUNCATED]

```md
---
comet_change: procuraai-owner-freeze-package-draft
role: technical-design
canonical_spec: openspec
---

# Design

## Context

当前 procuraAI 的工程侧已把人工终决身份绑定、ContractReviewed 持久化事件闭环等可机器验证能力落地；剩余事项主要是 owner / 法务 / 架构评审才能拍板的开口项。事实源分散在 `DECISION-REGISTER.md` 的 D-01…D-18、`eval/ai-decision-evalset.json` 的 `scalePlan`、D3-v2 的 §1.1/§2/§3、LLD 六卷与 `detailed-design/contracts/` 的 OpenAPI/AsyncAPI 工件。任何把这些材料“顺手改成已冻结 / 已达标”的写法都会污染真源状态。

本设计只回答如何形成待签草案，不替代业务裁决；动机与范围见 proposal.md，行为约束见 `specs/procurement/owner-freeze-package/spec.md`。

## Goals / Non-Goals

**Goals:**

- 产出一份可逐项签核的 `owner-freeze-package-draft.md`，覆盖契约冻结、评测扩容、法务输入和 D-01…D-18。
- 每项以六字段表达：状态、owner、验收判据、未闭合风险、真源锚点、待裁决动作。
- 不触碰运行时代码、DDL、CI 三入口判据集合、评测集状态与契约冻结状态。
- 让本地确定性检查能发现缺项、伪造状态和判据集合漂移。

**Non-Goals:**

- 不冻结契约、不执行迁移、不接生产 MQ / DB、不新增权限点或事件。
- 不填写 owner 签名、法务书面结论、模型效果达标值或压测冻结值。
- 不改 `CHANGELOG.md`、README 当前态行和 DECISION-REGISTER 的历史快照。
- 不把本 change 的文档检查接入 `ci.yml` / `check.sh` / `check-procura-ci.sh`。

## Decisions

### 决策 1：单一草案文件，不分散写入现有真源

在 change 目录写一份 `owner-freeze-package-draft.md`，OpenSpec delta 只定义“草案应满足什么行为”。不把待签内容写进 README、`DECISION-REGISTER.md` 或 D3-v2 正文。

理由：README 与决策登记表包含当前态锚点和历史快照，A10.16/A10.17 对措辞与三入口判据集合敏感；分散写入会增加假红和状态漂移风险。单一草案便于 owner 一次审阅、便于归档后追溯，也避免新增文档进入 `doc/ai-product/procuraAI` frontmatter 守护集合。

替代方案：① 直接改 DECISION-REGISTER → 否定，历史状态与用户授权不能伪造；② 新建产品族真源文档 → 否定，会扩大资产登记与 frontmatter 守护面；③ 只在 CHANGELOG 追加 → 否定，历史轮次不是当前真源。

### 决策 2：统一六字段待签项模板

每项材料固定包含：

1. `状态`：固定 `Draft / 待 owner 批准`；
2. `owner`：未指派写“未指派”；
3. `验收判据`：优先写可执行检查、可定位工件字段或签署动作；
4. `未闭合风险`：明确仍依赖谁、缺什么证据；
5. `真源锚点`：`path:line` 或稳定章节 / JSON 字段；
6. `待裁决动作`：owner / 法务需要填写或批准的具体内容。

理由：这把“建议、状态、责任、证据”分离，避免把工程建议误读为授权结论；同时可直接由本地脚本检查。

替代方案：表格只写“建议 + 状态” → 否定，无法回答谁签、怎么验收、缺什么风险。

### 决策 3：按四类材料分组，D-01…D-18 另设全量索引

正文分四节：契约冻结评审、评测扩容计划、法务书面结论输入、D-01…D-18 开口项。D-01…D-18 不重复展开长篇论证，只做编号级索引并回指前三类材料或原登记表。

理由：owner 通常按议题审阅，但收尾时需要确认“18 项没漏”。分组 + 索引兼顾可读性和机器可验性。

### 决策 4：真源引用采用裁决链，不新增并行真源

引用顺序沿用 `文档资产登记与真源裁决.md` 的八级裁决链：D3-v2 与 LLD 机读工件优先于旧族；DECISION-REGISTER 只作状态索引，不下业务裁决；evalset 的 `scalePlan` 是扩容计划真源，但状态字段仍保持 `seededDraft` / `notStarted`。

理由：避免草案自身成为第四真源；出现冲突时草案只列差异和待裁决动作，不自行收口。

### 决策 5：本地复核而非新增 CI 判据

用一次性本地确定性检查验证：四类材料存在、18 个编号齐全、状态字段未被改写、evalset 关键字段未变、三入口判据常量不变。检查结果写入 Verify 报告，但不新增 `FULL_GATE_KEYS`、不新增 blocking step。

理由：A10.17 / D-11 已把三入口判据集合锁死；新增判据必须同步四入口与注入反证，属于另一项 L3 change。本 change 的证据需求可由本地检查满足。

### 决策 6：状态诚实失败即 red

任何以下变化都视为失败：把 eval set 改成 `frozen` 或 `caseCount=200`；写入具体 owner / 法务签名；把契约差异写成 Frozen；把高后果召回率或 override 率写成数值；修改 CI 三入口判据常量。

理由：这些是本批 change 明确非目标，也是 owner 授权边界。

## Risks / Trade-offs

```

Full source: openspec/changes/procuraai-owner-freeze-package-draft/design.md

## openspec/changes/procuraai-owner-freeze-package-draft/tasks.md

- Source: openspec/changes/procuraai-owner-freeze-package-draft/tasks.md
- Lines: 1-30
- SHA256: 92f5be540d9c508d582bed0d559028a452461ffea5f225e3f06dc091104c0e50

```md
# Tasks

## 1. 草案骨架与状态护栏

- [x] 1.1 在 change 目录创建 `owner-freeze-package-draft.md`，写清文档性质、六字段模板、四类材料目录及“不改真源状态”护栏；用本地文本检查确认四个章节标题与 `Draft / 待 owner 批准` 标记存在。 <!-- comet-task:90ef3225-a9c4-426c-9df2-e24071aaaa59 -->
- [x] 1.2 固化状态诚实断言：`evalset.status=seededDraft`、`scalePlan.status=notStarted`、`caseCount=27`、契约冻结状态不变、owner/法务/模型效果结论均为待填；用 JSON 字段读取与草案关键字检查验证没有伪造状态。 <!-- comet-task:1201dba3-e24b-44de-bc12-bdb12a0a9035 -->

## 2. 四类 owner 待签材料

- [x] 2.1 编写契约冻结评审清单，按 D3-v2 / LLD 六卷 / `contracts/openapi.yaml` / `contracts/asyncapi.yaml` 列差异、影响能力码、真源裁决链位置与待裁决动作；用每行含真源锚点、owner、验收判据、未闭合风险的本地检查验证。 <!-- comet-task:e4764cc9-af31-4450-b598-5ee9d15ee563 -->
- [x] 2.2 编写评测扩容计划，覆盖 27→200、P0/P1/P2、双人标注、第三方仲裁、Kappa ≥ 0.8、训练/验证/冻结三分离与通过线待冻结字段；用 JSON 与草案对照检查确认新增数值未被写成已达标。 <!-- comet-task:98eefb7d-808c-4874-aebf-f4d2836e023b -->
- [x] 2.3 编写法务书面结论输入清单，覆盖六条不可稀释红线、主体适用性、场景射程及 `D3-v2 §3.1` C-2 按客户属性叠加算法备案 / 安全评估材料包条件；用六条红线编号与 C-2 关键字检查验证无缺项。 <!-- comet-task:d5f5f38d-5a2a-431c-8989-e8ed8792f5ab -->

## 3. D-01 至 D-18 全量索引

- [x] 3.1 编写 D-01…D-18 逐项索引，每项给出当前状态、owner、验收判据、未闭合风险、真源锚点和待裁决动作，并回指前三类材料；用正则统计确认 18 个编号齐全且各出现一次。 <!-- comet-task:2ad3f9e3-5f9e-477f-90c2-5550738527c3 -->

## 4. 本地确定性与既有门禁复核

- [x] 4.1 执行一次性本地确定性检查，覆盖四类材料齐全、18 项编号齐全、状态诚实字段未改写、CI 三入口判据常量未变，并做删除 D-15 / 篡改 caseCount 的注入反证；用“正例通过、注入转红、还原通过”三类输出作为完成证据。 <!-- comet-task:3211e82b-4c4e-47ee-8c47-22c0745404fe -->
- [x] 4.2 真实运行 procuraAI 对齐、evalset、AsyncAPI、frontmatter、persistence、event-chain 与迁移清单既有门禁，证明本 change 未引入文档或契约回归；用各命令 PASS / 零漂移输出作为完成证据。 <!-- comet-task:ed0ef9a0-0b2c-4fd7-b2ca-97af6a0c7426 -->

## 5. 验收与交付准备

- [ ] 5.1 完成唯一最终集成审查，重点检查草案未伪造 owner/法务/模型结论、未改 CI 判据集合、未改真源状态，并生成 verify 报告；用审查结论“0 CRITICAL / 0 IMPORTANT”和报告落盘作为完成证据。 <!-- comet-task:1e9943d1-10d8-4ddc-9911-f7a0811807a2 -->

## Workflow follow-up

- 完成 Comet Verify 的唯一最终集成审查后再归档。
- 归档交付方式沿用用户已给出的“提交并推送 origin/dev”授权；精确提交当前 change 文件，排除他人并行在途 change。

```

## openspec/changes/procuraai-owner-freeze-package-draft/specs/procurement/owner-freeze-package/spec.md

- Source: openspec/changes/procuraai-owner-freeze-package-draft/specs/procurement/owner-freeze-package/spec.md
- Lines: 1-103
- SHA256: 6f1db1966227c90f13c41e03463467c7403c43f60441bc56cbd4783a9b72ec0e

[TRUNCATED]

```md
# Spec Delta

## Purpose

为 procuraAI 的 owner、法务与架构评审提供可逐项签核的冻结包草案，统一记录待决项、真源锚点、验收判据和未闭合风险，并在 owner 签署前保持评测与契约状态诚实。

## ADDED Requirements

### Requirement: 冻结包草案覆盖四类评审材料

冻结包草案 SHALL 同时提供契约冻结评审清单、评测扩容计划、法务书面结论输入清单和 D-01…D-18 开口项索引；每类材料 MUST 指向可定位的仓库真源或明确标记“待补真源”。

#### Scenario: owner 打开草案即可逐类评审
- **WHEN** owner 打开冻结包草案
- **THEN** 四类评审材料均存在且各自列出真源路径或“待补真源”标记

#### Scenario: 缺失真源不得冒充已完成
- **WHEN** 某项材料找不到可定位真源
- **THEN** 该项被标记为“待补真源”，且不得被表述为已通过评审或已冻结

### Requirement: 每项待签材料使用统一字段

每一项待签材料 MUST 至少包含状态“Draft / 待 owner 批准”、owner（未指派写“未指派”）、可机器验证或可人工复核的验收判据、未闭合风险、真源锚点、待裁决动作六个字段；字段缺失时该项 MUST 被视为未完成。

#### Scenario: 单项材料具备签核所需的全部字段
- **WHEN** owner 复核任一待签项
- **THEN** 该项同时显示状态、owner、验收判据、未闭合风险、真源锚点和待裁决动作

#### Scenario: 未指派 owner 不编造姓名
- **WHEN** 真源未给出具体 owner
- **THEN** 该项 owner 字段写“未指派”，不得使用部门名、角色名或人名代替签署人

### Requirement: 状态诚实采用 fail-closed

草案 MUST NOT 修改 `eval/ai-decision-evalset.json` 的 `status`、`needsOwnerReview`、`scalePlan.status`、`caseCount`，MUST NOT 修改契约工件冻结状态，MUST NOT 写入 owner 签名、法务书面结论或模型效果达标声明；无法确认的状态 MUST 保持 Draft。

#### Scenario: 评测集仍为种子草稿
- **WHEN** 冻结包草案被生成或归档
- **THEN** `evalset.status` 仍为 `seededDraft`，`scalePlan.status` 仍为 `notStarted`，`caseCount` 仍为 27

#### Scenario: 未授权结论不得出现
- **WHEN** 草案需要填写 owner、法务或模型效果结论
- **THEN** 字段保持“待 owner 批准 / 待法务结论 / 待评测达标”，不得出现伪造签名或达标承诺

### Requirement: 契约冻结评审矩阵可追溯

契约冻结评审清单 SHALL 以 D3-v2、LLD 六卷、`contracts/openapi.yaml`、`contracts/asyncapi.yaml` 为对照面，列出差异项、影响能力码、真源裁决链位置和待 owner 裁决动作；草案 MUST NOT 把差异项表述为已冻结。

#### Scenario: reviewer 追踪一处契约差异
- **WHEN** reviewer 查看任一差异项
- **THEN** 能定位到两侧工件路径或章节、裁决链口径和明确待裁决动作

#### Scenario: 差异项不冒充冻结结论
- **WHEN** 某项契约差异尚未获架构评审批准
- **THEN** 该项状态仍为“Draft / 待 owner 批准”，冻结状态不得改为 Frozen

### Requirement: 评测扩容计划可执行但不冒充达标

评测扩容计划 SHALL 描述 27→200 样本扩容、双人标注、第三方仲裁、Cohen's Kappa ≥ 0.8、训练/验证/冻结集三分离和通过线字段；高后果召回率、override 率等数值 MUST 保持“待 owner 冻结”，不得伪造达标数值。

#### Scenario: owner 审查扩容退出条件
- **WHEN** owner 查看 P1-expand 阶段
- **THEN** 计划列出 200 样本、Kappa ≥ 0.8、三分离和字段级通过线待冻结项

#### Scenario: 数值未冻结不得写成通过
- **WHEN** 高后果召回率或 override 率缺少 owner 冻结值
- **THEN** 对应字段写“待 owner 冻结”，且草案不得宣称模型效果达标

### Requirement: 法务输入清单覆盖不可稀释红线

法务书面结论输入清单 SHALL 覆盖六条不可稀释红线（AI 不碰钱、授标归人、结案归人、三态 fail-closed、可解释与留痕、公平护栏）、主体适用性、场景射程，以及 `D3-v2 §3.1` C-2 按客户属性叠加算法备案 / 安全评估材料包的适用条件；清单 MUST NOT 代替法务结论。

#### Scenario: 法务 reviewer 找到六条红线
- **WHEN** 法务 reviewer 打开清单
- **THEN** 六条红线逐条可见，且主体适用性与场景射程分别列为待结论项

#### Scenario: C-2 条件不被一笔带过
- **WHEN** 清单涉及央国企客户或依法必须招标项目
- **THEN** 明确提示须由法务裁决是否叠加算法备案与安全评估材料包


```

Full source: openspec/changes/procuraai-owner-freeze-package-draft/specs/procurement/owner-freeze-package/spec.md
