---
comet_change: synapclaim-review-fatigue-controls
role: technical-design
canonical_spec: openspec
---

# Design: synapclaim-review-fatigue-controls

## 架构决策

沿用既有「domain 纯判据 + application 读配置/取数 + adapter 只做端口实现」分层，不新增架构概念、不引入新基础设施。

### 配置口径

`ReviewFatigueConfig` 是唯一解析真源，输入来自 `t_feature_toggle` 的 `REVIEW_FATIGUE_GUARD.config_json`。无记录/未开启/非法 JSON/越界字段一律回落代码基线，并在 `rejected` 中登记原因；应用层只把解析后的值对象交给领域判据，domain 不依赖 JSON/Spring。未知键必须出现在 `rejected`，避免“配了不生效”。

### 数据流

```
FeatureToggleRepository → FeatureTenantToggleService.reviewFatigueConfig(tenantId)
                                      ↓ ReviewFatigueConfig
ClaimTaskRepository.countFinishedTodayByAssignee(tenantId, assigneeId, UTC dayStart)
                                      ↓ long finishedToday
ClaimTaskApplicationService.decide()
   └─ FatigueAwareEligibilityPort(delegate + config + finishedToday)
        └─ TaskAssigner.decide(ctx, rules, guardedEligibility)
             ├─ 未超量 → 既有命中逻辑
             └─ 超量/计数异常 → ALL_INELIGIBLE → 入池 → 任务中心再派
```

`GET /review/fatigue-guard/evaluation` 只读取同一配置与调用方传入的连续时长/班末分钟/金额，返回评估 DTO；不写库、不改状态、不自动改派。

### 关键判定

1. **真实事实只取库内**：`finished_at` 已存在于 `t_claim_task`，按租户 + 复核人 + `DONE` + UTC 当日零点过滤；不用客户端声明的完成数。
2. **超量即候选不可用**：达到上限（`finishedToday >= maxPerShift`）即判不可用，交给 `TaskAssigner` 既有回落入池，保持“分配失败不等于收单失败”的不变量。
3. **异常 fail-closed**：仓储异常/返回不可信时按不可用处理，绝不因“查不到”把人当可用；主链不抛异常。
4. **软提示不阻断**：连续时长达到阈值只返回 `restPrompt=true`，不清空队列、不禁用提交、不改案件状态。
5. **敏感案件边界如实**：`sensitiveEligible=false` 目前只用于提示；没有可信服务端班次结束事实前，不宣称“班末硬排除”已完成。
6. **不碰钱**：金额阈值只用于提示/候选资格，不进入理算、拒赔、支付；AI/规则仍不得触碰资金结论。

### 不做的事（防范围蔓延 / 防造假绿）

- 不新增表、字段、迁移或 SQL 模板；`finished_at` 已存在。
- 不接 IAM 实时在职状态（缺降级契约，仍沿用 `DefaultAssigneeEligibilityAdapter` 的形态判定）。
- 不实现服务端班末硬排除：调用方传入的分钟数只做提示，验收登记 gap。
- 不重复实现主管抽检：`t_claim_inspection` 与抽检处置链已在前序批次落地。
- 不改拒赔/理算/支付路径，不引入自治 Agent。
