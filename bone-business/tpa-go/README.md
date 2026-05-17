# tpa-go

TPA 行业包 **Go 后端**（迁移目标栈）。当前为 **阶段 1 骨架**：健康检查 + 迁移状态只读 API；业务 API 仍由 [`../tpa-saas/`](../tpa-saas/)（Java）提供。

路线见 [`../TPA-MIGRATION.md`](../TPA-MIGRATION.md)。

## 快速开始

```bash
cp .env.example .env   # 可选
export BONE_SERVER_PORT=8090
make tidy
make run
```

## 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 存活探针 |
| GET | `/ready` | 就绪探针 |
| GET | `/api/v1/tpa/migration/status` | 迁移阶段元数据（契约见 `contracts/openapi.yaml`） |

## 目录（DDD）

```
cmd/server/          # 入口
internal/adapter/web/
internal/application/query/
internal/common/result/
internal/config/
contracts/           # OpenAPI 与 Java API 清单
```

## 前端联调

React 应用 [`../tpa-sass-react/`](../tpa-sass-react/) 可通过 `VITE_API_BASE` 指向本服务（迁移期与 Java 双栈切换）。

```bash
# 示例：仅探测 Go 迁移状态
curl -s http://localhost:8090/api/v1/tpa/migration/status | jq .
```
