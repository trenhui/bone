# 提案：procuraAI 安全治理（批次3：F3 kill switch / F4 双标识 / G15 公平）

## Why

批次1 已交付 AI 决策治理横切模块（能力三态 fail-closed / 人工终决 / override 回流），但 D3-v2 §3.1 的 C-4「全域 kill switch」、C-1/E-5「生成内容双标识」、§3「算法公平与反歧视」仍是契约态零实现。蓝图 F3/F4 与 G15 把它们列为 AI 能力上线的前置地基。本批次落地这三块的**纯工程机制与确定性护栏**，不触碰模型效果数字、法务定性与 owner 通过线。

## What Changes

- **F3 全域 kill switch**：`GlobalKillSwitch` 三态（STANDBY/ENGAGED/DISENGAGED），缺省全停、读库即时生效、一键停用 + 演练编号审计、仅显式解除才恢复；新增 `globalAllowsAi()` 总闸与 `drillStatus()` 演练快照（覆盖各能力，验证无残留 AI 路径）。
- **F4 生成内容双标识**：`ContentLabel` 承载 EXPLICIT（显式文案）+ IMPLICIT（隐式元数据），缺一禁止出域（LABEL-01 `missingImplicitLabel_blocksDelivery`）；不因 aiGenerated=false 自报豁免。仅落实技术载体与阻断动作，**不代替法务对《标识办法》主体适用性的定性**（C-1 标 voluntaryAdoption，转 backlog）。
- **G15 公平确定性护栏**：`FairnessRule` 按所有制/地域/规模受控特征 + AI 建议文本做确定性偏见检测，命中 fail-closed：FAIR-01 所有制偏见阻断模型版本晋升 + 伦理审查，FAIR-02 地域偏好转人工不发建议；特征 UNKNOWN 不压成无偏见。这是回归/调试护栏，**不是统计公平性度量**（后者需 ≥200 样本与 owner 通过线，转 backlog）。
- **端点与门禁**：6 个 REST 端点（读写权限分离），openapi 登记 + A10.9 豁免，不改 FULL_GATE_KEYS；FAIR-01/02 与 LABEL-01 的 testSymbol 在源码精确可达。

**非目标（明确不做）**：evalset 27→200（owner R-3，批次2 已转 backlog）、法务法源射程结论（D-08）、算法备案/安全评估材料包（C-2，owner）、第三方审计与季度阈值（C-5/F2，owner）、人月估工（owner）、生产 DDL 落库与 bone-ai-trace 接线（L3）。不伪造完成态、不伪造效果数字。

## Capabilities

### New Capabilities

- `procurement/ai-safety-governance`: procuraAI 安全治理的可观察行为——全域 kill switch 读库即时生效与停机演练、生成内容双标识缺一阻断出域、G15 公平确定性裁决与模型晋升阻断。

### Modified Capabilities

- （无。扩展既有 governance 模块代码，但不改批次1 已归档的 `procurement/ai-decision-governance` spec 级需求。）
