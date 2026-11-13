---
comet_change: synapclaim-workbench-assignment-review-quality
role: technical-design
canonical_spec: openspec
---

## Context

现状取证（2026-11-13，worktree `comet/synapclaim-workbench-assignment-review-quality`，全部结论带证据行）：

| 事实 | 证据 |
|---|---|
| 案件主表**没有**组织/归属/队列/优先级列（真实列清单逐个核对，命中数 0） | `bone-init.sql:2717-2800` |
| 唯一的人员字段是两个复核人 | `ClaimCase.java:174-180`、`bone-init.sql:2745-2746` |
| 「分配初审」不记录被分配人，只做 7→10 | `ClaimCase.java:408-411`、`ClaimCaseController.java:156-162` |
| 无任务实体；`ReviewTaskController` 自述「任务 ID 即案件 ID」 | `ReviewTaskController.java:42-47` |
| 审核/初审共用同一权限码 `claim:review:write` | `ClaimCaseController.java:157-270` |
| 决策图 5 节点 + 三分诊阈值 + 自动上限，`AUTO_APPROVE` 默认关闭 | `DecisionTriage.java:21-32`、`AutoApprovalApplicationService.java:52-57` |
| 阈值只能由 `feature_toggle.config_json` 手工写且「只收紧」 | `TriageLimits.java:48-60,188-222` |
| 抽检结论「只登记不执行」，无列表接口 | `InspectionRecord.java:30-31`、`InspectionController.java:41-137` |
| 初审页仅 1 个筛选条件；核赔页固定拉 50 条假分页；后督无列表 | `PreExamWorkbenchPage.tsx:209-219`、`ReviewWorkbenchPage.tsx:199,343`、`InspectionWorkbenchPage.tsx` 全文 |
| IAM 有 `iam_dept` 与 `iam_account.dept_id`，但**无代理人/委派模型** | `bone-init.sql:80,98`；全仓 grep 代理人/委派 0 命中 |
| 通用工作流有 `transferTask`，理赔作业未接入 | `WorkflowEngineImpl.java:289`、`WorkflowEngine.java:134` |
| 既有文档把 `org_id` 写成分配依据，但该列不存在（文档↔代码漂移） | `工程详细设计（落地版）§9.3/§10.3` vs `bone-init.sql` 列清单 |

约束：确定性内核不动（AI 不碰钱、拒赔人工确认、职责分离）；菜单与导航 v2.2 已收口不重做；`check_all.py` 19 项文档门禁必须保持通过。

## Goals / Non-Goals

**Goals:**
- 建立可运营的作业归属模型：一案多任务、任务有独立生命周期与 SLA、归属变更全程留痕。
- 让「按人 / 按组 / 按角色自动分配」有真实落点与可配置面，且**不许因为请假把案件钉死**。
- 把三段作业（初审 / 审核 / 复审）的队列、查询、AI 辅助做成一线的工位，而不是状态看板。
- 明确「AI 自动初审的规则到底配在哪」——给出三层配置边界与只收紧的棘轮约束。
- 让抽检结论具备处置能力（打回 / 重算 / 追偿），且与资金状态一致。

**Non-Goals:**
- 不引入 `AUTO_REJECT`：拒赔始终人工确认。
- 不把状态机改成配置驱动；状态仍是聚合内的代码常量。
- 不新建理赔侧组织体系（作业组 = `iam_dept`），不新建请假系统（消费 HR/IAM 可用性信号）。
- 不做跨租户共享池、不做移动端、不做批量自动审批（受保护动作禁批量）。

## Decisions

### D1 归属模型：新增 `t_claim_task`，不在案件主表加列

**决策**：新增作业任务表，一案件 × 一任务类型 = 一条存活任务（`PRE_EXAM` / `REVIEW` / `SECOND_REVIEW` / `RE_REVIEW` / `INSPECTION`）。关键列：`task_no`、`claim_id`、`task_type`、`status`（`PENDING_ASSIGN`/`PENDING_CLAIM`/`PROCESSING`/`DONE`/`TRANSFERRED`/`CANCELLED`）、`assignee_id`、`assignee_dept_id`（快照）、`assign_type`（`AUTO`/`MANUAL`/`SELF_CLAIM`）、`priority`、`due_at`、`claimed_at`、`assigned_by/at`、`version`、`tenant_id`。存活唯一性用生成列 `alive_key = IF(status NOT IN ('DONE','CANCELLED'), claim_id+task_type, NULL)` + `UNIQUE(tenant_id, alive_key)`（MySQL 无 partial unique index）。

