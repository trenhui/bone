# 深度分析：理赔作业台融合增强（分配引擎 / 独立复审 / 抽检打回）

## Why

初审、核赔、后督三个工作台目前仍是「按状态推进」的看板，不是真实产险理赔部能排班作业的工位：

- **没有归属**：`t_claim_case` 只有 `first_reviewer_id` / `second_reviewer_id` 两个「谁领了」字段，全库 `assignee_id` 在理赔作业域零命中；`POST /{id}/allocate` 只做状态 7→10，**不记录派给谁**，因此不存在「按人/组/角色自动分配」的落点。
- **没有队列表**：初审只能按状态筛（1 个筛选条件），核赔是固定拉 50 条的假分页（第 3 页即空），后督**没有列表接口**、必须先知道 18 位案件号才能作业。
- **没有请假与代理**：IAM 侧无代理人/委派模型，工作流引擎虽有 `transferTask` 但理赔作业未接入，人一请假案件就卡在原地。
- **AI 能力在后台、不在屏上**：决策图与三分诊阈值（0.7/0.85、自动上限 10000）已实现，但 `AUTO_APPROVE` 默认关闭、阈值只能由 `feature_toggle.config_json` 手工写且「只能收紧不能放开」，前端无任何建议/置信度/采纳触点，AI 治理页为纯只读。
- **质检只登记不处置**：抽检结论不回写案件、无打回重办，检出问题无法驱动返工。
- **文档↔代码漂移**：既有《工程详细设计（落地版）》§9.3/§10.3 把 `org_id` 写成「任务分配路由依据」，但该列在 `bone-init.sql` 与 `ClaimCase` 实体中**均不存在**——分配引擎不能建在这个不存在的字段上。

本 change 因此不是「把菜单整理下」，而是把三段作业从状态看板升级为**队列 + 归属 + 能力**驱动的工作台，并先补齐可落地的设计与规格，再分批实现。

## What Changes

- **新增深度分析报告**（`docs/superpowers/reports/`）：现状取证矩阵（后端/前端/文档三线）、业界对标（Guidewire ClaimCenter 工作队列与调整员工作台、SAP Fiori Spaces/配额、Duck Creek 90% 人机一致率门槛、Five Sigma 四级自主度、NAIC/EIP 监管口径）、差距清单与优先级。
- **新增三份设计文档**（`doc/ai-product/synapClaim/04-design/`）：
  - 作业分配与代理交接设计（分配规则模型、候选集求解、负载与技能、请假/转交/代理状态机、并发与幂等）
  - 独立复审阶段设计（与「≥1 万双人复核」的关系、状态位与流转、权限码分离、复审队列与 SLA）
  - 抽检打回闭环设计（结论可执行化：打回/重算/追偿，与案件状态、资金流水的一致性约束）
- **回写既有设计文档**：`工程详细设计（落地版）` §9.3/§10.3/§7.6/§11.4、`AI技术方案` §10.1、`总体技术方案` §7/§8、`易用性与角色工作台设计提案` 升版（查询条件、我的待办、AI 触点）。
- **修正文档漂移**：删除/改写不存在的 `org_id` 依据，改为「租户 × 险种 × 金额段 × 渠道 → 作业组（IAM `iam_dept`）」的真实落点。
- **产出 4 批落地清单**：① 分配模型 + 我的待办 + 查询增强；② AI 自动初审配置面 + 建议触达与采纳回流；③ 独立复审阶段；④ 抽检打回闭环 + 请假代理。
- **不变项（明确不做）**：AI 不进入金额与资金决策路径；拒赔仍需人工确认；不把状态机改成配置驱动；不重做菜单与导航（v2.2 已收口）；不改既有权限码语义（新增码另行登记）。

## Capabilities

本 change 为**纯设计 + 文档**交付：不改变任何运行时行为，故显式 opt-out specs（`skip_specs: true`）。行为规格（`openspec/specs/claim/**`）由后续每批实现 change 在其落地时回写，避免用「尚未实现的行为」污染规格真源。

### New Capabilities

（无。行为能力由后续实现批次按 `openspec/specs/claim/<capability>/spec.md` 逐个新增。）

### Modified Capabilities

（无。本轮不改任何既有规格条目；文档族以 `doc/ai-product/synapClaim/` 的 manifest 登记与 `check_all.py` 门禁为真源。）

## Impact

- **文档**：`doc/ai-product/synapClaim/04-design/`（新增 3 份 + 回写 4 份）、`05-plan/智能理赔-易用性与角色工作台设计提案.md` 升版、`docs.manifest.json` + `AI-INDEX.md`（生成物）+ `README.md` 同步。
- **代码**：本轮**不改**任何 Java / TS / SQL；不新增迁移、不改 DDL。
- **依赖系统（仅设计层面引用）**：IAM（`iam_dept` / `iam_account.dept_id` / `iam_role`）、工作流引擎（`wf_task.assignee_id` + `transferTask`）、`t_feature_toggle`（`AUTO_APPROVE` / `TRIAGE_LIMITS`）、`t_claim_inspection`。
- **门禁**：`doc/ai-product/synapClaim/tools/check_all.py`（19/19）与 `scripts/check.sh` 文档族检查必须保持通过。
- **风险承担**：本轮无运行时风险；设计中的状态码位、新表、新权限码需在后续批次实现前单独确认。
