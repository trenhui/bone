# ADR：数据库 DDL 真源（单轨）

| 项 | 内容 |
|----|------|
| **状态** | 已 supersede（2026-05-16） |
| **替代文档** | [**数据库开发规范.md**](./数据库开发规范.md) |

---

## 决策摘要

Bone 仅内部使用，**不维护 Flyway 增量迁移、不兼容历史库**。

| 项 | 结论 |
|----|------|
| 唯一可执行 DDL | 根目录 **`bone-init.sql`** |
| 变更方式 | 修改 init → `DROP DATABASE bone` → 重新执行 |
| Flyway | 各服务保持 `spring.flyway.enabled: false` |
| 双轨 / Vision | **废止**；`初始脚本.sql`、`DDL对齐说明.md` 仅作历史参考 |

详见 [数据库开发规范.md](./数据库开发规范.md)。
