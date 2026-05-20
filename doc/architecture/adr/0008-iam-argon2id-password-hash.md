# ADR-0008：密码哈希 BCrypt → Argon2id 迁移（ADR-IAM-007）

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-05-20 |
| **决策者** | 安全组（待评审） |
| **关联** | IAM 详设 §7.2.1、NIST SP 800-63B |

---

## 背景

As-Is 使用 BCrypt（cost≥10）。OWASP / NIST 推荐 memory-hard 算法（Argon2id）对抗 GPU 破解。

## 决策（提议）

1. 新密码使用 **Argon2id**（参数经基准测试文档化）。
2. 登录时若 `password_hash` 前缀识别为 BCrypt，验证成功后 **透明重哈希** 为 Argon2id。
3. 取消「强制 90 天改密」；改为 breach 驱动 + 弱口令字典（NIST 800-63B）。

## 理由

- 提升存储侧安全裕度。
- 渐进迁移无需全员重置密码。

## 合规与迁移

- `PasswordEncoder` 链式实现 + 单元测试向量。
- 更新 IAM 详设 §2.1 密码规则与 PRD 验收口径。
