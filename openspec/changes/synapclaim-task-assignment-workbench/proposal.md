## Why

作业台目前只有「案件 → 初审人」的单一归属列，一案多任务、按人/按组/按角色的自动分配、无人可派回落与「我的待办」均缺位；核赔页还以固定 50 条切片充当分页，`total` 与真实总数不一致，属数据正确性缺陷而非体验问题。已归档的 v2.3 深度设计（§13.5）把这部分列为四批落地的第一批，本 change 把它落为可运行代码。

## What Changes

- 新增 `t_claim_task`（一案多任务）与 `t_claim_assign_rule`（匹配规则表），增量迁移脚本与 `migration-manifest.json` 登记、全量基线 `bone-init.sql` 同批入库
- 新增分配器：按租户 / 险种 / 金额段 / 渠道 / 任务类型匹配规则，候选打分后命中即派；候选集全不可用则任务入池，可由任意持权人自领
- 新增「我的待办」与统一查询入口；修正假分页，`total` 取自与列表同一 `COUNT(*)` 口径，禁止前端固定切片
- 新增 `AUTO_ASSIGN` 回退开关：关闭后分配器停写，任务表保留不读
- 保留 `{claimId}` 路径为兼容层，内部映射 `task_no`；不引入 `org_id`，作业组真源为 IAM `iam_dept`

## Capabilities

### New Capabilities
- `claim/task-assignment`: 理赔任务的创建、规则驱动自动分配、无人可派回落入池、并发自领互斥、我的待办查询与待办分页总数正确性

### Modified Capabilities
<!-- 无：本批不改动既有 spec 级行为 -->

## Impact

- 代码：`bone-apps/synap-claim` 任务域新增实体 / 仓储接口 / SQL 模板 / 分配器 / 待办端点；`bone-frontend` 核赔与待办页去掉固定切片
- 数据：新增 2 张表与 3 条索引（`idx_tenant_assignee_status`、`idx_tenant_dept_status`、`uk_tenant_task_alive` 生成列唯一键）
- 门禁：`scripts/migration/migration-manifest.json` 追加登记；`check-migration-manifest.py`、DDL 文档一致性、E2E 造数同步须真实通过
- 依赖：批 2（AI 配置治理）、批 3（独立复审）、批 4（抽检与代理）均以本批的任务归属为前置；不改网关白名单、不改既有审核状态机
