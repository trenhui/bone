# integration L4 租户回填 —— DBA 执行手册

> 配套脚本：`integration_tenant_backfill.sql`（同目录）。本手册说明**执行顺序、前置、步骤、校验、回滚**。
> ⚠️ 本脚本 AI 无法替跑（无生产库连接）；须由持有 prod 权限的 DBA 在安全窗口执行。

---

## 1. 为什么必须做（顺序约束）

代码 B（5 个聚合 `extends TenantAggregateRoot<Long>`）上线后，`bone-metadata-sdk` 会按 `tenant_id`
**自动过滤**租户数据。生产 `int_*` 表中存量 `tenant_id = 0`（平台默认租户 / 历史无租户归属）的行，
在 B 上线后会被过滤成**对所有租户不可见**——表现为“存量流程、执行日志消失”。

**固定顺序：先回填（本脚本）→ 再上线 B。** 顺序颠倒 = 历史数据不可恢复地“消失”。

## 2. 前置 checklist（执行前逐项确认）

- [ ] 已与业务确认一个**真实、非 0** 的兜底租户 ID（无法按 `created_by` 解析归属的行的去处）。
- [ ] 代码 B **尚未**上线（或本次发布窗口安排在回填之后）。
- [ ] 已对 `int_flow / int_flow_node / int_flow_connection / int_connector / int_execution_log` 做全量备份
      （`CREATE TABLE ..._bak AS SELECT * FROM ...` 或物理备份）。
- [ ] 已在**低峰 / 维护窗口**执行；int_* 表行数已知，预计 UPDATE 影响行数可控。
- [ ] 已确认 prod 中 `iam_account` 表存在且含 `id` / `tenant_id` 列（据 `bone-init.sql` DDL 真源，物理表为 `iam_account`，历史 `iam_user` 命名已淘汰）。

## 3. 执行步骤

### 3.1 设置兜底租户（必改）
打开 `integration_tenant_backfill.sql`，把第 0 步的
```sql
SET @default_tenant = 0;  -- TODO(DBA): 替换为业务确认的兜底租户 ID（非 0）
```
改为业务确认的**非 0** 租户 ID。若仍为 0，步骤 2/4 退化为“不回填”，B 上线后相关行仍不可见。

### 3.2 预检计数（步骤 0，去掉注释执行）
确认待回填行数。若全为 0，则无需执行，直接结束。

### 3.3 回填（依次执行步骤 1 → 4）
- **步骤 1**：`int_flow` 按 `created_by` → `iam_account.tenant_id` 回填（仅能解析的行）。
- **步骤 2**：剩余 `int_flow`（解析不了）→ 兜底租户 `@default_tenant`。
- **步骤 3**：级联子表 `int_flow_node` / `int_flow_connection` / `int_execution_log`，按所属 `flow_id` 的 `tenant_id`。
- **步骤 4**：`int_connector` 先按 `created_by` 解析，失败 → 兜底租户。

> 顺序不可乱：必须先回填 `int_flow`（父），再级联子表，否则子表拿不到父表归属。

### 3.4 后校验（步骤 5，去掉注释执行）
5 张表 `tenant_id = 0 OR IS NULL` 的计数**应全部为 0**。若仍有残留：
- 多为 `@default_tenant` 未正确设置（仍为 0）→ 重设后重跑步骤 2/4；
- 或 `created_by` 指向不存在的 `iam_account` → 业务确认兜底租户后重跑。

## 4. 回滚

若回填错误，用文件末尾 `integration_tenant_backfill_rollback.sql` 思路：先恢复备份表，再按 `id` 关联
`UPDATE ... SET tenant_id = 0`。**必须在 B 上线前**回滚，否则 0 值行再次被 SDK 过滤。

## 5. 与代码 B 的协同发布

| 顺序 | 动作 | 风险 |
|------|------|------|
| 1 | DBA 执行本回填脚本（含后校验通过） | 无（仅更新 0/NULL 行） |
| 2 | 上线代码 B（5 聚合 TenantAggregateRoot） | B 上线后 SDK 按 tenant_id 过滤，已回填行正常可见 |
| 3 | （可选）下线兜底租户内的历史行清理 | — |

**禁止**：先上线 B 再回填 —— 历史 0 值行将不可见且难以定位。

## 6. 已知假设（执行前请 DBA 复核）

- `int_flow.created_by` / `int_connector.created_by` 关联 `iam_account.id`（据 `bone-init.sql`）。
- `iam_account.tenant_id` 为归属租户；管理员/平台账号 `tenant_id = 0` 时，其创建的流程也会落 0，
  最终归入兜底租户（业务需确认是否可接受）。
- `int_execution_log` 无 `created_by`，完全依赖 `flow_id` 级联，故必须等步骤 1 完成后再跑步骤 3。
