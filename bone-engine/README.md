# bone-engine

Bone 四大引擎与相关中间件的 Maven 聚合目录。

## 模块索引

| 目录 | 角色 | 文档入口 |
|------|------|----------|
| [bone-metadata-sdk](./bone-metadata-sdk/) | **数据面**：平台 P0 持久化 + 扩展字段三模式（预留列默认/JSON/EAV，嵌入业务进程） | [README](./bone-metadata-sdk/README.md) → [doc/](./bone-metadata-sdk/doc/) |
| [bone-metadata-server](./bone-metadata-server/) | **控制面（选配）**：:9001 — As-Is 扩展字段 API（预留列/JSON/EAV）；MVP-2 catalog（实体/字段/关系） | [README](./bone-metadata-server/README.md) |
| [bone-metadata-engine](./bone-metadata-engine/) | **模式 B 计算面**：运行时动态 CRUD / 规则 / SmartQL（增强后可减少标准代码生成） | [README](./bone-metadata-engine/README.md) |
| — | **三模块定义、协作与竞品** | [doc/design/modules/元数据能力-实现映射与竞品对照.md](../doc/design/modules/元数据能力-实现映射与竞品对照.md) |
| [bone-extension-engine](./bone-extension-engine/) | ExtPoint 扩展引擎（SDK + Studio） | [README](./bone-extension-engine/README.md)（摘要）→ [docs/使用指南](./bone-extension-engine/docs/使用指南.md) |
| — | **集成服务**已迁至 `bone-platform/bone-integration` | [ADR](../doc/architecture/ADR-integration-consolidation.md) |
| [studio-generator](./studio-generator/) | 代码生成服务（:8085，纳入 `mvn test`） | 产品详设见 [doc/design/modules/8](../doc/design/modules/8.Studio%20Generator%20详细设计方案.md) |
| [go-engine](./go-engine/) | Go 实验性对照实现 | [README](./go-engine/README.md) |

平台级架构与 DDD 门禁：[doc/architecture/](../doc/architecture/README.md)。

## 构建

```bash
# 引擎层全量测试（按需缩小 -pl）
mvn -pl bone-engine/bone-metadata-sdk,bone-engine/bone-extension-engine/bone-extension-sdk test
```
