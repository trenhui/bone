---
comet_change: synapclaim-task-assignment-workbench
role: technical-design
canonical_spec: openspec
---

## Context

动机见 `proposal.md`。当前实现把归属压在 `t_claim_case.first_reviewer_id` 一类单列上，`ReviewTaskController` 自述「任务 ID 即案件 ID」，无法表达一案多任务；`bone-init.sql` 与 `ClaimCase.java` 均无 `org_id`，作业组真源是 IAM `iam_dept`（v2.3 §13.6 已纠正）。本批是四批中的第一批且无前置依赖，批 2/3/4 都读它的任务归属。已确认的上游设计是 `openspec/changes/archive/2026-11-13-synapclaim-workbench-assignment-review-quality/design.md` §四批落地路线与提案 §13.5。

## Goals / Non-Goals

**Goals:**
- 建立可独立领取、可审计、可回退的任务实体与规则表
- 让分配命中率、回落率、待办延迟、分页 total、索引覆盖都成为可执行的数值判据
- 保留既有 `{claimId}` 兼容路径，旧代码对新表零感知（expand 阶段）

**Non-Goals:**
- 不改既有审核状态机，不合并 44 双人复核，不引入 `AUTO_REJECT`
- 不改网关白名单、不新开 `/api/ai/v1/**`
- 不在本批做 AI 配置治理、独立复审、抽检代理（分别属批 2/3/4）
- 不改已封存的 v2.3 设计文档与归档目录

## 正式技术方案（已批准）

> 本节与 Design Wait 提案（`proposalHash=cf353fcd2f9cbc837a274fd5ec56dd9a8761570c74cec44068265e8a41790664`）
> 一致；批准依据为用户 2026-11-13 通过结构化提问明确选择「批准方案 A」，非 Agent 推断。
> 候选探索过程见 `.comet/brainstorm-summary.md`。

### 方案 A（已批准）

- **任务模型**：独立 `t_claim_task`，一案多行；`active_key = IF(生效, 拼接键, NULL)` 生成列 +
  `UNIQUE(tenant_id, active_key)`，终态行返回 NULL 不占唯一性；生成列不进实体。
- **并发领取**：唯一键兜底 + `@Version` 乐观锁；冲突映射 `SC_CLAIM_TASK_CONFLICT`。
  显式**不用** `SELECT ... FOR UPDATE`（项目架构规范禁用）。
- **分配器**：`t_claim_assign_rule` 按租户 / 险种 / 金额段 / 渠道 / 任务类型匹配，
  固定求解顺序 + 显式优先级，目标 `USER|DEPT|ROLE`，命中记录落库可审计。
- **归属时机**：写入期把结果写进任务行，查询期只读（D4）。
- **分页**：服务端用与列表同一过滤条件的 `COUNT(*)` 产出 `total`，前端只传 `page/size`，
  去掉固定 50 条切片。
- **灰度/回退**：`AUTO_ASSIGN` 开关；关闭时新任务以入池形态创建，读侧不依赖新表，
  旧 `{claimId}` 兼容层保留。
- **失败路径**：候选集全不可用 → `PENDING_CLAIM` + `assignee_id IS NULL` + 监控计数，
  不臆造归属；规则表空 → 全部入池而非报错；开关关闭 → 旧审核路径零改动。
- **取舍**：多两张表、一次增量迁移与清单登记；换来一案多任务、行级并发领取、
  可审计归属，以及批 2/3/4 可直接复用的归属事实。

### 已否方案（保留理由，避免后续重复引入）

| 方案 | 设想 | 否决理由 |
| --- | --- | --- |
| B | 规则引擎/表达式外置 + 查询期动态求解归属 | 同一任务在不同时刻归属漂移，破坏「归属是事实」；批 2 的 AI 参与归因与批 4 的超时回池会读到漂移结果；引入新依赖与不可回退的表达式面，可解释性与审计难度上升；与 `spec.md`「留下可审计的规则命中记录」及 D4 冲突 |
| C | 应用层先查后写 + 分布式锁，免生成列唯一键 | 并发下存在双写窗口，`spec.md`「并发双领取恰 1 成功」无法由数据库强保证；引入分布式锁运维面；项目规范禁 `SELECT ... FOR UPDATE`，退路更少 |

