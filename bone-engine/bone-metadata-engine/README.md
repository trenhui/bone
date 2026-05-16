# bone-metadata-engine

智能元数据引擎（**原 `bone-smartmeta-*`，已退役该 Maven 坐标**）。

## 模块

| 子模块 | artifactId | 说明 |
|--------|------------|------|
| `bone-metadata-engine-core` | `bone-metadata-engine-core` | 引擎实现（包 `com.bone.metadata.engine`） |
| `bone-metadata-engine-starter` | `bone-metadata-engine-starter` | Spring Boot 自动装配 |

## 迁移对照

| 旧 | 新 |
|----|-----|
| `bone-smartmeta` | `bone-metadata-engine`（父 POM） |
| `bone-smartmeta-engine` | `bone-metadata-engine-core` |
| `bone-smartmeta-starter` | `bone-metadata-engine-starter` |
| `com.bone.smartmeta.engine` | `com.bone.metadata.engine` |

## 构建

```bash
mvn -pl bone-engine/bone-metadata-engine -am test
```

默认不参与平台单体启动。设计说明见 [doc/design/modules/9. SmartMeta 引擎模块技术说明.md](../../../doc/design/modules/9.%20SmartMeta%20引擎模块技术说明.md)。
