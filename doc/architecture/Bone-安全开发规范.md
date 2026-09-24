# Bone 平台安全开发规范

> **文档性质**：应用安全、身份、密钥、依赖与插件沙箱的**工程门禁**（与 IAM 产品能力解耦）。  
> **更新**：2026-05-17  
> **关联**：[Bone-API-规范.md](./Bone-API-规范.md) §9、[Bone-日志规范.md](./Bone-日志规范.md) §7、[Bone-配置与环境规范.md](./Bone-配置与环境规范.md)

---

## 1. 参考标准

| 标准 | Bone 采纳 |
|------|-----------|
| [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/) | L2 为平台默认目标 |
| [OWASP Top 10](https://owasp.org/www-project-top-ten/) | PR 自查与威胁建模输入 |
| [OWASP API Security Top 10](https://owasp.org/API-Security/) | BOLA、认证、速率限制 |
| NIST SP 800-63B | 密码策略、会话 |
| Zero Trust | 默认拒绝、最小权限、持续校验 |

---

## 2. 认证与会话

| 规则 | 说明 |
|------|------|
| 默认拒绝 | 除白名单外所有 API 需认证 |
| JWT | **As-Is**：`bone.iam.jwt` HS256 **对称密钥**，生产密钥 ≥256 bit 随机，仅环境变量。**Target**：RS256/EdDSA + JWKS（公钥经 `/.well-known/jwks.json` 分发；HS256 仅保留 dev profile，生产 fail-fast 禁用）——见 [ADR-0005](./adr/0005-iam-jwt-rs256-jwks.md)（状态：提议，**待裁决**） |
| 令牌传输 | 仅 `Authorization: Bearer`；禁止 query string 带 token |
| 过期 | Access 短 TTL；Refresh 独立端点 + 轮换（见 bone-iam） |
| 登出 | 服务端黑名单/版本号失效 refresh（已实现方向） |

**白名单示例**：`/api/v1/iam/login`、健康检查；生产 Swagger 需鉴权或关闭。

---

## 3. 授权与多租户

| 规则 | 说明 |
|------|------|
| Scope | `{domain}:{resource}:{action}`，与 OpenAPI、IAM 一致（API 规范 §9.2） |
| 租户 | `tenant_id` / `biz_identity_code` **仅从 JWT/网关注入**，禁止仅靠 body 覆盖（见 [Bone-多租户规范](./Bone-多租户规范.md)） |
| BOLA | 资源 ID 访问必须校验租户归属 |
| 默认角色 | 生产禁用 `admin/123456`；首次部署强制改密 |

---

## 4. 密钥与敏感配置

| 禁止 | 必须 |
|------|------|
| `src/main/resources` 提交真实密码、AK/SK、JWT secret | `${BONE_*}` 占位 + `.env`（见配置规范） |
| 日志打印 Token、密码、连接串 | Gitleaks CI + `.gitleaks.toml` |
| 前端打包密钥 | 仅 public 配置进 Vite `import.meta.env` |

轮换：密钥泄露须 **立即轮换** + 审计；历史见 [doc/wiki/09-密钥与Git历史.md](../wiki/09-密钥与Git历史.md)。

---

## 5. 输入与输出安全

| 面 | 规则 |
|----|------|
| 校验 | Bean Validation + 白名单枚举；失败 `COMMON_VALIDATION_FAILED` |
| SQL | 参数化 / Metadata SDK；禁止拼接用户输入 |
| 错误响应 | `detail` 无堆栈/SQL（[Bone-错误码登记](./Bone-错误码登记.md)） |
| 文件上传 | MIME/扩展名白名单、大小限制、checksum（API §14.4） |
| 路径 | 禁止返回服务器绝对路径 |

---

## 6. 扩展插件沙箱（EXT）

| 规则 | 说明 |
|------|------|
| 隔离 | 插件在受控 ClassLoader / 进程；禁止任意反射访问平台 Bean |
| 制品 | JAR 校验和、大小上限（`EXT_ARTIFACT_*`） |
| 网络 | 默认禁止插件出站；若开放须显式配置 + 审计 |
| 超时 | 沙箱执行超时 → `EXT_SANDBOX_TIMEOUT` |

---

## 7. 传输与 CORS

| 环境 | 规则 |
|------|------|
| 生产 | HTTPS only；HSTS（网关） |
| 开发 | Vite 代理 CORS 可放宽 |
| 生产 CORS | 明确域名白名单，禁止 `*` + credentials |

---

## 8. 依赖与供应链

| 项 | 规则 |
|----|------|
| 版本 | 锁定 `bone-parent` BOM |
| 扫描 | CI `backend-security`（OWASP Dependency-Check） |
| 升级 | 高危 CVE **7 天内**评估修复或 ADR 接受风险 |

---

## 9. 安全头（网关 / Spring Security）

推荐响应头（生产）：

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'   # 按前端资源调整
Cache-Control: no-store                      # API 响应
```

---

## 10. PR 检查清单

- [ ] 无新增明文密钥  
- [ ] 新 API 有认证/Scope  
- [ ] 租户隔离查询条件  
- [ ] 错误响应无敏感信息  
- [ ] 上传/插件路径有校验  
- [ ] 依赖无未登记高危 CVE  

---

## 11. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 初版：从 API 规范 §9 与总体架构安全章抽离 |
