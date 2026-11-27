---
comet_change: procuraai-owner-freeze-package-draft
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-23-procuraai-owner-freeze-package-draft
status: final
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

- [风险] 草案放在 change 目录，owner 可能在产品文档树中找不到 → 缓解：Verify 报告和最终交付说明中给出明确路径；不修改 README 锚点。
- [风险] 评审材料内容庞杂，owner 难以一眼判断缺项 → 缓解：六字段模板 + D-01…D-18 索引 + 本地缺项检查。
- [风险] 引用 `file:line` 会随后续编辑漂移 → 缓解：同时写章节名 / JSON 字段名；Verify 阶段重新核对关键锚点。
- [风险] 本地检查不进 CI，未来可能被忽略 → 缓解：本 change 明确定位为 owner 待签材料，不是发布门禁；后续冻结单独立项。
- [风险] D-06 / D-11 已 Landed，D-18 部分工程已落地，容易被误写成 owner 已签 → 缓解：逐项保留“用户授权本轮落地、owner 签署未伪造”的措辞。

## Migration Plan

1. 生成草案与 OpenSpec 产物，保持所有真源文件只读。
2. 本地检查草案完整性、状态诚实与三入口判据常量未变。
3. Verify 阶段真实运行 procuraAI 对齐 / evalset / AsyncAPI / frontmatter / event-chain 等既有门禁，证明无回归。
4. Archive 时只提交当前 change 的草案与 OpenSpec 元数据。
5. 后续由 owner 逐项填写签名、压测链接、法务结论与通过线；任何状态迁移必须另开 change，并重新走门禁与冻结流程。

## Open Questions

无。owner、法务和模型效果待填内容已作为草案字段保留，不属于可由本 change 自行决定的未知项。
