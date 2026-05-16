# bone-engine

Bone 四大引擎与相关中间件的 Maven 聚合目录。

## 模块索引

| 目录 | 角色 | 文档入口 |
|------|------|----------|
| [bone-metadata-sdk](./bone-metadata-sdk/) | **默认持久化 SDK**（`@EnableSqlRepositories`） | [README](./bone-metadata-sdk/README.md) → [doc/](./bone-metadata-sdk/doc/) |
| [bone-metadata-server](./bone-metadata-server/) | 元数据可部署服务 | [README](./bone-metadata-server/README.md) |
| [bone-metadata-engine](./bone-metadata-engine/) | SmartMeta 运行时（原 smartmeta） | [README](./bone-metadata-engine/README.md) |
| [bone-extension-engine](./bone-extension-engine/) | ExtPoint 扩展引擎（SDK + Studio） | [README](./bone-extension-engine/README.md)（摘要）→ [docs/使用指南](./bone-extension-engine/docs/使用指南.md) |
| [bone-integration](./bone-integration/) | 集成引擎运行时 | [README](./bone-integration/README.md) |
| [studio-generator](./studio-generator/) | 代码生成服务 | 产品详设见 [doc/design/modules/8](../doc/design/modules/8.Studio%20Generator%20详细设计方案.md) |
| [go-engine](./go-engine/) | Go 实验性对照实现 | [README](./go-engine/README.md) |

平台级架构与 DDD 门禁：[doc/architecture/](../doc/architecture/README.md)。

## 构建

```bash
# 引擎层全量测试（按需缩小 -pl）
mvn -pl bone-engine/bone-metadata-sdk,bone-engine/bone-extension-engine/bone-extension-sdk test
```
