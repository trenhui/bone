# 头脑风暴摘要 · 工程证据底座

> 候选内容已按 Open 阶段用户确认的 proposal/specs/design 定稿；本文件区分「候选」与「已确认」，供 Design Wait 追溯。

## 项目上下文（已探索）
- 代码：`bone-apps/synap-claim`（app + adjustment-engine），既有 fail-closed 姿态散落在决策图节点、免赔限定条件、能力声明服务。
- 门禁真源：`doc/ai-product/synapClaim/tools/_ai_native_readiness.py` 12 项判据，当前 7 SAT / 5 BLOCK，L0。
- 上一轮评估证明文档数字会滞后于代码，判定必须靠可重跑复核器 + 证据。

## 澄清结论（已确认）
- 用户选择范围：工程证据底座（纯工程、不碰资金闸/合规/DDL）。
- 治理红线：L2 资金闸双签、L3 DDL+L2 授权目录、L4 合规签署均不在范围；无真实 provider 凭证。
- 无改变规格的开放问题。

## 方案取舍（候选 → 已确认）

| 决策点 | 候选 A | 候选 B | 已确认 | 理由 |
|---|---|---|---|---|
| 证据视图与门禁关系 | 证据视图直接作为复核器判据输入 | 证据视图只读、门禁不放宽、冲突取保守 | B | A 会造第二配置源、可绕过双签把 L0 伪装 L3 |
| 不可达维度 | 新增 4 维配置列（DDL） | 在既有 `LimitedConditions` 入口显式拒绝 | B | A 属 L3 DDL 且依赖外部目录口径；B 杜绝「配了不读」静默态 |
| provider 缺失 | 本地桩冒充 provider | fail-fast + 复用健康定位、桩不冒充模型 | B | A 破坏 anti-fake-green；B 不改变真实链路 |
| 决策轨迹原因码 | 原因码参与 Condition 求值 | 仅写入可查询原因码、不改决策 | B | A 越 L2 资金红线；B 可区分「不可用」与「已核无险」 |

## 风险与验证策略（已确认）
- 证据视图与复核器口径漂移 → 复核器为真源、冲突取保守、自检钉死。
- 新增错误码三处齐备 → 优先复用既有 fail-closed 原因码；若新增则 ClaimErrorCodes + DEFAULT_HTTP_STATUS + 两语言包 + 台账。
- 每项新增判据配变异验证：移除接线翻转、只改字面串不翻绿。
- E2E 负向钉子验证无 provider 时主链仍 MANUAL_REQUIRED 而非中断。

## 状态
候选方案与 proposal/specs/design 一致，无 Spec Patch。提交 Design Wait 请用户确认。
