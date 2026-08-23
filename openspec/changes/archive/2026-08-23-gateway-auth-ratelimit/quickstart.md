# Quickstart: 网关统一鉴权与限流熔断

## 前置
- Redis 可用；JWT 密钥经环境变量注入
- 依赖 iam-org-menu-baseline 完成后验证角色注入

## 本地验证
```bash
mvn -pl bone-platform/bone-gateway -am clean install -DskipTests
# 启动 gateway (8888)
curl -i http://localhost:8888/api/v1/iam/...  # 无 token 应 401
```

## 关键入口
- Filter：`bone-platform/bone-gateway/.../filter/JwtAuthGlobalFilter.java`
