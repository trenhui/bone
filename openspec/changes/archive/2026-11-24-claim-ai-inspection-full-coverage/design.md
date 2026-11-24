---
comet_change: claim-ai-inspection-full-coverage
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-24-claim-ai-inspection-full-coverage
status: final
---

## Context

现有后督抽检由 `InspectionSamplingJob` 从 `t_feature_toggle.INSPECTION_SAMPLING` 按租户读取比例（无配置回落 5%），`InspectionSamplingPolicy.shouldSample` 已具备 `ratePercent>=100` 全量分支，但自动抽样候选来源仅 `FINISHED` 终态，拒赔走人工发起（MANUAL）。代码能力与 PRD §14 V1.0「100% 覆盖」口径之间缺的是规格钉子、终态范围与可证伪门禁，而非全新机制。详见 proposal.md 的 Why。

### 代码现状实证（Design 侦察，文件:行）

| 断言 | 判定 | 证据 |
|---|---|---|
| `ratePercent>=100` 全量分支已存在 | 属实 | `domain/model/inspection/InspectionSamplingPolicy.java:29-38`：`if (ratePercent >= 100) return true;` |
| 比例来自入参、策略类无 IO | 属实 | `InspectionSamplingPolicy.java:29`（纯函数）；配置由 `adapter/schedule/InspectionSamplingJob.java:78-82` 读取后透传 |
| 自动候选来源仅 `FINISHED` | 属实 | `resources/sql/.../ClaimCaseRepository/findFinishedForInspectionAllTenants.sql:7`：`WHERE c.status = 'FINISHED'` |
| 第二道门槛 `claim.finished()` 已含两终态 | 属实（关键） | `ClaimCase.java:783-785` → `valueobject/ClaimStatus.java:76-79`：`terminal() = FINISHED \|\| CLOSED_REJECTED` |
| 拒赔终态枚举名 | `CLOSED_REJECTED`（code "45"） | `ClaimStatus.java:52-53` |
| `INSPECTION_SAMPLING` 唯一配置源 | 属实 | 常量 `domain/model/config/FeatureToggle.java:57`；唯一消费 `application/FeatureTenantToggleService.java:151-153` |
| 无配置回落 5% | 属实 | `domain/model/inspection/InspectionConfig.java:14,16-47`；服务层 fail-safe `FeatureTenantToggleService.java:147-175` |
| 抽检只写 `t_claim_inspection`、不碰资金 | 属实 | `application/InspectionApplicationService.java:106-108`；`InspectionRecord.java:35`（`@Table`）；inspection 包内无 `Adjustment`/`AUTO_APPROVE`/`available`/`MANDATORY` |
| 100% 纯策略单测已有；终态范围/端到端测试缺失 | 属实 | `InspectionSamplingPolicyTest.java:30-38`；`InspectionSamplingJobTest.java:102-104`（候选状态写死 `FINISHED`） |

**关键推论**：`sampleIfEligible` 的终态校验 `claim.finished()`（`InspectionApplicationService.java:93`）已经接受 `FINISHED` 与 `CLOSED_REJECTED`。因此「拒赔纳入自动抽检」的**唯一实现改动点是仓储候选查询 SQL 的状态过滤**（`findFinishedForInspectionAllTenants.sql:7`），下游建单链路无需改动。这是现成但未启用的扩展点，也是本设计选择「复用而非重写」的根本依据。

## Goals / Non-Goals

**Goals:**
- 把「100% 全量 + 拒赔纳入自动抽检」固化为可机检的规格与测试
- 保持默认配置（5%）与现网行为完全一致，100% 仅显式开启
- 每项行为配变异双向钉子（改比例/改终态必红，还原必绿）

**Non-Goals:**
- 不新增 DDL、不改资金路径、不接 provider、不声称模型准确率
- 不调整具体抽检限值以外的理赔业务规则

## Decisions

**决策 1：复用现有抽样策略，不重写机制。**
`InspectionSamplingPolicy.shouldSample` 的全量分支已存在（`ratePercent>=100` → true），本 change 只补测试与规格，不引入新抽样算法。备选是重写为独立采样器——否决，因增加资金主链外的不确定性且无功能收益（YAGNI）。

**决策 2：终态范围扩展只改候选 SQL 的白名单，且默认不生效。**
将候选查询从 `WHERE c.status = 'FINISHED'` 扩展为包含 `CLOSED_REJECTED`（终态白名单）；不改 `sampleIfEligible`（其 `claim.finished()` 已兼容）。白名单默认仍只含 `FINISHED` 行为不变的思路通过「100% 配置显式启用」实现——即拒赔终态在进入候选后仍需比例判定，默认 5% 下拒赔即使入候选也按 5% 抽样，不产生全量副作用。备选一「默认全终态」——否决，改变现网行为且纳入非终态；备选二「在 sampleIfEligible 另写状态判断」——否决，重复 `ClaimStatus.terminal()` 语义制造第二口径。

**决策 3：fail-safe 回落，不 fail-fast。**
配置读取失败时回落 5% 并记 WARN 诊断日志，不中断主理赔流程。备选是 fail-fast 拒绝——否决，因抽检为质检旁路，不应成为理赔主链的可用性风险。

> **Verify 修订（2026-11-24，独立审查 IMPORTANT-1）**：原实现只 catch 了 JSON 解析，`featureToggleRepository.findByFeatureKey` / `isOn` 的仓储读取在 try 块外，DB 故障时会冒泡到 Job 计入 failed 并跳过该笔，与 spec Requirement 3「配置读取失败回落 5%」不符。已把仓储读取一并纳入 try/catch，任一异常均回落 5% 并登记 `$json` 诊断；补 `samplingRepositoryFailureFallsBack` 测试（注入仓储异常）。这是本 change 的生产代码第 2 处改动。

