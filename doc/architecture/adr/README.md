# 架构决策记录（ADR）

本目录存放 **Architecture Decision Records**：不可轻易推翻的技术决策及其背景。

## 流程

1. 复制 [0000-template.md](./0000-template.md) 为 `NNNN-short-title.md`  
2. PR 评审：架构师 / TL 确认  
3. 状态：`提议` → `已接受` → `已废弃`（须链到替代 ADR）  
4. 被替代的旧 ADR 保留文件，文首标注 **superseded**

## 索引

| ID | 标题 | 状态 |
|----|------|------|
| [0001](./0001-database-ddl-single-source.md) | 数据库 DDL 单轨真源（`bone-init.sql`） | 已接受 |

## 关联

- [数据库开发规范.md](../数据库开发规范.md) — DDL 操作细则  
- [Bone-版本与发布规范.md](../Bone-版本与发布规范.md) — 发布与回滚  
