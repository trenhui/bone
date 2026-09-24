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

## domain 分组形态（E-10 登记）

**目标形态**（[ADR-0036](../../doc/architecture/adr/0036-domain-model-package-single-standard.md)，2026-09-22 起为平台唯一形态）：聚合构件置于 `domain/model/{聚合}/`，聚合根 / 实体 / 值对象在聚合包内直接平铺，`event/` `projection/` 为聚合内子包；`repository` / `gateway` / `service` 端口留在 `domain/` 根。

**本模块现状**：**已迁移至目标形态**（2026-09-22 完成，47 个测试全绿）。原 `domain/model/` 平铺类已按概念分组，`enums` 角色包已解散：

| 包 | 构件 |
|---|---|
| `domain/model/meta/` | `MetaEntity` / `MetaField` / `MetaEntityRelation` + 原 `domain/enums/` 的 `MetaEntityStatus` / `MetaDeliveryMode` |
| `domain/model/iam/` | `IamApplicationRef` / `IamModuleRef` |
| `domain/model/physical/` | `PhysicalStructurePlan`（原 `model/physical/`，保持不变） |
| `domain/{gateway,repository,service}` | **不变**——按 ADR-0036 R3 端口留根（`service/` 的 `IamApplicationValidator` / `IamModuleValidator` 同属端口） |

`enums` 的两个枚举定性为**模型构件**（被 `MetaEntity` 直接引用）而非端口，故随聚合迁入 `model/meta/`，原 `domain/enums/` 目录已删除。

## 本上下文拥有的表（E-1.2 数据所有权声明）

bone-metadata-server 是下列表的唯一写方与 Schema _owner；其他模块只读须经本模块出站端口，不得直连这些表：

| 表 | 语义 | 聚合 |
|---|---|---|
| `meta_entity` | 扩展字段实体 | MetaEntity |
| `meta_field` | 扩展字段定义 | MetaField |
| `meta_entity_relation` | 实体关系 | MetaEntityRelation |

> 注：`bone_application` / `bone_module` 经 `IamApplicationRef` / `IamModuleRef` 只读引用，Schema _owner 为 IAM 上下文（见 bone-iam README），本模块不持有其写权。`meta_code_template` 已在 `bone-init.sql` 建表但当前无 `@Table` 实体，归属待裁定。
