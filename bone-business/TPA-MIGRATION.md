# TPA 技术栈迁移路线（规划）

> **状态**：**阶段 1 已启动** — [`tpa-go/`](./tpa-go/) 骨架可运行；业务 API 仍由 Java 提供。  
> **目标**：后端 **Java → Go**；管理端 **统一为 React**（`tpa-sass-react`）。

---

## As-Is / To-Be

| 层级 | 当前（在役） | 目标 |
|------|----------------|------|
| 后端 | [`tpa-saas/`](./tpa-saas/)（Java / Spring，`com.pkh.cloud`） | **[`tpa-go/`](./tpa-go/)**（Go，逐步切流） |
| 管理端 | [`tpa-sass-vue3/`](./tpa-sass-vue3/) + [`tpa-sass-react/`](./tpa-sass-react/) | **仅** [`tpa-sass-react/`](./tpa-sass-react/) |
| 平台内核 | `bone-platform/*`、`bone-engine/*`（Java） | **不变**；TPA 作为行业包通过 API 集成 |

---

## 原则（与 Bone 架构一致）

1. **契约优先**：OpenAPI / 事件契约先行；Go 实现与 Java 行为对齐后再切流量（Strangler Fig）。
2. **分层对齐**：Go 侧参考 [Bone-DDD 最终实践方案](../doc/architecture/Bone-DDD-最终实践方案.md) 与 [`bone-blueprint-go`](../bone-engine/go-engine/bone-blueprint-go/) 的包结构（adapter → application → domain ← infrastructure）。
3. **持久化**：新 Go 服务优先复用 Bone **Metadata SDK 能力**的 Go 对照（[`bone-metadata-go`](../bone-engine/go-engine/bone-metadata-go/)），避免再引入第二套 ORM 栈。
4. **前端**：新功能 **只加在 React**；Vue3 仅做缺陷修复与 parity，达到功能对等后下线 `tpa-sass-vue3`。
5. **范围**：本路线仅覆盖 **TPA 行业包**；不替换 `bone-iam`、`bone-metadata-server` 等平台 Java 服务。

---

## 建议阶段

| 阶段 | 后端 | 前端 | 验收 |
|------|------|------|------|
| **0 — 基线** | 冻结 Java 对外 API 清单（OpenAPI） | React 与 Vue3 能力矩阵对照表 | 契约入库、CI 契约测试 |
| **1 — Go 骨架** | 新建 Go 模块：健康检查 + 1 条读 API 代理/重写 | React 可切换 `VITE_API_BASE` 指向 Go | 双栈并行，灰度 1 个只读接口 |
| **2 — 核心域迁移** | 理赔/保单等核心写路径按域切块迁 Go | Vue3 停新功能；React 补 parity | 域级切换 + 回滚预案 |
| **3 — 收尾** | Java `tpa-saas` 只读或下线 | 删除/归档 `tpa-sass-vue3` | 文档与部署脚本仅保留 Go + React |

具体里程碑与负责人请在 PRD / 看板中登记（可与 [wiki/07-P0-TODO看板](../doc/wiki/07-P0-TODO看板.md) 分列 **TPA-*** 项）。

---

## Go 落点建议

| 选项 | 说明 |
|------|------|
| **A. `bone-business/tpa-go/`**（**已采用**） | 与 `tpa-saas` 并列，行业边界清晰，独立 `go.mod` |
| **B. `bone-engine/go-engine/tpa-*/`** | 可选：将域模块拆入 go-engine 子目录时仍由 tpa-go 聚合入口 |

无论路径，均应：

- 引用 [`bone-metadata-go`](../bone-engine/go-engine/bone-metadata-go/README.md) / [`bone-extension-go`](../bone-engine/go-engine/bone-extension-go/README.md) 作为技术探路，而非从零造轮子；
- HTTP 风格与平台一致（统一错误码、`X-Tenant-Id`、审计字段与 [数据库开发规范](../doc/architecture/数据库开发规范.md) 对齐）。

---

## 相关文档

- [bone-business/README.md](./README.md) — 目录与本地构建
- [go-engine/README.md](../bone-engine/go-engine/README.md) — Go 实验与对照实现
- [BONE 总体架构 — IAM / 行业包](../doc/architecture/BONE-总体架构设计方案.md)（SA-Token 与 Spring Security 分栈说明）
