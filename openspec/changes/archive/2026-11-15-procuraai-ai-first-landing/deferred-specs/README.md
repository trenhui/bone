# Deferred Specs — 设计储备（不参与本 change 的归档合并）

> 日期：2026-11-14　来源：Verify 独立最终集成审查 CRITICAL-2 修复

本目录存放 3 份**全文未改、整体迁出** `specs/procurement/` 的 delta spec：

| 文件 | 描述的能力 | 本 change 实现状态 | backlog 归属 |
|---|---|---|---|
| `procurement/ai-contract-review/spec.md` | 合同/招标文件 AI 风险审查流水线、条款锚点、HITL、分任务评测 | **未实现** | `deferred-backlog.md` 组 4.x |
| `procurement/ai-decision-governance/spec.md` | AI 决策三态 fail-closed、披露、人工最终决策、运行时治理 | **未实现** | `deferred-backlog.md` 组 5.x |
| `procurement/supplier-master-data/spec.md` | Supplier/Vendor 聚合、租户与审计、Golden Record、开通闸门 | **未实现**（本 change 交付的是通用主数据 Golden Record 内核，非供应商专属聚合） | `deferred-backlog.md` 供应商 MDM 实体 / DDL 落库 |

## 为什么迁出

Comet SDK Archive 会把 `specs/**/spec.md` 的 `ADDED Requirements` 合并进 `openspec/specs/`，使未实现能力成为主规格的生效需求（`comet-archive/reference/compat.md:110`）。本 change 的实际交付不含任何 AI 能力，故归档前必须把这些设计储备移出 `specs/`。**迁移不是删除也不是否决设计**：归档时本目录随 change 封存，后续独立 change 可从 archive 目录取回。

## 使用约定

- 这些文件**不得**作为本 change 的验收依据，也不得据此宣称 AI-first MVP 已交付。
- 恢复为生效 delta spec 时，须在**新的 change** 中重新走 Open → Design → Build → Verify，并按真实实现重写验收文字与场景。
- 完整范围裁决见 `../specs/SCOPE.md`；本 change 交付范围见 `../tasks.md` §3。
