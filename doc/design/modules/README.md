# 模块详细设计索引（`doc/design/modules`）

本目录为 **控制台、元数据、主数据、集成、扩展、IAM、系统管理、Studio Generator、SmartMeta 引擎** 等模块的详细设计；与 `doc/prd`、`doc/architecture` 分工如下：

| 层级 | 目录 | 职责 |
|------|------|------|
| 产品 | [`doc/prd/`](../prd/) | 做什么、优先级、验收口径（主 PRD） |
| 平台架构 | [`doc/architecture/`](../architecture/) | 总体架构、DDD 门禁、前端、数据库规范 |
| 模块详设 | 本目录 | 单模块功能、数据模型、接口与工程结构（**规划 + As-Is 对照**） |

**真源优先级**：默认端口、可运行模块边界以根目录 [**README.md**](../../../README.md)「端口与模块对照」及各模块 **`application.yml`** 为准；详设中的端口/API 若为历史规划示例，文中会标注「规划示例」。

## 模块文档

| 编号 | 文档 | 仓库模块（As-Is） | 默认端口（开发） | PRD |
|------|------|-------------------|------------------|-----|
| 1 | [控制台](./1.%20控制台与仪表盘模块详细设计方案.md) | **规划中**（无 `bone-console`；能力分散在 Shell + 各微应用） | — | §4.3 |
| 2 | [元数据](./2.%20元数据管理模块详细设计方案.md) | `bone-metadata-server`、`bone-metadata-sdk` | 见 `application*.yml`（如 9001） | §4.4 |
| 3 | [主数据](./3.%20主数据管理模块详细设计方案.md) | `bone-platform/bone-masterdata` | **8080** | §4.5 |
| 4 | [集成](./4.%20集成管理模块详细设计方案.md) | `bone-engine/bone-integration` / `bone-platform/bone-integration` | **30888** / **8085** | §4.7 |
| 5 | [扩展](./5.%20扩展管理模块详细设计方案.md) | `bone-extension-engine`、`bone-extension-studio` | Studio **8080**；`bone-extension-app` **3008** | §4.6 |
| 6 | [IAM](./6.%20IAM账号权限管理模块详细设计方案.md) | `bone-platform/bone-iam` | **8081** | §4.8 |
| 7 | [系统管理](./7.%20系统管理模块详细设计方案.md) | `bone-platform/bone-system` | **8083** | §4.9 |
| 8 | [Studio Generator](./8.Studio%20Generator%20详细设计方案.md) | `bone-engine/studio-generator` 等 | **8085**（与平台 integration 默认冲突时注意） | §4 + Studio |
| 9 | [SmartMeta](./9.%20SmartMeta%20引擎模块技术说明.md) | `bone-engine/bone-metadata-engine` | 随宿主应用 | 引擎能力 |

**跨模块**： [BONE X Studio 详细设计](../BONE-X-Studio-详细设计方案.md)

## 工程债与实现状态

- **P0 看板**：[`doc/wiki/07-P0-TODO看板.md`](../../wiki/07-P0-TODO看板.md)（扩展等模块 §0 与 EXT-* 项同步维护）
- **架构门禁**：[`doc/architecture/Bone-DDD-最终实践方案.md`](../../architecture/Bone-DDD-最终实践方案.md)