> 「查询层先修假分页、任务表延后」的拆分思路会使已批准批 1 的分配命中率验收无处落地，
> 并让批 2/3/4 的依赖悬空，属于范围变化；本轮不采用，维持用户已批准的 4 批拆分。

### Spec Patch

**无。** 本方案只在已批准范围内选择实现路径，不新增能力、不改公开行为、不改验收；
`specs/claim/task-assignment/spec.md` 保持原样，无需 Spec Patch 事先授权。

## Decisions

- **D1 任务表与案件表分离，而非在 `t_claim_case` 加归属列**：一案多任务需要独立主键、独立状态与并发领取语义；加列只能表达单任务，且会让历史行的归属含义含糊。备选「案件表加 JSON 列存任务数组」被否：无法做行级并发领取与唯一约束。
- **D2 用生成列 `active_key` + `UNIQUE(tenant_id, active_key)` 表达「同案同类型至多一条生效任务」**：MySQL 无 partial unique index，生成列在终态时返回 NULL 从而不占用唯一性。备选「应用层先查后写」被否：并发下会有双写窗口，且探针要求并发双领取仅 1 成功。生成列不进实体。
- **D3 规则表 + 固定求解顺序，不引入规则引擎**：匹配维度有限（租户/险种/金额段/渠道/任务类型）且要求可解释、可审计；引入 Drools 之类会带来新依赖与不可回退的表达式面。求解顺序固定、优先级显式，命中记录落库。
- **D4 写入期分配、不在查询期现算**：归属是事实，必须可审计；查询期计算会让同一任务在不同时刻归属不同，也会破坏批 2 的归因与批 4 的超时回池。分配器输出写进任务行，查询只读。
- **D5 `total` 由服务端同一 `COUNT(*)` 口径产出**：前端固定 50 条切片是数据正确性缺陷（§13.4），不是体验问题；前端只负责传 `page/size` 与渲染返回值。
- **D6 在途案件初始归属**：一次性回填脚本按「当前 `first_reviewer_id` → 归属人，其余入池」，不做编造指派；回填前先探测存量，若存在多条候选则打印清单并中止。此决策写入设计，不留作 Open Question，以免任务清单悄悄改变构建内容。
- **D7 迁移号按基线末尾空位分配，不抢号**：当前 `dev` 基线 `6f0ea427f` 的 `migration-manifest.json` 止于 `order=123`，`0124_synap_claim_ai_feedback_event.sql` 文件已在基线但登记由并行会话 staged 未入库（主树在途版 `executableCount=124` / `entries=131`）。本批 DDL 必须等其入库后追加到末尾空位，禁止插入中间、禁止回改已发迁移（migrate.py 按 sha256 记 journal）。
- **D8 等并行会话入库后再进 Build**：并行会话正新建 `ClaimWorkbenchController` / `ClaimSearch*` / `AiFeedback*` 并改 `ClaimErrorCodes` / `ClaimErrors` / `ClaimCaseRepository`；本批的「统一查询」与其重叠。用户已裁定等入库后开工，避免重复造轮子与同文件冲突。

## Risks / Trade-offs