**决策 4：红线隔离。**
抽检结果只写 `t_claim_inspection`（`InspectionRecord.java:35`），不进入 `Adjustment` 资金路径；不触碰 `AUTO_APPROVE`、视觉桩 `available`、目录准入 `MANDATORY`。这是本 change 与 V1.0 中被排除的「反欺诈视觉/资金闸」能力的硬边界。

## Test Strategy（可证伪）

| 判据 | 测试类型 | 验收 | 变异钉子 |
|---|---|---|---|
| 100% 全量命中 | 策略单测 + 应用服务层（配置键→建单） | 比例=100 时案件建单（`sampleIfEligibleOutcomes`）；策略 `rateBounds` 钉 100 全量 | 全量分支 `return true`→`return false` → 必红（`>=100`→`>100` 数学等价，不用作变异点） |
| 拒赔终态纳入 | 仓储候选 SQL 契约测试（`InspectionSamplingTerminalScopeTest`） | SQL 同时含 `FINISHED`/`CLOSED_REJECTED` 且保留 `NOT EXISTS` 去重 | 终态白名单回退为仅 `FINISHED` → 必红 |
| 默认 5% 回落 | 值对象单测（`InspectionConfigTest`）+ 服务层（`FeatureTenantToggleServiceTest`） | 无记录/缺键/非整数/越界一律回落 5% 且登记原因 | `DEFAULT_SAMPLING_RATE` 5→0（clean 重编避免常量内联）→ 必红 |
| 租户隔离 | 跨租户场景测试（`InspectionSamplingJobTest.usesPerTenantConfiguredRate`） | 租户 A=100% 不影响租户 B 默认 5% | — |
| fail-safe | 异常注入测试（`samplingBrokenJsonFallsBack`） | 配置读取异常→回落 5% + 登记 `$json`、主流程不中断 | 同默认回落变异 |
| 资金边界 | grep + 测试 | 抽检仅写 `t_claim_inspection`，无资金闸字段写入 | — |

### 变异双向实证（2026-11-24，clean 重编后跑）

| 变异 | 判据测试 | 变异后 | 还原后 |
|---|---|---|---|
| 全量分支 `return true`→`false` | `InspectionSamplingPolicyTest,InspectionApplicationServiceTest` | RC=1 红 | RC=0 绿 |
| 终态白名单回退仅 `FINISHED` | `InspectionSamplingTerminalScopeTest` | RC=1 红 | RC=0 绿 |
| `DEFAULT_SAMPLING_RATE` 5→0 | `InspectionConfigTest,FeatureTenantToggleServiceTest` | RC=1 红 | RC=0 绿 |
| fail-safe catch 回落 5→100（Verify 修复新增） | `FeatureTenantToggleServiceTest.samplingRepositoryFailureFallsBack` | RC=1 红 | RC=0 绿 |

> 注：常量内联坑——`static final int` 会在编译期内联到引用方，仅 touch 源文件不足以让测试用上新值，须 `mvn clean` 才能可靠验证该类变异。

### 独立最终集成审查（review_mode=standard）

由独立 reviewer（Explore 子代理，无实现上下文）对最终 diff 审查，结论：**1 项 IMPORTANT（已修复）+ 4 项 SUGGESTION**。

| 项 | 结论 | 处置 |
|---|---|---|
| IMPORTANT-1：inspectionConfig 仓储读取在 try 外，DB 异常未回落 5% | 已修复：纳入 try/catch + 注入测试 + 第 4 条变异钉子 | ✅ 本次 Verify 修复 |
| SQL status 口径（name vs code "45"） | 5 路交叉验证确认存 name，`IN ('FINISHED','CLOSED_REJECTED')` 正确 | 无需改动 |
| SUGGESTION-1：应用层 mock 掉 claim.finished()，无法证明拒赔建单 | 补 `downstreamTerminalGateAcceptsRejected`（真实 ClaimStatus.terminal 断言） | ✅ 已加固 |
| SUGGESTION-2：spec「行为完全一致」措辞 | 拒赔从 0%→5% 抽样属设计意图，design 已说明不改变数量口径 | 保留（spec 为 Open 批准产物，不在 Build 私改） |
| SUGGESTION-3：SQL 文本门禁非执行级 | 现网单测用内存仓储替身不加载 SQL，执行级需真库；本 change 边界内以文本契约+领域断言覆盖 | 记录为边界 |
| SUGGESTION-4：变异实证无归档文件 | 本 design 表格 + 会话脚本 `/tmp/mutation_verify2.sh` 记录命令与 RC | 已记录 |

## Risks / Trade-offs

- [100% 全量可能带来抽检存储与处理量上升] → 仅租户显式配置启用；默认 5% 不变；容量评估留待规模化阶段，本 change 不预置峰值指标
- [拒赔终态识别口径与现网 MANUAL 发起重叠] → 终态白名单 + SQL 已有 `NOT EXISTS` 去重，测试覆盖「不重复生成抽检记录」
- [配置读取失败被静默忽略] → 强制 WARN 日志，并在变异测试中验证失败可观测
- [终态白名单扩展改变默认候选集] → 拒赔案件即使进入候选，默认 5% 下仍按 5% 抽样，不改变现网抽检数量口径

## Migration Plan

无需数据迁移（无 DDL、沿用现有 `t_feature_toggle` 键）。部署后默认行为与现网一致；如需启用 100%，由租户运营方显式配置 `INSPECTION_SAMPLING=100`。回滚：将配置改回 5% 或移除即恢复现网行为，无需代码回滚。

## Open Questions

无。全部关键决策在本 design 内确定；现场抽检容量阈值属规模化阶段，不改变本 change 的规格、方案或任务拆分。
