-- Bone IAM 模块 Flyway 基线迁移脚本
-- 注意：首次引入 Flyway 时，若数据库已存在历史数据，请执行：
--   mvn flyway:baseline -Dflyway.baselineVersion=1
-- 或直接在数据库中创建 flyway_schema_history 表并插入 baseline 记录。

-- 本文件为占位符，实际表结构已在 bone-init.sql 中定义。
-- 后续所有 IAM 相关的 DDL/DML 变更必须在此目录下按版本号递增添加新脚本，例如：
--   V2__add_user_force_change_password.sql
--   V3__add_role_data_scope.sql
