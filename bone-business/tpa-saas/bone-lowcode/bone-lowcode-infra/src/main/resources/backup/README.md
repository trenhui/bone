# `backup/` 配置说明（安全基线）

本目录下的 `application-*.yaml` **仅为脱敏模板**，用于说明可配置项与推荐的环境变量命名。

## 禁止事项

- 禁止提交真实 **数据库密码、Redis 密码、云 AccessKey、JWT secret、内网地址** 等到 Git。  
- 历史若曾误提交，须在密钥轮转后使用 `git filter-repo` 等工具从历史中清除（并视为密钥已泄露）。

## 推荐用法（12-Factor / 机密外置）

1. 在本地复制一份到 **已被 `.gitignore` 忽略** 的路径，例如 `~/.bone/tpa-application-local.yaml`。  
2. 启动时通过 `SPRING_CONFIG_ADDITIONAL_LOCATION=file:~/.bone/` 或 Kubernetes `Secret` / CI 变量注入。  
3. 所有敏感项使用 **环境变量占位**（模板中 `${VAR:default}`），生产环境 **不设 default** 或强制校验非空。

## 环境变量速查（与模板中占位符对应）

| 变量前缀 / 名称 | 用途 |
|------------------|------|
| `TPA_DB_*` / `LOWCODE_DB_*` | JDBC URL、用户名、密码 |
| `TPA_REDIS_*` / `LOWCODE_REDIS_*` | Redis 主机、端口、密码 |
| `METADATA_SDK_*` | 元数据 SDK URL、密钥、appcode |
| `SA_TOKEN_SECRET_KEY` | Sa-Token 签名密钥 |
| `ALIYUN_OSS_*` | OSS Endpoint、AK/SK、Bucket（若使用 oss 段） |

具体键名以各 `application-*.yaml` 内注释为准。
