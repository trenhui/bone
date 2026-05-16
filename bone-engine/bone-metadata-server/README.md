# bone-metadata-server

元数据 **可部署服务**（默认端口 `9001`，应用名 `bone-metadata-server`）。

## 职责

- 对外提供扩展字段等 REST API（`MetadataController`）
- 内嵌 **bone-metadata-sdk**（`deploymentMode: EMBEDDED`），与 MySQL + SQL 模板配合

## 与 sibling 模块

| 模块 | 角色 |
|------|------|
| `bone-metadata-sdk` | 库：仓储、扩展字段、嵌入式/远程客户端 |
| `bone-metadata-server` | 本模块：Spring Boot 服务壳 |
| `bone-metadata-engine` | 智能引擎（规则、SmartQL；原 `bone-smartmeta`） |

## 运行

```bash
mvn -pl bone-engine/bone-metadata-server -am spring-boot:run -Dspring-boot.run.profiles=local
```

环境变量：`BONE_DB_PASSWORD`、`METADATA_SDK_TOKEN`、`API_KEY`（见 `application-local.yaml`）。
