# 签收自动建案 — 设计候选摘要（brainstorm）

## 目标与来源
- 来源：用户 Open 确认的 proposal/specs/tasks；`claim/secure-signing` spec 第 38/54 行承诺但未实现的自动建案行为。
- 目标：签收确认后按明细自动建案 → 自动受理 → 分配初审 → 自动初审；失败降级人工；实名明文落主表。

## 候选方案（已写入 design.md）
1. **触发点**：订阅 `SignStatusChangedEvent(FINISHED)`，AFTER_COMMIT 执行建案流水线；失败不回滚签收。
2. **数据模型**：扩展 `t_claim_sign_batch_item` 10 列承载建案要素；`claim_id` 非空=引用已有案件，空=携带要素建案（双路径兼容）。
3. **幂等**：明细级 `business_key`，查重 + `uk_tenant_idem` 唯一索引兜底。
4. **自动化**：accept→allocateToPreExam→startPreExam(automated)，每步独立事务，失败写 AUTO_CASE_FAILED 轨迹并停在合法人工状态。
5. **实名**：按用户裁决明文落主表（insured_id_hash 存身份证、name_masked 存姓名），日志不打印完整证件号；显式记录为 PIPL 偏离。
6. **计划与费用**：plan_id=0 沿用既有口径，占位费用明细金额 0 满足领域约束。

## 用户已裁决的关键点
- 工作区：新 worktree
- 建案落点：自动分配/自动录入，只有自动化不通过或卡住才人工
- 实名：明文落主表，可查可审核可推送保司
- Open 产物：已确认

## 关键取舍与风险
- AFTER_COMMIT 失败不影响签收：靠轨迹 + 待人工数可观测，人工补录兜底。
- 实名明文：偏离 PIPL，仅限本地开发库，生产前必须重评。
- 占位费用/plan_id=0：影响理算，但本轮只建案不理算，停在初审前由人工补充。

## Spec Patch
无。当前 design.md 未改变 spec 行为契约，仅落实用户已裁决口径。