**理由**：一案同时存在多个待办（初审完 → 审核 → 复审），且每条待办有独立归属、独立 SLA、独立转交历史。案件主表已有 40+ 列，加 `assignee_id/pool_id/priority` 只能表达「当前唯一归属」，转交即覆盖、无留痕，且无法承载队列视图。

**备选与否决**：
- 直接在 `t_claim_case` 加 `assignee_id` + `pool_id`：无法表达一案多任务，转交无历史 → 否决。
- 复用 `wf_task`（`bone-engine` 通用审批）：`wf_task` 的 `node_code`/`node_round` 是流程编排语义，理赔是状态机驱动、无流程实例；强行接入等于给每个案件起流程实例，跨模块强耦合 → 否决。
- 新建独立微服务：体量与收益不匹配 → 否决。

### D2 作业组真源：`iam_dept`，并纠正文档

**决策**：作业组 = IAM 部门（`iam_dept`），任务表冗余 `assignee_dept_id` 作快照；**删除既有文档中「`org_id` 是任务分配路由依据」的表述**，改为「租户 × 险种 × 金额段 × 渠道 → 作业组（`iam_dept`）」。

**理由**：组织体系已存在且被账号引用（`iam_account.dept_id`），再建 `t_claim_group` 必然与 IAM 双写漂移。而文档里的 `org_id` 在 DDL 与实体中均不存在，是纯粹的纸面字段——保留它会让实现者建出一个永远读不到值的路由条件（本项目已多次踩「配了不生效」）。

**备选**：新建理赔作业组表并与 IAM 组织映射 → 两套组织树，权限与报表口径分裂 → 否决。

### D3 分配策略：规则表 + 候选集打分 + 超时回落池

**决策**：新增 `t_claim_assign_rule`（匹配维度：租户 / 险种 / 金额段 / 渠道 / 任务类型 → 目标类型 `USER|DEPT|ROLE` + 目标 ID + 优先级 + 策略）。求解顺序固定为：

```
① 显式指派（主管手工/上一环节指定人）
② 命中最高优先级规则 → 展开候选集（人 / 组内人 / 角色内人）
③ 候选集打分：技能匹配 > 在处理量（负载）> SLA 紧迫度 > 轮询公平
④ 无人可派 → 落公共池（PENDING_CLAIM），任何持权人可自领
```

**理由**：真实理赔部的派单不是「一条规则定终身」——大额要派给高技能组、小额要摊平负载、峰值要允许自领兜底。规则命中后仍要做候选集打分，是因为规则只能表达「派给谁这一群」，不表达「这一群里谁现在有空」。

派单**不是锁定**：`PENDING_CLAIM` 超过 `assign_timeout` 未领取即回池，这在 D4 的请假场景下是关键安全阀。

**备选**：纯人工派单（峰值不可支撑）／纯自领池（质量与公平不可控、主管无法干预）→ 均否决。

### D4 请假 / 转交 / 代理：三层拆开，不自建 HR

**决策**：
- **转交**（单案移交）：`POST /api/v1/claim/tasks/{taskNo}/transfer`，写 append-only 移交记录（from / to / reason / operator），任务归属即时变更。
- **代理**（时间窗委托）：`t_claim_user_agent`（`principal_id`（被代理人）/`agent_id`（代理人）/`scope` 任务类型集合/`start_at`/`end_at`/`status`）。生效期间，「我的待办」= 我本人的任务 ∪ 我代理的任务，且新派单可路由给代理人。
- **不可用**（请假）：不建请假系统。优先消费 IAM/HR 的不可用信号；无信号时由主管手工置「暂停派单」标记。分配器遇到不可用人员**直接跳过**，且该人已持有的 `PENDING_CLAIM` 任务在下一个扫描周期自动转代理或回池。

