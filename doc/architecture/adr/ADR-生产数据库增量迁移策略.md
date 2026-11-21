# ADR: 生产数据库增量迁移策略

## 状态
已接受（2026-06-19）；2026-11-21 增补「迁移工具分层定位」，澄清与 Flyway 的关系（不改变原决策）

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
- 本项目当前阶段选择「自研脚本 + 账本」而非 Flyway/Liquibase：脚本按 manifest 显式定序，可表达大表 gh-ost/pt-osc、多租户同库等复杂场景；这只是**当前阶段的取舍**，不代表否决 Flyway
- 开发环境保持 bone-init.sql 全量重建，生产环境使用增量迁移

## 迁移工具分层定位（2026-11-21 澄清）

bone 的 DDL 真源与迁移工具按环境分层，各司其职、互不替代：

| 层 | 场景 | 工具 / 真源 | 说明 |
|----|------|-------------|------|
| 本地开发 | 空库一键重建 | `bone-init.sql` 全量 | 唯一可执行 DDL 真源（见 ADR-0001）；`DROP DATABASE` 后重建 |
| 生产 / 存量演进 | 已有库增量、零停机 | `scripts/migration/*.sql` + `scripts/migrate.py` | 按 `migration-manifest.json` 定序，`schema_migration_journal` 记 sha256 账本，M1–M11 门禁治理 |
| 备选（未启用） | 未来自动化迁移 | Flyway | **已评估、当前不采用**；方案草案见 `db/migration/README.md`（L3 待审批） |

- 三者的关系是**分层互补**，不是竞争真源：bone-init.sql 是结构真源，增量脚本是演进手段，Flyway 是尚未采用的备选工具。
- 本决策不排除未来引入 Flyway，但届时必须以新 ADR supersede 本条，并明确账本迁移（journal → flyway_schema_history）、manifest 废止、init 归档三项前置条件，避免双真源并存。

## 后果
- 需要运维人员审查每个迁移脚本
- 迁移脚本需要测试环境验证后才能上生产
- 若未来迁移规模显著扩大，可按上述前置条件评估切换 Flyway（草案与待办已在 `db/migration/README.md` 登记）

## 合规
- 与 ADR-0001（数据库 DDL 单轨真源）互补：开发用全量，生产用增量
- bone-init.sql 仍为表结构唯一真源
- 迁移脚本必须与 bone-init.sql 最终状态一致
- 本 ADR 不授权启用 Flyway；各服务保持 `spring.flyway.enabled: false`（见 `数据库开发规范.md`）
