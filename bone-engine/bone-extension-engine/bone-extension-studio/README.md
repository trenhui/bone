# bone-extension-studio

扩展引擎管理台：扩展点 / 扩展实现 CRUD，插件版本与执行日志，以及运行时路由元数据推送。

## 持久化

| Profile | `persistence.mode` | 数据源 | DDL |
|---------|-------------------|--------|-----|
| **`in-memory`**（默认） | `in-memory` | 无 | — |
| **`metadata`** | `metadata` | H2 文件 `./data/extension-studio-metadata` | `schema-h2.sql`（仅本地） |
| **`metadata-mysql`** / **`prod`** | `metadata` | MySQL `bone` 库（`BONE_DB_*`） | **[`bone-init.sql`](../../../bone-init.sql)** `exts_*`，**禁止** `spring.sql.init` |

> 若库中仍有旧表 `xst_*` / `ext_studio_*`，请全量重建：`DROP DATABASE bone` 后重新执行 `bone-init.sql`。

### 内存模式（默认联调）

```bash
cd bone-extension-studio
mvn spring-boot:run -Dmaven.test.skip=true
```

### Metadata + H2（单机持久化）

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=metadata -Dmaven.test.skip=true
```

首次启动会执行 `db/schema-h2.sql`；换库时删除 `./data/extension-studio-metadata*`。

### Metadata + MySQL（与平台库一致）

```bash
# 仓库根目录
mysql -u root -p < bone-init.sql

source ../../../scripts/dev/load-env.sh   # 或 export BONE_DB_*
cd bone-extension-studio
mvn spring-boot:run -Dspring-boot.run.profiles=metadata-mysql -Dmaven.test.skip=true
```

`prod` profile 等价导入 `application-metadata-mysql.yml`，仅附加生产日志级别。

## API

- 路径：`/api/v1/extension/*`（见 [Bone-API-规范](../../../doc/architecture/Bone-API-规范.md) §13）
- 响应：`com.bone.core.model.ApiResponse`；失败 `data` 为 `ProblemDetail`
- Swagger UI：`http://localhost:8088/swagger-ui.html`
- 健康检查：`GET /actuator/health`
- 审计查询：`GET /api/v1/extension/audit-logs?limit=20`（游标，见 `Link` 头）

## 鉴权（与 bone-iam 对齐）

| Scope | 用途 |
|-------|------|
| `extension:points:read` | 读扩展点 / 概览 / 执行日志 / 审计 |
| `extension:points:write` | 写扩展点 / 插件 CRUD |
| `extension:plugins:deploy` | 部署、上传、回滚、发布运行时 |

- `/api/**` 需携带 IAM 签发的 `Authorization: Bearer <accessToken>`（JWT `scopes` claim）
- 配置项：`bone.iam.jwt.secret-key`（与 IAM 相同；生产务必 `BONE_IAM_JWT_SECRET` 外部化）
- Profile `in-memory` / `metadata`：`bone.extension.studio.security.permit-unauthenticated=true` 可免 JWT（仅本地）
- 本地联调：先访问 IAM `POST /api/v1/iam/login`，再将返回的 `token` 写入前端 `localStorage.token` 或请求头

## 网关（可选）

`bone-gateway` `:8888` 转发 `/api/v1/extension/**` → Studio、`/api/v1/iam/**` → IAM；见 [`config/env/README.md`](../../../config/env/README.md)。

```bash
# 示例
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8088/api/v1/extension/overview
```

## 运行时 SDK 执行日志上报（可选）

```yaml
bone:
  extension:
    studio:
      report:
        enabled: true
        base-url: http://localhost:8088
```

上报：`POST /api/v1/extension/execution-logs:ingest`  
动作类接口使用 **冒号后缀**（如 `plugins/{id}:deploy`、`points/{id}:enable`），见 OpenAPI [extension-v1.yaml](../../../doc/architecture/openapi/extension-v1.yaml)。

## 契约测试（§15）

```bash
# Provider 冒烟
mvn test -pl bone-engine/bone-extension-engine/bone-extension-studio \
  -Dtest=ExtensionApiContractTest,ExtensionManagementControllerTest

# OpenAPI 草案校验
bash scripts/ci/validate-extension-openapi.sh
```

覆盖：`ApiResponse` 信封、`ProblemDetail` 404、游标分页、冒号动作路径。

| 测试类 | 说明 | 依赖 |
|--------|------|------|
| `ExtensionApiContractTest` | Provider 契约 | — |
| `ExtensionStudioSmokeIT` | 进程内 HTTP 冒烟 | — |
| `StudioMetadataMysqlIT` | metadata + MySQL | **Docker** |

CI：`.github/workflows/extension-studio.yml`。

## 运行时同步

见 [`../docs/使用指南.md`](../docs/使用指南.md) §4.6。
