# bone-metadata-server

**扩展字段元数据控制面**（可选部署）：Spring Boot 服务，默认端口 `9001`。

> 三模块定义、协作与竞品：**[元数据能力-实现映射与竞品对照.md](../../doc/design/modules/元数据能力-实现映射与竞品对照.md)**

## 职责（As-Is）

- `POST/GET /api/v1/metadata/fields:*` — 扩展字段（`MetadataController`；存储模式预留列/JSON/EAV 由 sdk 路由）
- **catalog**：`/api/v1/metadata/entities`、`…/entities/{entityId}/fields`、`…/relationships`（`catalog` 包 Controller）
- **模式 B 运行时**：`/api/v1/runtime/entities/{entityCode}/records`（`delivery_mode=RUNTIME` 且已发布；`JdbcRuntimeRecordService`）
- 内嵌 **bone-metadata-sdk**（`deploymentMode: EMBEDDED`），供其他应用 `REMOTE` Feign 调用

**As-Is 不负责**：代码生成（见 `studio-generator`）、规则引擎（见 `bone-metadata-engine`）。

## 与 sibling 模块

| 模块 | 角色 |
|------|------|
| `bone-metadata-sdk` | **数据面**：持久化 + 扩展字段（预留列/JSON/EAV，默认预留列）（各业务进程必选） |
| `bone-metadata-server` | **本模块**：扩展字段 REST 集中端点 |
| `bone-metadata-engine` | **计算面**：智能元数据引擎（可选） |

## 运行

```bash
mvn -pl bone-engine/bone-metadata-server -am spring-boot:run -Dspring-boot.run.profiles=local
```

环境变量：`BONE_DB_PASSWORD`、`METADATA_SDK_TOKEN`、`API_KEY`（见 `application-local.yaml`）。
