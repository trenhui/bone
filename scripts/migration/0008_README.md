# 0008 — `custom_entity_name` 存量驼峰收敛：两种执行路线

## 问题
`GenTableMetadata.toEntityName` 已实现「剥离 `t_` 前缀 + 下划线转大驼峰」，`create()` 会调用；但 `updateFrom()` 故意不覆盖 `custom_entity_name`（该字段语义是"用户可自定义实体名"，每次同步都覆盖会丢用户改名）。因此 `toEntityName` 落地前同步的存量行仍保留原始表名，生成产物出现 `public class bone_application`、包名含下划线（可编译但不规范）。

**只修正 `custom_entity_name = original_table_name` 的行**（即从未被用户改名的）；用户已自定义的行（如 `MyOrder`）保持不动。两条路线逻辑完全一致。

## 路线 A：纯 SQL 直跑（本文件 `0008_generator_entity_name_pascal.sql`）
- 适合：有 `mysql` 客户端 + 具备 `CREATE FUNCTION` 权限（`SUPER` 或 `log_bin_trust_function_creators=ON`）。
- 特点：自带阶段 0 自动备份 `bak_gen_table_metadata_0008`，阶段 3 校验，阶段 4 清理临时函数。
- 执行：
  ```bash
  mysql -h <host> -u <user> -p <bone_db> < 0008_generator_entity_name_pascal.sql
  ```
- 不满足权限时**不要强行执行**，改用路线 B。

## 路线 B：应用层一次性端点（推荐，免 DB 权限）
- 端点：`POST /api/v1/generator/admin/repair-entity-names`
  - 默认 `execute=false` 仅预览；带 `{"execute":true}` 才写入。
  - 支持 `{"dataSourceId":"<dsId>"}` 限定单数据源；幂等可重复。
- 配套脚本：`scripts/repair-entity-names.sh`（依赖 `curl` + `python3`）
  ```bash
  ./scripts/repair-entity-names.sh            # 预览→确认→执行→复核
  ./scripts/repair-entity-names.sh -d ds-abc  # 仅单数据源
  ./scripts/repair-entity-names.sh -y         # 非交互直接执行
  ```
- 适合：无 `mysql` 客户端 / 无 `CREATE FUNCTION` 权限 / 想走可审计、可预览、可回滚（重新同步即还原语义）的应用通道。
- 前提：需先重新部署含该端点的 `studio-generator`。

## 如何选择
| 条件 | 选 |
|---|---|
| 有 mysql 客户端且可建函数 | A（最轻量，自带备份） |
| 无客户端 / 无建函数权限 / 走应用审计通道 | B（端点 + 脚本） |

两条路线结果等价；**不要同时跑**，B 执行后 A 的 `WHERE custom_entity_name = original_table_name` 将命中 0 行，属预期。