**理由**：理赔自建请假 = 与 HR 双写，必然不一致；但系统必须有一个 fail-closed 的「此人现在不能接活」信号，否则请假一天就有一批案件静默过期。

**备选**：接入 `wf_task.transferTask` → 见 D1；只做转交不做代理 → 请假期间新案仍会派给他，问题没解决 → 否决。

### D5 独立复审：新增状态码 43 / 46，与「双人复核」并列而非合并

**决策**：
- `43 = PENDING_RE_REVIEW`（复审待办）、`46 = RE_REVIEWING`（复审中）。`44 = REVIEWING` 保持为金额 ≥1 万的**双人复核**，语义不动。
- 流转：`ADJUSTED(42)` / `REVIEWING(44)` 结论通过后 → 按规则进入复审（43）→ 复审结论通过 → `FINISHED(50)`；复审打回 → 回 `ADJUSTING(41)`。
- 权限码分离：新增 `claim:re-review:write`。复审人 ≠ 初审人 ≠ 第一审核人（复用现有 `SC_CLAIM_REVIEWER_CONFLICT` 同类校验并扩展）。

**理由**：双人复核是**金额闸门**（≥1 万必须两人），复审是**质量闸门**（按比例/风险抽复审）；两者的触发条件、队列、度量指标完全不同。合并会让主管无法分别计算「双人复核积压」与「复审质量通过率」，也无法对复审人做职责分离。

**备选**：复用 `44` + `status_sub` 细分 → `status_sub` 现有取值语义未在契约中定义，且既有 40+ 处状态断言（状态字典、`t_claim_case_track`、SLA、前端 `statusOptions`、探针）都要按 `status` 判等，改动面与风险都大于新增两个码位 → 否决。

**为什么不是「流程内质检」**：复审在结案前、属作业质量；质检在结案后、属事后监督。用户已裁定质检保持「后督抽检 + 打回重办」，不新增流程内质检状态，因此复审是本次唯一的流程内新增环节。

### D6 AI 自动初审：三层配置边界 + 只收紧棘轮 + 建议上屏

**决策**（直接回答「自动初审的规则在哪里配置」）：

| 层 | 内容 | 载体 | 谁能改 |
|---|---|---|---|
| L1 系统确定性规则 | 证据完整性、责任判定、理算公式 | 代码（不可配） | 研发 |
| L2 租户自动化策略 | `AUTO_APPROVE` 开关、金额上限、`aiManualThreshold`(0.7)、`aiReviewThreshold`(0.85)、`ocrManualThreshold`、放量档位 | `t_feature_toggle.config_json` | 租户管理员（**只能收紧**） |
| L3 作业参数 | 分配规则、SLA、复审比例与抽检比例 | `t_claim_assign_rule` / 配置表 | 业务主管 |

- 新增受控配置 API：`GET/PUT /api/v1/claim/ai/triage-config`，写侧复用 `TriageLimits` 的棘轮校验（新值只能使自动通过更难），并写审计。前端在既有 AI 治理页（当前纯只读）新增「自动初审策略」区块，明确展示收紧方向与当前放量档位。
- **不引入 `AUTO_REJECT`**：维持 `SUGGEST_REJECT` + 人工确认。
- 初审工作台加入 AI 建议列（结论 / 置信度 / 命中规则）与采纳/驳回回流，复用已有 `t_claim_ai_feedback_event`，让「AI 建议采纳率」成为可观测指标。

**理由**：当前 `AUTO_APPROVE` 默认关闭、阈值只能手写 JSON，等于「能力有、运营无」；而把阈值做成自由可调会打开资金风险口子。棘轮 + 放量档位 + 采纳率观测是同时满足「能运营」和「不出事」的最小组合。

### D7 抽检打回：结论可执行化，但受资金状态约束

**决策**：`t_claim_inspection` 结论新增可执行处置，落 `t_claim_inspection_action`（inspection_no / action / target_status / operator / executed_at / reverser_ref）。打回按资金状态分叉：

