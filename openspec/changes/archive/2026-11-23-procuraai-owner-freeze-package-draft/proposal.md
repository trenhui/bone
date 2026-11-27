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
