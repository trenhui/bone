# ADR: 生产数据库增量迁移策略

## 状态
已接受（2026-06-19）

## 背景
ADR-数据库迁移与DDL真源 确立了 bone-init.sql 单轨真源，开发环境使用 DROP DATABASE + 全量重建。
但生产环境无法 DROP DATABASE，需要标准化的增量迁移方案。

## 决策
采用 expand/contract（扩展-收缩）模式进行生产环境增量迁移：

1. **Expand 阶段**：新增列时使用 nullable + DEFAULT，不破坏现有读写
2. **Migrate 阶段**：后台任务迁移历史数据
3. **Switch 阶段**：应用代码切换到新列
4. **Contract 阶段**：确认无引用后删除旧列

迁移脚本管理：
- 每次表结构变更编写 `scripts/migration/{version}_{description}.sql`
- 迁移脚本按版本号顺序执行
- 迁移前必须备份
- 迁移脚本纳入 Git 版本控制
- 大表变更使用 pt-online-schema-change 或 gh-ost

## 理由
- expand/contract 是零停机迁移的通用做法
- 相比 Flyway/Liquibase，手动脚本更灵活，适合复杂迁移
- 开发环境保持 bone-init.sql 全量重建，生产环境使用增量迁移

## 后果
- 需要运维人员审查每个迁移脚本
- 迁移脚本需要测试环境验证后才能上生产
- 未来如果迁移频繁，可考虑重新引入 Flyway 做自动化管理

## 合规
- 与 ADR-数据库迁移与DDL真源 互补：开发用全量，生产用增量
- bone-init.sql 仍为表结构唯一真源
- 迁移脚本必须与 bone-init.sql 最终状态一致