| 风险 | 处理 | 验证判据 |
| --- | --- | --- |
| 并行会话占用同一批文件与迁移清单 | 用户裁定等其入库；开工前 `git status --short -- <精确路径>` 复核，绝不 `git add` 全量 | 复核记录入库零冲突 |
| 迁移清单撞号 / 清单与脚本不同批 | 清单是唯一真源，脚本与清单同批入库；`order` 追加末尾空位，`check-migration-manifest.py` 真实跑 | M1/M2/M3/M4/M11 全绿 |
| 已发迁移被回改 | 一律写新迁移；已应用的脚本连注释都不可改（sha256 journal 全环境校验） | 无 journal 校验和不符 |
| 新表列与活库不一致导致静默读空、整实体 save 抹空 | 实体 `@Column` 与 `information_schema` 求差；每个新仓储方法同批交 SQL 模板，SELECT 列对齐，模板首行必须是语句关键字 | `mvn -o compile` + 读回值探针 |
| 新增错误码漏登记导致类加载即抛 | `ClaimErrorCodes` + `ClaimErrors.DEFAULT_HTTP_STATUS` + 前端两语言包 + 错误码登记台账 §6，四处同批 | 启动期无异常 + 冲突错误码可辨认 |
| 前端雪花 ID 声明为 number | 所有 `id` / `claimId` / `taskId` 声明 `string`，输入框用纯数字正则 | `check.sh` 前端 ID 类型门禁 0 处 |
| 索引未命中导致 P95 不达标 | 三条索引按待办 / 部门池 / 生效唯一性分别覆盖 | `EXPLAIN` 三条均命中；1 万在办 100 次采样 P95 < 300ms |
| 门禁「恒绿」无判别力 | 变异验证：前端改回固定 50 条切片、`total` 改整表判断，两处都必须转红 | 变异 0 红即判失败 |
| 新增 worktree 位于仓库内被全仓扫描误扫 | Runtime 硬约束要求 worktree 留在主工作树内，用户选择保持现状；误扫红点在验证报告中单独归因 | 验证报告单列归因，不宣称全仓 `check.sh` exit 0 |

## Test Strategy

- **单元**：规则求解（命中 / 候选全不可用 / 优先级 / 金额段边界）、并发冲突映射、
  `COUNT(*)` 同源口径。
- **集成**：迁移后 `information_schema` 终态断言 + `SIGNAL`（`MESSAGE_TEXT` ≤128 字符）、
  启动期无 `TemplateNotFoundException`、仓储读回写入值、跨租户探针返回空。
- **性能与正确性探针**：137 条翻页（total、逐页和、ID 去重均为 137）、
  1 万在办 100 次采样 P95 < 300ms、并发双领取恰 1 成功。
- **负向与变异**：放开前端固定切片与整表 `total` 两处判据必须转红；回填多条候选即中止。
- **运行期证据**：写入 `docs/superpowers/reports/`，逐项带实测数值，不写未执行项；
  门禁红点按与本 change 的交集归因。

## Migration Plan

1. expand：新增迁移脚本建 `t_claim_task` / `t_claim_assign_rule` 与索引，末尾 `information_schema` 强断言 + `SIGNAL`（`MESSAGE_TEXT` ≤128 字符）；`bone-init.sql` 同批补建表块，清单同批登记。
2. 部署后代码仍走旧路径（旧代码对新表零感知）；打开 `AUTO_ASSIGN` 灰度，按 7 天窗口统计命中率 ≥0.90。
3. 回填在途案件：先探测、再按 D6 回填，终态断言。
4. contract：批 2/3/4 逐步改为读任务归属；`{claimId}` 兼容层保留到最后再评估下线。
5. 回滚：关闭 `AUTO_ASSIGN`，分配器停写、任务表保留不读，无需 DROP TABLE；不产生数据损失。

## Build 前停止条件

本 Design 完成**不等于**获准开工。进入 Build 前须同时满足：

1. 并行会话在途改动入库（`dev` HEAD 不再是 `6f0ea427f`，`ClaimWorkbenchController` /
   `ClaimSearch*` / `AiFeedback*` / `migration-manifest.json` 登记均已提交）；
2. 仓库原生全量门禁 `scripts/check.sh` 转绿，或红点明确归因为与本 change 零交集的基线项；
3. 用户另行确认本 change 的 Build 执行配置（`build_mode` / `tdd_mode` / `review_mode`），
   当前 `configurationReadiness.missingFields` 仍为 `build_mode`、`tdd_mode`。

用户 2026-11-13 已裁定「等并行会话入库、全量门禁转绿后才进 Build 写代码」，
故 `complete-design` 之后停在 Build 之前，不写实现代码、不发 Build 提案。