```
未支付 → 允许直接打回（回 ADJUSTING / REVIEWING 或重开复审）
已支付 → 必须先冲正（REFUND 回冲）成功，才允许打回；否则只登记差异并置"待追偿"
```

**理由**：已赔付案件直接回退状态会造成「案件在审但钱已出」的账实不一致，这是资损面。抽检结论必须与资金事实同源，而不是绕过资金流水改状态。

**备选**：只登记不处置（现状，等于质检无效）／无条件打回（资损风险）→ 均否决。

### D8 查询模型：服务端分页 + 统一条件集，修掉假分页

**决策**：抽 `ClaimQuery`（`keyword`（报案号/保单号/被保人哈希）、`statuses[]`、`taskType`、`assigneeId`、`deptId`、`sourceChannel`、`amountMin/Max`、`createdFrom/To`、`slaBucket`、`page`、`size`），返回真实 `total`；三工作台共用同一查询契约。索引：`idx_tenant_task_assignee_status(tenant_id, assignee_id, status, due_at)`。

**理由**：核赔页固定拉 50 条、前端切 20/页，第 3 页即空——这不是「条件少」，是**分页契约错误**，用户翻不到第三页就等于丢了三分之二的待办。

## Risks / Trade-offs

- [新增状态码 43/46 波及 40+ 处状态断言（字典、track、SLA、前端 options、探针）] → 同一批补齐状态字典单测 + 状态机穷举测试；迁移脚本对存量行不加新状态，灰度期默认不启用复审。
- [分配规则误配导致案件无人处理] → ④ 回落公共池是强制兜底；规则变更提供 dry-run 命中量预览，命中 0 人时禁止保存。
- [请假信号数据源缺失] → 无信号时 fail-closed 到公共池 + 主管手工置暂停；不静默按「在职」处理。
- [复审与双人复核概念混淆，用户/客服口径不一致] → 前端队列分列、权限码分离、文档名词表统一，并在页面文案明确「金额闸门 / 质量闸门」。
- [已支付打回造成账实不一致] → 强制冲正前置（D7），未冲正时只允许登记差异。
- [设计文档再次与代码漂移（`org_id` 教训）] → 本 change 的所有字段级论断必须带 `bone-init.sql` 行号；批次 4 给 `check_all.py` 增加「设计文档引用的列必须存在于 DDL」检查。
- [一案多任务与现有「任务 ID = 案件 ID」契约冲突] → `ReviewTaskController` 的 `{claimId}` 路径保留为兼容层（内部映射到 `task_no`），不直接 BREAKING。

## Migration Plan

分 4 批，每批一个独立 change，各自带迁移脚本、可回滚、可独立验收：

| 批次 | 范围 | 依赖 | 回滚 |
|---|---|---|---|
| 批 1 | `t_claim_task` + `t_claim_assign_rule` + 分配器 + 我的待办 + 统一查询 + 修假分页 | 无（D1/D2/D3/D8） | 停用分配器开关，任务表保留不读 |
| 批 2 | AI 自动初审配置 API + 治理页可写 + 初审页 AI 建议与采纳回流 | 批 1（任务归属用于归因） | 关闭 PUT 接口，回到只读 |
| 批 3 | 独立复审阶段（43/46）+ 复审队列 + `claim:re-review:write` | 批 1 | 复审开关关闭，43/46 无流量 |
| 批 4 | 抽检打回闭环 + 请假/转交/代理 + DDL 文档一致性门禁 | 批 1、批 3 | 处置动作置只登记模式 |

本 change（设计 + 文档）是上述四批的前置：产出分析报告、三份设计文档、既有文档回写与分批清单，不含任何代码或 DDL 变更。

## Open Questions

- 复审的触发比例与口径（按金额段？按审核人抽样？按风险标记？）实现期由业务定，不影响本设计的表结构与状态机。
- 上线时在途案件（status 40/41/44）的初始任务归属：建议一次性脚本按「当前 `first_reviewer_id` 回填 + 其余入池」，具体策略在批 1 落地前确认。
- 代理关系是否需要审批（主管审批代理申请）：当前设计为「主管可建可直接改」，审批流在批 4 再评估。
