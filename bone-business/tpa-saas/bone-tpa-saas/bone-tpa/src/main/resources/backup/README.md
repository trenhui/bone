# `backup/` 配置说明（安全基线）

本目录下的 `application-*.yaml` **仅为脱敏模板**，用于说明可配置项与推荐的环境变量命名。

## 禁止事项

- 禁止提交真实 **数据库密码、Redis 密码、云 AccessKey、JWT secret、内网地址** 等到 Git。  
- 历史若曾误提交，须在密钥轮转后使用 `git filter-repo` 等工具从历史中清除（并视为密钥已泄露）。

## 推荐用法（12-Factor / 机密外置）

1. 在本地复制一份到 **已被 `.gitignore` 忽略** 的路径，例如 `~/.bone/tpa-application-local.yaml`。  
2. 启动时通过 `SPRING_CONFIG_ADDITIONAL_LOCATION=file:~/.bone/` 或 Kubernetes `Secret` / CI 变量注入。  
3. 所有敏感项使用 **环境变量占位**（模板中 `${VAR:default}`），生产环境 **不设 default** 或强制校验非空。

## 环境变量速查

| 变量 | 用途 |
|------|------|
| `TPA_DB_URL` / `TPA_DB_USERNAME` / `TPA_DB_PASSWORD` | 主数据源 |
| `TPA_REDIS_HOST` / `TPA_REDIS_PORT` / `TPA_REDIS_PASSWORD` | Redis |
| `METADATA_SDK_URL` / `METADATA_SDK_SECRET` / `METADATA_APPCODE` | 元数据 SDK |
| `SA_TOKEN_SECRET_KEY` | Sa-Token 签名 |
| `ALIYUN_OSS_*` | 对象存储（若启用 oss 段） |
| `KT_ACCESS_KEY` / `KT_ACCESS_SECRET` | 快瞳 OCR（`kt.*` → `KTProperties`） |
| `TPA_API_URL` | TPA 对外 API 基址 |

具体键名以各 `application-*.yaml` 内注释为准。

### 快瞳（kt）配置示例

```yaml
kt:
  access-key: ${KT_ACCESS_KEY:}
  access-secret: ${KT_ACCESS_SECRET:}
```

本地启动可设置环境变量或 `SPRING_CONFIG_ADDITIONAL_LOCATION` 指向含真实值的私有 yaml（勿提交 Git）。
