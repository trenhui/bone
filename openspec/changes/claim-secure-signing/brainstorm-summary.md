# 案件签收 —— Design 候选方案与取舍记录

> 状态：候选（待 Design Wait 确认）。来源：Open 阶段 proposal/specs/design/tasks + 用户四项拍板。
> 本文件记录 brainstorming 阶段的方案空间，正式设计以 `openspec/changes/claim-secure-signing/design.md` 为准。

## 已确认（用户拍板，不可重开）

1. 工作区：独立 worktree `.worktrees/claim-secure-signing`，分支 `comet/claim-secure-signing`。
2. 交付范围：前后端全链路（含后端签收端点/记录/状态机/迁移脚本，不执行生产 DDL）。
3. 线上自动签收：双触发——内部报案自动签收 + 外部推送幂等签收。
4. 导航：正式菜单（visible 路由 `/sign` + IAM 菜单 SQL + 单导航 E2E 探针）。

## 候选方案与取舍

### C1 签收模型：独立批次聚合 vs 挂在案件上

- **候选 A（采纳）**：独立聚合 `ClaimSignBatch` + 子集合 `SignBatchItem`/`SignTrackLog`。理由：参考普康做法，能表达批次与多案签收、轨迹独立；符合 DDD 聚合边界。
- **候选 B（否决）**：签收字段挂 `t_claim_case`。理由：无法表达批次，污染案件实体。
- **取舍**：新增 3 张表 + 迁移复杂度上升；换来完整审计能力与批次语义。

### C2 并发安全：CAS 条件更新 vs 乐观锁 vs 分布式锁

- **候选 A（采纳）**：CAS `UPDATE ... WHERE sign_status = 前置态`，影响行数 0 即冲突；`@Version` 兜底。理由：Bone 规范禁 `FOR UPDATE`；CAS 精准解决"同一批次重复确认"。
- **候选 B（补充）**：`@Version` 乐观锁作为聚合整体并发兜底。
- **候选 C（否决）**：Redis 分布式锁。理由：引入外部依赖与锁续期复杂度，超出收益。
- **取舍**：需要在仓储层提供条件更新方法并配 SQL 模板；换来可审计的幂等语义。

### C3 线上自动签收用例组织：统一用例 vs 两套逻辑

- **候选 A（采纳）**：`AutoSignApplicationService.signOnAcceptance` 单一用例，内部报案末端与外部回执端点共同调用，参数含 businessKey/channel。
- **候选 B（否决）**：两条链路各写一套。理由：易漂移、轨迹格式不一致。
- **取舍**：报案链路新增签收副作用（签收失败=报案失败，事务内不吞错）；换来双触发行为一致。

### C4 幂等键：稳定键 ≤64 vs 随机 UUID

- **候选 A（采纳）**：稳定语义操作用 `boundedStableKey`（前缀+FNV-1a 哈希截断到 ≤64），重试复用同键；仅不可预知操作用 UUID。
- **理由**：真库列 `varchar(64)`，项目实测超长报 `COMMON_VALIDATION_FAILED`。
- **取舍**：需要单测钉死 length≤64，防回归。

### C5 前端页面：常规 antd5 页面 vs 低代码 schema 渲染

- **候选 A（采纳）**：React18 + antd5 常规页面，复用 `useClaimQuery`/`ClaimQueryFields`/`Table`/`AuthButton`/`useConfirmAction`。
- **候选 B（否决）**：移植 PKRender 低代码渲染。理由：跨技术栈、无对应基础设施。
- **取舍**：自定义页面工作量更大，但符合 Bone 微应用范式，可控可测。

### C6 影像完备性口径

- **候选 A（采纳）**：明细关联的 evidence 是否齐备（DocType 四类）为准；签收批次只登记完备状态，不存本体。
- **风险**：口径与既有 evidence 模块不一致会误拒签收；需在 Verify 用真实链路验证。

## Spec Patch

无。C1–C6 均在 Open 阶段 specs 的覆盖范围内，未改变已确认行为需求；无需修改 delta spec。

## 风险与验证策略

- 并发双签 → 两个并发确认请求单测，恰一成功一冲突。
- 幂等键超长 → 单测钉 `≤64`，真库联调复核。
- 正式菜单 E2E 探针 → 同步 routes/IAM SQL 后跑 `claim-single-nav-probe.mjs`。
- 自动签收改变报案行为 → 成功/失败路径单测 + 本地双触发联调记录。
