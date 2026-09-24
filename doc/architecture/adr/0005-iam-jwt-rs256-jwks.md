# ADR-0005：JWT HS256 → RS256/EdDSA + JWKS 轮换（ADR-IAM-004）

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-05-20 |
| **决策者** | 架构组 + 安全组（待评审） |
| **关联** | IAM 详设 §7.2.2、[RFC 8725](https://datatracker.ietf.org/doc/html/rfc8725) |

---

## 背景

As-Is 使用 HS256 + 单 `secret`（多服务共享验签风险）。密码学 BCP 推荐非对称签名 + `kid` 轮换。

## 决策（提议）

1. **[Target]** 签发方 `bone-iam` 使用 **RS256**（或 EdDSA），私钥 KMS/文件外置。
2. 消费方通过 **`/.well-known/jwks.json`** 拉取公钥；支持 **双 `kid` 重叠窗口** 轮换。
3. Access Token TTL 缩短至 **≤15min**；Refresh 轮换 + 复用检测（`replaced_by`）强制启用。
4. HS256 仅保留 dev profile，**生产 fail-fast** 禁用。

## 理由

- 降低密钥泄露 blast radius（消费方无需持私钥）。
- 满足 OWASP ASVS V3 与 RFC 8725。

## 后果

- 所有微服务须升级 JWT 解析库支持 JWKS 缓存。

## 合规与迁移

1. 发布 JWKS 端点 + 文档化 claim 集。
2. 网关与各服务并行支持 HS256/RS256 双轨 ≥2 周。
3. 下线 HS256。
