## Why

M2 ① Expand（`0123` 加 `liability_rule_json` 列）与 ② Shadow（写侧影子 + 写入即回读比对）已落地并推送，但**读侧仍只有标量路径**：JSON 列存得进、比得对，却没有任何代码消费它。直接在 ③ 把读真源切到 JSON 是资金行为变更，一旦回落语义有偏差就是赔错钱，而当前没有任何证据能判断"能不能切"。因此先建立可回放、可按键回落、可双轨比对的读路径，并让开关默认关闭以保持零行为变更——**这份比对结果才是 ③ 后半段是否允许切主的唯一判据**。

同时，诊断报告交人清单里 `engine_version` 与 PITest 两项长期以"未做"悬空，缺少的是"为什么现在不能做、什么条件下做"的可判定结论，而不是又一次状态描述。本 change 一并按代码与制品事实收口，避免"配了不生效"与"接了等于没接的假绿门禁"。

## What Changes

- 新增 `claim/liability-rule-json-dual-read` 行为契约：读侧**按键回落** `resolve(key, scalar)`，JSON 缺该键时取对应旧标量列；禁止按整列判断（`json != null ? 全走 JSON : 全走标量`），否则只配一部分键时会静默丢掉其余已配值。
- 装配点保持唯一：`LiabilityRule.fromLegacy(...)` 增加接收 `LiabilityRuleJson` 的重载，不引入第二套算法；domain 层零框架依赖，回落逻辑落在领域值对象，读开关落在 application 并以参数传入。
- 新增读开关（**默认 false**）：关闭时逐笔走旧标量路径，行为与 ② 完全一致；打开时同时计算 (a) 旧列路径与 (b) 回落逻辑并逐笔比对，差异只记录、不阻断、不改变赔付结果。
- 补齐双轨比对与专属反例用例：历史行（JSON 为 NULL）逐笔等于迁移前、只配 1 个键时其余键仍取旧列、只配 1 个 B 类键时其余 B 类键不得被当作 0 或拒绝、A 类双空必拒、未知版本必拒、非法 JSON 字段级报错；删掉 JSON 分支的变异必须转红。
- 收口剩余 L3 项结论：`engine_version` 消费方检索为零，按"配了不生效"原则**不落实体与 DDL**，仅在台账登记触发条件；PITest 离线制品缺失，**不接线**，避免离线构建红或产生从未运行过的门禁，登记触发条件（CI 有网 + 路由开关生产启用评审前）。
- 更正 O-06 闸门绑定：把 §13 台账的「M2 ③ 开工前 5 个硬缺口须逐项验收」收窄为「与 mapping 有交集的配置面改造开工前」，理由与原始条文一并留痕；O-06 本身仍独立跟踪，不在本 change 内转正。依据是这 5 个硬缺口（双保单号、保司码表、外键锚点、软删唯一键、险种层）与 `liability_rule_json` 读路径零代码与数据交集，照字面停工属于误报，而误报会导致作者关掉整道门禁。
- 文档同步：`04-design/总体技术方案.md` §9.1.1 开工状态与 §13 台账、`04-design/方案质量诊断报告.md` 交人清单。

**明确不做（非目标）**：不把读开关默认改为 true（3b 切主）、不做 ④ Unlock/Retire、不改赔付金额、不做生产库迁移或生产启用、不回填压测实测数值、不动 O-06 的 mapping 结构性改造、不开放 6 个 B 类键的写入入口（消费方仍为零，记在 O-10）。

## Capabilities

### New Capabilities

- `claim/liability-rule-json-dual-read`: 责任规则 JSON 的按键回落语义、读开关默认关闭的零行为变更保证、双轨比对的等价判据与失败分类，以及 L3 阻塞项的可判定登记口径。

### Modified Capabilities

无。② Shadow 的写入期 Schema 已落地，本 change 不修改任何既有 capability 的需求；新增的是独立读路径契约。

## Impact

- **代码**：`bone-apps/synap-claim/synap-claim-app` 的 `domain/model/liability/LiabilityRuleJson.java`、`domain/model/adjustment/engine/LiabilityRule.java`、`domain/model/adjustment/Adjustment.java`（读参数传递）、`application/` 新增开关与比对器；对应单测。
- **数据**：无 DDL、无迁移脚本、无存量回填。`0123` 已落的新列继续可空；历史行 `liability_rule_json IS NULL` 必须保持可复算。
- **文档与门禁**：`doc/ai-product/synapClaim/04-design/` 两份文档；`check_all.py` 19 项文档门禁、`scripts/check-sql-entity-column-coverage.py` 列覆盖门禁须保持全绿。
- **风险边界**：开关默认 false ⇒ 发布后行为零变化；回滚 = 关开关或删 JSON 分支。生产启用与压测实测仍属 L4，本 change 不触碰。
