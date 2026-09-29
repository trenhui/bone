# PR：为 0010 / 0011 回填脚本补充回滚阶段（异常一键还原）

> **一键开 PR（base=`dev`，已预填）**：https://github.com/trenhui/bone/compare/dev...pr/0010-0011-rollback?expand=1
> **或 CLI**：`gh auth login && gh pr create --base dev --head pr/0010-0011-rollback --title "docs(migration): 为 0010/0011 回填脚本补充回滚阶段（异常一键还原）" --body-file PR_0010_0011_rollback.md`

> 分支：`pr/0010-0011-rollback`（已推 origin，基于 `dev`，相对 `dev` 仅 2 个 SQL 文件 +19 行）
> 提交：`cd554bca7`
> 门禁状态：**L3 待架构师签字**（runbook 完善，不改变 DDL/数据结构语义）；**L4 仅写脚本、不执行**（由运维在目标库执行）

## 背景

`scripts/migration/0010_iam_quota_counter_backfill.sql`（iam 配额占用计数回填）与
`scripts/migration/0011_generator_tenant_backfill.sql`（studio-generator 租户回填）此前只有
「阶段0 备份 → 更新 → 校验」，缺少**异常还原**步骤。一旦阶段2/阶段3 之后发现数据异常，运维只能手工反推，
风险高、易误操作。本次为两个脚本补齐回滚阶段，做到「异常即还原、无需重跑」。

## 变更明细

### `scripts/migration/0010_iam_quota_counter_backfill.sql`
- 新增 **阶段 5：回滚（异常时使用）**（行 57–62）。
- 仅还原两个占用计数列，不动其它字段：
  ```sql
  UPDATE iam_tenant t
  JOIN bak_iam_tenant_0010 b ON b.id = t.id
  SET t.allocated_accounts = b.allocated_accounts,
      t.allocated_roles    = b.allocated_roles;
  ```
- 应急兜底（阶段0 未执行/备份表缺失时）：计数全置 0，再由业务重新触发核对——不推荐，会丢失真实在用量快照。

### `scripts/migration/0011_generator_tenant_backfill.sql`
- 新增 **阶段 9：回滚（异常时使用）**（行 97–105）。
- 逐表用阶段0 备份还原 `tenant_id`（6 张表）：
  ```sql
  UPDATE gen_data_source d             JOIN bak_gen_data_source_0011 b             ON b.id=d.id             SET d.tenant_id=b.tenant_id;
  UPDATE gen_table_metadata m          JOIN bak_gen_table_metadata_0011 b          ON b.id=m.id             SET m.tenant_id=b.tenant_id;
  UPDATE gen_column_metadata c         JOIN bak_gen_column_metadata_0011 b         ON b.id=c.id             SET c.tenant_id=b.tenant_id;
  UPDATE gen_code_template t           JOIN bak_gen_code_template_0011 b           ON b.id=t.id             SET t.tenant_id=b.tenant_id;
  UPDATE gen_generation_task g         JOIN bak_gen_generation_task_0011 b         ON b.id=g.id             SET g.tenant_id=b.tenant_id;
  UPDATE gen_code_generation_history h JOIN bak_gen_code_generation_history_0011 b ON b.id=h.id            SET h.tenant_id=b.tenant_id;
  ```
- 回滚会把 `tenant_id` 还原为回填前的值（含用户自建数据回到 0），**回滚后须重启 studio-generator 新版本实例**。

## 验证

本地预提交钩子 7 项全绿：
- ✅ Spotless（全树 `spotless:check`）
- ✅ ORM 框架拦截
- ✅ 密钥泄露扫描（gitleaks 本机未装 → 跳过；HC-004 实测 Manual）
- ✅ pom.xml 依赖检查
- ✅ i18n 同步校验（errorCode ↔ 台账 ↔ 语言包）
- ✅ HC-006 绕过 SDK 的 JDBC/MyBatis 扫描（bypass imports 14/14）

> 注：本次提交仅含两个迁移脚本的回滚段；同工作树中 `bone-file`、`studio-generator` 的其它在途文件
> 属并行编辑者，未并入本次提交，留由其 owner 收口。

## 风险与待办（非本次提交范围）

1. **L3 签字**：合并前需架构师审批（DDL/数据操作 runbook 完善）。
2. **L4 执行**：0010 / 0011 由运维在目标库执行，跑前**务必确认阶段0 备份表已生成**
   （`bak_iam_tenant_0010`、`bak_gen_*_0011`），否则回滚语句无据可依。
3. **0010 依赖**：回填期间建议 iam 服务临时禁写账号/角色增删，避免与在线写入竞争 `allocated_*`；
   阶段4 若发现 `allocated_* > max_*`，须先上调配额或清理再放行使新建（否则被 `@Version` 乐观锁拒）。
4. **0011 依赖**：运行实例必须是含 `TenantContext` 口径的新版本；回填靠 `created_by` 反查，
   执行前确认 `iam_account.id ↔ tenant_id` 完整。`created_by` 为空/父记录缺失的孤儿行留在 `tenant_id=0`，需业务确认归属。

## 评审清单

- [ ] 架构师已签字（L3）
- [ ] 目标库执行计划已排期（L4，含备份表预检）
- [ ] 0010 / 0011 备份表命名与回滚语句中的表名一致
- [ ] 0011 回滚后重启 studio-generator 新版本实例的步骤已写入执行 runbook

## 如何创建 PR

分支 `pr/0010-0011-rollback` 已推送到 origin，精确指向 `cd554bca7`，相对 `dev` 的 diff 仅 2 个 SQL 文件（+19 行）。

### 方式 A：GitHub CLI
```bash
gh auth login   # 仅需一次，浏览器授权
gh pr create --base dev --head pr/0010-0011-rollback \
  --title "docs(migration): 为 0010/0011 回填脚本补充回滚阶段（异常一键还原）" \
  --body-file PR_0010_0011_rollback.md
```

### 方式 B：GitHub 网页端
在 `trenhui/bone` 仓库 → New Pull Request → base 选 `dev`、compare 选 `pr/0010-0011-rollback`，正文粘贴本文件内容即可。

> ⚠️ base 必须选 `dev`。若误选 `release/mvp-v1.0`，diff 会膨胀到 83 文件（整条 nightly 特性都被带出），不符合本次「仅回填脚本回滚」的意图。
