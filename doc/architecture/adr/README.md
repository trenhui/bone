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
| [0002](./0002-iam-rbac-jwt-authorities.md) | IAM：RBAC + JWT 权限码（ADR-IAM-001） | 已接受 |
| [0003](./0003-iam-five-layer-policy.md) | IAM：五层权限模型分阶段（ADR-IAM-002） | 提议 |
| [0004](./0004-iam-authz-service-evolution.md) | IAM：PolicyEvaluator → authz-service（ADR-IAM-003） | 提议 |
| [0005](./0005-iam-jwt-rs256-jwks.md) | IAM：JWT RS256 + JWKS 轮换（ADR-IAM-004） | 提议 |
| [0006](./0006-iam-tenant-isolation-modes.md) | IAM：租户隔离三模式（ADR-IAM-005） | 提议 |
| [0007](./0007-iam-platform-audit-bus.md) | IAM：平台审计总线 + WORM（ADR-IAM-006） | 提议 |
| [0008](./0008-iam-argon2id-password-hash.md) | IAM：Argon2id 密码哈希（ADR-IAM-007） | 提议 |
| [0009](./0009-iam-audit-log-schema-split.md) | IAM：审计表 vs 审计总线职责（ADR-IAM-008） | 提议 |
| [0010](./0010-iam-account-direct-permission.md) | IAM：账号直连权限 MVP 范围（ADR-IAM-009） | 提议 |
| [0002-md](./0002-masterdata-catalog-sync.md) | `meta_*` → `mdm_*` 同步范式（复制 + 来源追溯） | 已接受 |

> **编号冲突**：`0002-iam-rbac-jwt-authorities` 与 `0002-masterdata-catalog-sync` 共用序号 `0002`。引用时请用**完整文件名**；后续治理 PR 可将主数据 ADR 重编号为 `0011`（须更新全库链指）。

## 关联

- [数据库开发规范.md](../数据库开发规范.md) — DDL 操作细则  
- [Bone-版本与发布规范.md](../Bone-版本与发布规范.md) — 发布与回滚  
