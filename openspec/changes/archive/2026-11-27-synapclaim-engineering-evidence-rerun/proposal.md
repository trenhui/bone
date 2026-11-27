## Why

synapClaim 的确定性工程主链（案件 → 理算 → 额度 → 结算）已达生产级，但设计方案中依赖 AI 与外部数据的能力多数处于 fail-closed / 默认关状态。上一轮实现度评估中多项「未实现」结论已滞后于代码（L3 就绪度实际为 7 SAT / 5 BLOCK，300 条盲标与冻结集已真解锁），说明**判断现状必须靠可重跑的复核器与证据，而不是文档快照**。当前缺少一层「工程证据底座」：让每一处 fail-closed 姿态都能被独立取证、被变异证伪、被如实记录，为后续 L2/L3/L4 授权解锁提供可信前置，而不是靠继续堆功能或改默认开关。

本 change 只做**纯工程、可当场验证、不越治理红线**的部分。资金闸（视觉桩 `available=true`、`AUTO_APPROVE` 打开、目录准入改 MANDATORY）、L3 DDL 补全、L4 合规签署均不在范围内——那些需要双签、架构审批或法务签署，AI 单方执行即构成伪造或资金行为变更。

## What Changes

- 新增 `EngineeringEvidenceService`（工程证据服务）：汇总四处安全姿态的可判定证据——决策图 fail-closed 落点、免赔维度不可达原因、AI provider 配置口径、能力声明与实现一致性——输出结构化、可机检的证据视图，供就绪复核与人工解锁评审消费。
- 免赔限定条件新增「不可达维度显式拒绝」判据：当配置出现当前无配置列 / 依赖外部目录的维度（区域 / 医保 / 药品 / 诊疗）时，MUST 返回显式拒绝错误码与原因，禁止「配了但引擎不读」的静默第三态。沿用现有 `LimitedConditions` 记录，不新增 DDL 列。
- provider 配置缺失 fail-fast：AI 网关 base-url 缺失时以清晰、可诊断的方式拒绝进入自动放行路径，并保持运行态口径如实记录（禁止以桩执行冒充模型参与），不改变现有真实链路。
- 新增工程证据专项测试与变异验证：对每条新增判据提供「改一处即翻红」的正向与反向钉子；不新增外部数据依赖，不触碰资金金额语义。
- 复核器口径对齐：`_ai_native_readiness.py` 的既有判据不因本 change 放宽；本 change 的证据视图作为**补充**输入，不替代现有 12 项门禁。

**非目标（明确不做）**：不改 `AUTO_APPROVE` 默认值；不改视觉反诈桩 `available=false`；不改 `CATALOG_POLICY` 默认 NONE；不补免赔 4 维配置列（L3 DDL）；不补定额 / 津贴内核业务规则（无 V1 语料即不臆造）；不将 Go-Live 13 册标为 `current`（L4）；不改任何赔付金额计算语义；不接入真实 provider 凭证。

## Capabilities

### New Capabilities
- `claim/engineering-evidence-baseline`: 工程证据底座——汇集 fail-closed 姿态的结构化证据、不可达维度显式拒绝、provider 缺失 fail-fast，以及每项的变异可证伪性。

### Modified Capabilities
- `claim/ai-native-readiness-governance`: 补充「工程证据视图」作为如实记录的输入来源，判据本体不放宽。
- `claim/decision-graph-orchestration`: 明确 fail-closed 落点的可查询证据字段（不改变节点拓扑或决策结果）。

## Impact

- 代码：`bone-apps/synap-claim/synap-claim-app`（application / adapter）、`bone-apps/synap-claim/adjustment-engine`（免赔限定条件判据，仍 fail-closed）。
- 错误码：若新增不可达维度拒绝码，须同步 `ClaimErrorCodes` + `ClaimErrors.DEFAULT_HTTP_STATUS`（避免类加载抛错）与前端两语言包 errors，并登记错误码台账。
- 门禁：`scripts/check.sh` 接线新增判据（带 `--check`）；`doc/ai-product/synapClaim/tools/_ai_native_readiness.py` 保持不放宽。
- 无 DDL、无资金行为变更、无合规文档签署、无真实外部数据依赖。
