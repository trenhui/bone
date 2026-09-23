# 架构设计文档（`doc/architecture`）

平台架构、工程规范与 UI 约定，要求与仓库实现保持一致。

## 阅读顺序（新人）

| 顺序 | 文档 | 说明 |
|------|------|------|
| 0 | [根 README.md](../../README.md) | 产品理念、四大引擎、元数据双模式、演进路线 |
| 1 | [BONE-总体架构设计方案.md](./BONE-总体架构设计方案.md) | 平台总体、模块、NFR |
| 2 | [Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md)（v5.5.3） | DDD 唯一完整入口：原则、决策、门禁、样板、版本历史；术语见 [glossary.md](../glossary.md) |
| 3 | [Bone-API-规范.md](./Bone-API-规范.md) | REST 契约、OpenAPI、契约测试 |
| 4 | [数据库开发规范.md](./数据库开发规范.md) | DDL 真源 `../../bone-init.sql` |

## 规范索引

**HTTP 与契约**：[Bone-API-规范.md](./Bone-API-规范.md) · [Bone-错误码登记.md](./Bone-错误码登记.md) · [openapi/](./openapi/)（域契约）· [Docs-as-Code-模块合规模板.md](./Docs-as-Code-模块合规模板.md) · [_generated/](../_generated/)

**可观测性与运维**：[Bone-日志规范.md](./Bone-日志规范.md) · [Bone-可观测性规范.md](./Bone-可观测性规范.md) · [Bone-配置与环境规范.md](./Bone-配置与环境规范.md) · [config/env/README.md](../../config/env/README.md)

**安全、数据与集成**：[Bone-安全开发规范.md](./Bone-安全开发规范.md) · [Bone-多租户规范.md](./Bone-多租户规范.md) · [Bone-缓存规范.md](./Bone-缓存规范.md) · [Bone-消息与事件规范.md](./Bone-消息与事件规范.md) · [0037 集成引擎单模块收敛](./adr/0037-integration-engine-single-module.md)

**质量与交付**：[Bone-测试策略.md](./Bone-测试策略.md) · [Bone-版本与发布规范.md](./Bone-版本与发布规范.md) · [Bone-国际化规范.md](./Bone-国际化规范.md) · [adr/](./adr/)

**前端**：[bone-前端架构.md](./bone-前端架构.md) · [frontend/frontend-ui-spec.md](./frontend/frontend-ui-spec.md)

**其他**：[元数据能力-实现映射与竞品对照](../design/modules/元数据能力-实现映射与竞品对照.md) · [smartmeta/README.md](./smartmeta/README.md)

## 与 `doc/prd`、`doc/design` 的关系

| 目录 | 职责 |
|------|------|
| `doc/prd/` | 产品需求 |
| `doc/design/modules/` | 模块详设（API 细节以 OpenAPI + 本目录规范为准） |

## 审查清单

历史审查台账已归档至 [`doc/archive/文档治理-三目录审查子任务.md`](../archive/文档治理-三目录审查子任务.md)。
