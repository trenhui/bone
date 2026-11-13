## Purpose

把理赔案件的「归属」从案件表上的单列升级为可独立领取、可审计、可回退的任务实体，使一案多任务、按规则自动派单、无人可派回落入池与我的待办查询都有明确且可验证的行为契约，同时保证待办分页总数与真实数据量严格一致。

## ADDED Requirements

### Requirement: 一案多任务且同一案件同时至多一个生效任务

系统 SHALL 为每案件生成独立任务行（含 `task_no`、租户、任务类型、状态、归属人/组），并 MUST 保证同一租户同一案件的同一任务类型在同一时刻至多存在一条生效任务；已终态任务不占用该唯一性。

#### Scenario: 同类型重复建任务被拒

- **WHEN** 同一案件在已有 `PENDING_CLAIM` 任务时再次触发同类型建任务
- **THEN** 第二次写入 MUST 被唯一键拒绝并返回可辨认的冲突错误，任务总数仍为 1

#### Scenario: 终态任务不阻塞新任务

- **WHEN** 前一任务已完成（终态），同一案件因打回或复审再次需要同类型任务
- **THEN** 允许新建一条生效任务，且历史终态任务保留可查

### Requirement: 规则驱动的自动分配

系统 SHALL 按租户 / 险种 / 金额段 / 渠道 / 任务类型匹配生效规则，按固定求解顺序与优先级选出目标（`USER|DEPT|ROLE`），把命中的任务标为 `AUTO` 并写入归属人。灰度窗口 7 天内 `assign_type='AUTO'` 且 `assignee_id IS NOT NULL` 的比例 MUST ≥ 0.90。

#### Scenario: 规则命中即派

- **WHEN** 新建任务命中一条生效规则且目标用户可用
- **THEN** 任务 `assign_type='AUTO'`、`assignee_id` 为该用户，并留下可审计的规则命中记录

#### Scenario: 命中率不达标即判失败

- **WHEN** 对灰度窗口执行统计 SQL，`AUTO` 且已指派占比低于 0.90
- **THEN** 验收 MUST 判红，不得以「体验更好」类描述替代该数值判据

### Requirement: 无人可派回落入池且可被自领

系统 MUST 在候选集全不可用（请假、停用、无持权人）时不臆造归属，把任务置为 `PENDING_CLAIM` 且 `assignee_id IS NULL`；该计数 SHALL 按小时落监控，且这些任务 MUST 100% 可被任意持权人自领。

#### Scenario: 注入候选集全不可用

- **WHEN** 探针把所有候选用户置为不可用后建任务
- **THEN** 任务留在池中、`assignee_id IS NULL`，且监控计数 +1

#### Scenario: 池中任务可被自领

- **WHEN** 任一持权人对池中任务发起领取
- **THEN** 领取成功且归属人改为该持权人；任务不再出现在池中

### Requirement: 并发自领互斥

系统 MUST 保证同一任务被并发领取时恰好一个成功，另一个返回 `SC_CLAIM_TASK_CONFLICT`，不得出现双归属或静默覆盖。

#### Scenario: 并发双领取

- **WHEN** 两个持权人同时领取同一池中任务
- **THEN** 恰有 1 个返回成功，另 1 个返回 `SC_CLAIM_TASK_CONFLICT`，库中归属人为成功者

### Requirement: 我的待办查询与租户隔离

系统 SHALL 提供按当前持权人过滤的待办查询，1 万在办量级下服务端耗时 P95 MUST < 300ms；查询 MUST 同时受功能权限与租户隔离约束，跨租户任务 MUST NOT 出现在结果中。

#### Scenario: 1 万在办量级采样

- **WHEN** 构造 10 000 行在办任务并对待办端点取 100 次采样
- **THEN** 服务端耗时 P95 < 300ms，且 `EXPLAIN` 命中 `idx_tenant_assignee_status`

#### Scenario: 跨租户不可见

- **WHEN** 租户 B 的持权人查询待办而任务属于租户 A
- **THEN** 结果集不含该任务，且不以「查不到」掩盖越权成功

### Requirement: 待办分页总数正确性

系统 SHALL 让列表的 `total` 取自与列表同一过滤条件的 `COUNT(*)`，MUST NOT 由前端以固定条数切片后上报；造 137 条待办时 `total` MUST 等于 137，逐页条数之和与 ID 去重后均等于 137。

#### Scenario: 137 条翻页探针

- **WHEN** 造 137 条待办并翻到第 7 页
- **THEN** `total == 137`、逐页条数之和 == 137、ID 去重后 == 137，无重复无遗漏

#### Scenario: 前端固定切片被移除

- **WHEN** 把前端改回固定 50 条切片并运行同一探针
- **THEN** 验收 MUST 判红（变异验证），失败信息能定位到 `total` 或切片位置

### Requirement: 分配器可回退

系统 SHALL 提供 `AUTO_ASSIGN` 开关；关闭时分配器 MUST NOT 写入新归属，任务表保留但读侧不依赖其存在，已有业务路径行为与开关关闭前一致。

#### Scenario: 关闭开关后建任务

- **WHEN** `AUTO_ASSIGN` 关闭后新建案件任务
- **THEN** 任务以入池形态创建、`assign_type` 非 `AUTO`，且既有审核流程无报错

#### Scenario: 开关关闭后 43/46 等后续批次无流量

- **WHEN** 开关关闭且后续批次尝试按任务归属归因
- **THEN** 归因逻辑 MUST 走空值兜底而非抛异常，不产生隐式指派
