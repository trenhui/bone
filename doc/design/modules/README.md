# 模块详细设计索引（`doc/design/modules`）

本目录为 **控制台、元数据、主数据、集成、扩展、IAM、系统管理、Studio Generator、SmartMeta 引擎** 等模块的详细设计；与 `doc/prd`、`doc/architecture` 分工如下：

| 层级 | 目录 | 职责 |
|------|------|------|
| 产品 | [`doc/prd/`](../../prd/) | 做什么、优先级、验收口径（主 PRD） |
| 平台架构 | [`doc/architecture/`](../../architecture/) | 总体架构、DDD 门禁、前端、数据库规范 |
| 模块详设 | 本目录 | 单模块功能、数据模型、接口与工程结构（**规划 + As-Is 对照**） |

**真源优先级**：默认端口、可运行模块边界以 [**doc/wiki/03-本地开发与构建.md**](../../wiki/03-本地开发与构建.md)「常见服务端口」及各模块 **`application.yml`** 为准；详设中的端口/API 若为历史规划示例，文中会标注「规划示例」。

**元数据能力族**（sdk / server / engine / generator）：统一以 [**元数据能力-实现映射与竞品对照.md**](./元数据能力-实现映射与竞品对照.md) 为定义与协作真源（含 **§1.1 两类 Field**、**§8 与 masterdata 边界**）；详设 §2、PRD §4.4、BONE X 文首表与之对齐。

### 详设阅读约定

- **DDL 唯一真源**：根目录 [`bone-init.sql`](../../../bone-init.sql) + [数据库开发规范.md](../../architecture/数据库开发规范.md)。
- **无增量迁移**：改表即改 init，开发库 `DROP DATABASE` 后重建。
- **Hybrid**：部分能力仍在实现中（如 Generator 持久化），以各文 §0 与 P0 看板为准。
- **Docs-as-Code**：As-Is / Backlog 由 `tools/*-compliance-collector` 派生至 [`doc/_generated/`](../../_generated/README.md)；PR 前 `bash scripts/ci/collect-all-compliance.sh --sync-doc`。
- **审查清单**：历史台账（T1–T37）**已完成**并删除，需追溯请查 Git 历史。

## 模块文档

| 编号 | 文档 | 仓库模块（As-Is） | Docs-as-Code | 默认端口（开发） | PRD |
|------|------|-------------------|--------------|------------------|-----|
| 1 | [控制台](./1.%20控制台与仪表盘模块详细设计方案.md)（**v2.0**） | `bone-system` `/api/v1/console/*` | [`_generated/console`](../../_generated/console/) | **8083** | §4.3 |
| 2 | [元数据](./2.%20元数据管理模块详细设计方案.md) · [**对照**](./元数据能力-实现映射与竞品对照.md) | sdk + server + engine | [`_generated/metadata`](../../_generated/metadata/) + 详设 `META_COMPLIANCE_*` | **9001** | §4.4 |
| 3 | [主数据](./3.%20主数据管理模块详细设计方案.md) | `bone-masterdata` | [`_generated/masterdata`](../../_generated/masterdata/) + 详设 `MDM_COMPLIANCE_*` | **8084** | §4.5 |
| 4 | [集成](./4.%20集成管理模块详细设计方案.md) | `bone-integration` | [`_generated/integration`](../../_generated/integration/) + 详设 `INT_COMPLIANCE_*` | **8085** | §4.7 |
| 5 | [扩展](./5.%20扩展管理模块详细设计方案.md)（**v2.5**） | extension-engine + studio | [`_generated/extension`](../../_generated/extension/) + 详设附录 A/C | Studio **8088** | §4.6 |
| 6 | [IAM](./6.%20IAM账号权限管理模块详细设计方案.md) | `bone-iam` | [`_generated/iam`](../../_generated/iam/) + 详设附录 A/C | **8081** | §4.8 |
| 7 | [系统管理](./7.%20系统管理模块详细设计方案.md) | `bone-system` | —（与控制台同进程，见 console 收集器） | **8083** | §4.9 |
| 8 | [Studio Generator](./8.Studio%20Generator%20详细设计方案.md) | `studio-generator` | [`_generated/generator`](../../_generated/generator/) + 详设 `GEN_COMPLIANCE_*` | **8086** | §4 + Studio |
| 9 | [SmartMeta](./9.%20SmartMeta%20引擎模块技术说明.md) | `bone-metadata-engine` | — | 随宿主 | 引擎能力 |
| 10 | [应用与模块管理](./10.%20应用与模块管理详细设计方案.md) | `bone-platform` 平台注册中心 | — | N/A | §4 |

**跨模块**： [BONE X Studio 详细设计](../BONE-X-Studio-详细设计方案.md)

## 工程债与实现状态

- **P0 看板**：[`doc/wiki/07-P0-TODO看板.md`](../../wiki/07-P0-TODO看板.md)（扩展等模块 §0 与 EXT-* 项同步维护）
- **架构门禁**：[`doc/architecture/Bone-DDD-最终实践方案.md`](../../architecture/Bone-DDD-最终实践方案.md)
