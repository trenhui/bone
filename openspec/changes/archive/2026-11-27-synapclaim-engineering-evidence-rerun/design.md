---
comet_change: synapclaim-engineering-evidence-rerun
role: technical-design
canonical_spec: openspec
---

# 工程证据底座 · 技术设计

## Context

见 `proposal.md` 的 Why。当前约束：synapClaim 的 fail-closed 姿态散落在决策图节点、免赔限定条件、能力声明服务三处；`_ai_native_readiness.py` 的 12 项判据是成熟度唯一真源，任何「补充证据」若被误当作门禁输入，会把 L0 伪装为 L3，在受监管金融理赔里是不可接受的资金/合规风险。上一轮评估已证明数字会滞后于代码——因此证据必须可重跑、可机检、可变异证伪。

## Goals / Non-Goals

- Goals：把四处安全姿态收敛为一份稳定、可机检的证据视图；新增判据全部可变异证伪；不改任何资金行为或金额语义。
- Non-Goals（设计层边界）：证据视图不是第二套门禁、不是配置源、不是 UI 展示层；不承担解锁动作本身；不引入外部数据或 provider 凭证；不修改 DDL。

## Decisions

### 决策 1：证据视图只读，门禁判据不放宽
- 选择：`EngineeringEvidenceService` 只产出证据（判据 ID / 状态 / 原因码），`ai-native-readiness` 的 12 项判据保持独立且不放宽；冲突时取更保守一方。
- 理由：安全姿态的判定必须单一真源。若证据视图能翻绿门禁，等于给 L2/L4 解锁开了一条绕过双签的后门。
- 备选（否决）：让复核器直接读取证据视图作为判据输入——会制造「两处配置源」，且证据一旦过时即静默误判。

### 决策 2：不可达维度拒绝落在既有 `LimitedConditions`，不新增 DDL
- 选择：在免赔限定条件的判定入口检测「区域/医保/药品/诊疗」四类无配置列维度，返回稳定原因码与显式拒绝；医院/病种两维保持既有确定行为。
- 理由：`LiabilityRule.java` 已注明这四维「当前没有配置列——表达不出来」，硬补列属 L3 DDL 且依赖外部目录口径；在入口显式拒绝可杜绝「配了但引擎不读」的静默第三态，且不碰金额语义。
- 备选（否决）：新增配置列并接受外部目录数据——超出本 change 范围，属臆造规则。

### 决策 3：provider 缺失 fail-fast 复用现有能力健康定位
- 选择：在自动放行前置校验读取 provider 配置，缺失即以可诊断错误 fail-fast，决策保持 `MANUAL_REQUIRED`；运行态产物继续按真实参与桶记录。
- 理由：`AiRuntimeHealthLocator` / 就绪复核器已有「无 provider 标 RUNTIME_ONLY」的口径，复用它避免另造第二套判据；不改变真实链路，不触发放款。
- 备选（否决）：提供本地桩冒充 provider——会把桩执行算成模型参与，破坏 anti-fake-green。

### 决策 4：fail-closed 原因码写入决策轨迹而非改决策
- 选择：在 `MANUAL_REQUIRED` 的执行轨迹中补充可查询原因码（视觉桩 / 规则桩 / provider 未配 / knowledge 不可用），节点拓扑与决策结果不变。
- 理由：既让人工复核能区分「不可用」与「已核无风险」，又不触碰决策图这一受 L2 保护的资金路径。
- 备选（否决）：让原因码参与 Condition 求值——会改变决策结果，越 L2 红线。

## Risks / Trade-offs

- [证据视图与复核器口径漂移] → 以复核器为门禁真源，证据视图仅补充；两者冲突取保守值；自检钉死不放宽。
- [新增拒绝码破坏前端/国际化] → 若引入新码，同步 `ClaimErrorCodes` + `DEFAULT_HTTP_STATUS` + 两语言包 + 台账；优先复用已有 fail-closed 原因码以避免新增码。
- [不可达维度误伤已支持维度] → 白名单仅医院/病种，按编码比对；变异测试验证已支持维度不受影响。
- [fail-fast 影响现有 E2E 断言] → 保持决策结果不变；E2E 负向钉子验证无 provider 时链路仍走 MANUAL_REQUIRED 而非报错中断主链。

## Migration Plan

无 DDL、无数据迁移。部署为增量代码 + 门禁接线；回滚即移除新增判据与接线，既有 fail-closed 行为不变。

## Open Questions

无。四项解锁前置（盲标数据 / 目录授权 / L2 双签 / L4 合规）已明确登记为外部输入，不属本 change 可决范围。
