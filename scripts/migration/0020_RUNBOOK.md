# 0020 — 唯一索引纳入 deleted 列（DBA 执行手册）

> 对应脚本：`scripts/migration/0020_soft_delete_unique_index.sql`
> 级别：**DDL 变更 L3；生产库执行属 L4，禁止 AI / 自动流程执行，须 DBA 经 gh-ost / pt-osc 执行。**
> 状态：**已就绪，待 DBA 授权执行**（2026-10-08 经审计校验：23 条唯一索引列集合与 `bone-init.sql` 完全一致，0 缺失 0 错位）。

## 1. 为什么必须做（影响面）

软删治理（`@Deleted` 已在我方 4 个提交 `51883821 / 04239108 / 7ed15c56 / 3ce3cd98` 落地）后，22 张业务表的唯一索引**无一含 `deleted` 列**。SDK `deleteById` 走软删（`UPDATE deleted=1`）后同值行仍在表内，导致"删掉 `code=ADMIN` 的角色 → 再也建不了新 ADMIN"。MySQL 无 partial index，业界标准解法是把 `deleted` 纳入唯一索引末端 `(cols..., deleted)`：因 `deleted` 仅 0/1，效果等价于"允许同值存在 1 条已删记录"，足以支撑软删后重建，且保持活跃数据唯一。

## 2. ⚠️ 上线铁律：顺序不可反

**必须先应用本 DDL，再部署上述 4 个提交。** 顺序反了会直接上线一个"删了建不回来"的缺陷。

```
[t0] 备份（见 §4）
[t1] 应用 0020 DDL（索引纳入 deleted）
[t2] 部署 4 个软删提交（代码侧 @Deleted 生效）
[t3] 验收（§5）
```

## 3. 执行（在线变更，推荐 gh-ost / pt-osc）

脚本自带幂等（`DROP INDEX IF EXISTS` + 按 information_schema 判定列集合，`@need_*` 为 false 时退化为 `SELECT 1`），可复跑。
前置校验段会对每张表查"同 UK 同 deleted 重复行"，若历史数据已被物理删除逻辑破坏（重复 > 0），脚本 `SIGNAL` 报错而非静默加索引失败 —— 此时**先人工清理重复行再执行**。

### 3.1 生产（大表，走在线变更工具）

对 22 张表逐张或分批（建议按影响面排序：先小表后大表）：

```bash
# 以 gh-ost 为例，单表模板（替换为实际表名与索引定义）
gh-ost \
  --database=bone \
  --table=iam_role \
  --alter="DROP INDEX uk_iam_role_code, ADD UNIQUE KEY uk_iam_role_code (tenant_id, code, deleted)" \
  --execute
```

> 等价 pt-osc：`pt-online-schema-change --alter "DROP INDEX uk_iam_role_code, ADD UNIQUE KEY uk_iam_role_code (tenant_id, code, deleted)" D=bone,t=iam_role --execute`
> 22 张表的完整 (表, 索引, 列) 映射见 `0020_soft_delete_unique_index.sql` 阶段 2，已与 `bone-init.sql` 校验一致。

### 3.2 开发 / 测试库（可直跑脚本）

```bash
mysql -h<host> -u<user> -p<pass> bone < scripts/migration/0020_soft_delete_unique_index.sql
```

## 4. 备份（回滚前置）

```sql
-- 整库逻辑备份（推荐）
mysqldump -h<host> -u<user> -p<pass> --single-transaction bone > bone_pre_0020_$(date +%Y%m%d).sql
-- 或仅备份受影响的 22 张表索引形态，便于核对回滚
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema='bone' AND index_name LIKE 'uk_%'
GROUP BY table_name, index_name INTO OUTFILE '/tmp/uk_before_0020.csv';
```

## 5. 验收

```sql
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND index_name LIKE 'uk_%'
  AND table_name IN ('iam_role','iam_account','mdm_record','sys_config',
                     'sys_dict_item','sys_dict_type','gen_code_template', /* ... 其余 22 张 ... */)
GROUP BY table_name, index_name;
-- 期望：23 条索引的最后一列均为 deleted。
```

并补一条业务验证：对任一表软删一条 `code=X` 的记录后，应能再插入一条 `code=X` 的活跃记录（不再报唯一约束冲突）。

## 6. 回滚

反向改回 `(cols...)`（不含 deleted）：

```sql
ALTER TABLE `iam_role` DROP INDEX `uk_iam_role_code`, ADD UNIQUE KEY `uk_iam_role_code` (tenant_id, code);
```

⚠️ **回滚前务必备份**：若回滚前已存在"软删 + 同值新建"的记录，反向重建索引会因重复而失败，须先清理这些行。
