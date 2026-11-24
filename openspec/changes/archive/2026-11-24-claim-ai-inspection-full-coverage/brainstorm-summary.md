# Brainstorm Summary — claim-ai-inspection-full-coverage

> 本文件记录 Design 阶段的探索与候选方案。状态标记：`[已由代码实证]` / `[候选待确认]`。
> 正式设计以 design.md 为准；本文件仅承载推导过程与证据，不创建第二份规格。

## 1. 需求来源（已确认，不重新询问）

来自 OpenSpec delta spec `claim/ai-inspection-full-coverage` 的 4 条 Requirement：
1. 全量抽样比例（100% 时 N 笔合格案件生成 N 条抽检记录；未配置回落 5%）
2. 终态覆盖范围（自动通过 + 拒赔两类终态，100% 时均进入抽检）
3. 租户隔离与配置口径（按租户读取、互不干扰、读取失败 fail-safe 回落 5%）
4. 资金与语义边界（不改金额/AUTO_APPROVE/视觉桩/目录准入）

## 2. 代码现状实证（全部 [已由代码实证]，2026-11-23 侦察）

| 断言 | 判定 | 证据（文件:行） |
|---|---|---|
| `ratePercent>=100` 全量分支已存在 | 属实 | `InspectionSamplingPolicy.java:29-38`（`if (ratePercent >= 100) return true;`） |
| 比例来自入参，策略类无 IO | 属实 | `InspectionSamplingPolicy.java:29`（纯函数），配置由 `InspectionSamplingJob.java:78-82` 透传 |
| 自动候选来源仅 `FINISHED` | 属实 | `findFinishedForInspectionAllTenants.sql:7`（`WHERE c.status = 'FINISHED'`） |
| 第二道门槛 `claim.finished()` 已含两个终态 | 属实（关键发现） | `ClaimCase.java:783-785` → `ClaimStatus.terminal():76-79` = `FINISHED || CLOSED_REJECTED` |
| 拒赔终态枚举名 = `CLOSED_REJECTED`（code "45"） | 属实 | `ClaimStatus.java:52-53` |
| `INSPECTION_SAMPLING` 唯一配置源 | 属实 | 常量 `FeatureToggle.java:57`；唯一消费 `FeatureTenantToggleService.java:151-153` |
| 无配置回落 5% | 属实 | `InspectionConfig.java:14,16-47`；服务层 fail-safe `FeatureTenantToggleService.java:147-175` |
| 抽检只写 `t_claim_inspection`，不碰资金 | 属实 | `InspectionApplicationService.java:106-108`；`InspectionRecord.java:35` |
| 100% 纯策略单测已有；终态范围/端到端测试缺失 | 属实 | `InspectionSamplingPolicyTest.java:30-38`；`InspectionSamplingJobTest.java:102-104`（状态写死 FINISHED） |

**关键推论**：`sampleIfEligible` 的终态门槛已经接受 `CLOSED_REJECTED`，因此「拒赔纳入自动抽检」的实现成本**只在候选查询 SQL 的状态过滤**——把 `WHERE c.status = 'FINISHED'` 扩展为包含 `CLOSED_REJECTED`，下游无需改动。这是现成但未启用的扩展点。

## 3. 方案探索（brainstorming）

### 3.1 终态范围扩展方式
- **方案 A（推荐，已实证可行）**：放宽仓储 SQL 的状态过滤，把候选从 `FINISHED` 扩为 `FINISHED + CLOSED_REJECTED`（终态白名单）。下游 `claim.finished()` 已兼容，零改动。
  - 理由：最小改动面、复用已存在的领域语义、不引入新的状态判断逻辑；风险仅限候选查询，可用 SQL 层测试钉住。
- **方案 B（否决）**：改为「所有终态」——会把 `ADJUSTING`/`REVIEWING` 等非终态纳入，违背 spec「终态覆盖」口径并改变现网行为。
- **方案 C（否决）**：在 `sampleIfEligible` 里另写状态判断——重复 `ClaimStatus.terminal()` 语义，制造第二口径。

### 3.2 100% 全量能力
- **方案 A（推荐）**：不新增抽样算法，`ratePercent>=100` 分支已存在；本 change 的价值是补齐「配置键→候选→建单」的端到端可证伪测试 + 规格钉子。
  - 理由：YAGNI；机制已存在，缺的是守护而非实现。
- **方案 B（否决）**：重写独立采样器——增加资金主链外不确定性，无功能收益。

### 3.3 默认行为保护
- **方案 A（推荐）**：默认 5%、终态默认仍只 `FINISHED`（拒赔随 100% 显式配置生效）；租户隔离沿用现有 tenantId 查询。
- 与 spec Scenario「未配置回落 5%」「租户 A=100 不影响租户 B」完全一致。

## 4. 测试与可证伪策略 [候选待确认]

- 正向：`ratePercent=100` 时 N 笔合格案件（含 `CLOSED_REJECTED`）生成 N 条 `InspectionRecord`。
- 反向/回归：未配置→5%；租户隔离；配置异常→fail-safe 5% + 日志可观测。
- 终态白名单测试：直接钉仓储 SQL 候选过滤（100% 下拒赔案件入选、非终态不入）。
- 变异双向钉子（3 条）：比例分支篡改、终态白名单回退、fail-safe 移除——改一处必红、还原必绿。
- 资金边界：grep + 测试证明抽检只写 `t_claim_inspection`，无 `Adjustment`/`AUTO_APPROVE`/`available`/`MANDATORY` 写入。

## 5. 风险与取舍

- 100% 全量带来抽检量上升 → 仅租户显式启用，默认 5% 不变；容量阈值留待规模化阶段（不改本 change 规格）。
- 拒赔终态与现网 MANUAL 发起重叠 → 终态白名单 + `NOT EXISTS` 去重（SQL 已有），测试覆盖不重复建单。
- 配置读取失败被静默忽略 → 强制 WARN 日志 + 变异测试验证可观测。

## 6. 边界（Non-Goals，与 proposal 一致）

不新增 DDL、不改资金闸、不接真实 provider、不改赔付金额语义、不宣称模型准确率、不改 Go-Live 分册状态。
