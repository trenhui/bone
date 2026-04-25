---
name: db-migration
description: 数据库迁移脚本生成技能包
---

## 职责
- 根据数据模型变更生成 Flyway/Liquibase 迁移脚本
- 检查破坏性变更（DROP TABLE, ALTER COLUMN 等）
- 生成回滚脚本（如果需要）
- 验证 SQL 语法正确性

## 规范
- 迁移脚本文件名遵循 Flyway 约定：`V{version}__{description}.sql`
- 每个功能一个迁移版本
- 只新增不修改已应用的迁移
- 包含必要的索引和外键约束

## 检查项
### 安全检查
- [ ] 不直接删除已有列（先加新列，数据迁移，再删除旧列）
- [ ] 添加 NOT NULL 列时要有默认值
- [ ] 大表加索引使用 `ALTER ONLINE` 或避免锁表
- [ ] 数据变更有事务保护

### 破坏性变更需要人工确认
- DROP TABLE / DROP COLUMN
- ALTER COLUMN 修改数据类型
- RENAME TABLE / RENAME COLUMN
- 清空表数据 TRUNCATE

## 输出
- 迁移脚本放到 `src/main/resources/db/migration/`
- 注释说明变更内容
- 如果是 L2 变更，生成回滚脚本 `U{version}__{description}.sql`
