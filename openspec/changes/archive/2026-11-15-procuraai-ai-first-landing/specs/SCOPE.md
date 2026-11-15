# Spec 范围裁决 — procuraai-ai-first-landing

> 日期：2026-11-14　阶段：Verify FAIL 回 Build 后的范围收敛

## 裁决结论

**本 change 的 `specs/` 目录下没有生效的 delta spec，归档时不得向 `openspec/specs/procurement/` 合入任何需求。**

## 依据

1. 2026-11-14 范围重定界后，本 change 的实际交付仅为 `bone-platform/bone-masterdata` 上的**通用主数据 Golden Record 去重内核**（37 个新增 main 文件、250 例单测），见 `tasks.md` §3 与 `docs/superpowers/plans/2026-11-14-procuraai-ai-first-landing.md`。
2. 原 `specs/procurement/` 下 3 份 delta spec（`ai-contract-review` / `ai-decision-governance` / `supplier-master-data`）描述的是完整 AI-first MVP（AI 合同审查流水线、AI 决策治理、HITL 评测、Supplier/Vendor 聚合），**全部未实现**，且含 `SHALL` / `MUST` 级需求。
3. Comet SDK Archive 会按 OpenSpec `ADDED/MODIFIED/REMOVED/RENAMED` 语义把 delta spec 合并进主 spec（`.workbuddy/skills/comet-archive/reference/compat.md:110`）；主 spec 当前**不存在** `openspec/specs/procurement/` 目录。若保留 3 份 delta spec 直接归档，等于把零实现的 AI 能力登记为主规格的生效需求 —— 这是 Verify 独立审查判定的 **CRITICAL-2**。
4. `openspec archive --skip-specs` 虽为官方选项，但 Comet SDK Guard 不透传该参数，且直接调用 `openspec archive` 会被 Guard 拦截（Classic change 必须走 SDK Archive flow）。因此在**归档前**把 delta spec 迁出 `specs/` 是唯一不污染主 spec、又不绕过 Runtime 的路径。

## 资产去向（迁移，不是删除）

3 份 spec 全文一字未改，整体迁至本 change 的 `deferred-specs/procurement/`：

| 原路径 | 现路径 | 归属 |
|---|---|---|
| `specs/procurement/ai-contract-review/spec.md` | `deferred-specs/procurement/ai-contract-review/spec.md` | `deferred-backlog.md` AI 合同审查 4.x |
| `specs/procurement/ai-decision-governance/spec.md` | `deferred-specs/procurement/ai-decision-governance/spec.md` | `deferred-backlog.md` AI 决策治理 5.x |
| `specs/procurement/supplier-master-data/spec.md` | `deferred-specs/procurement/supplier-master-data/spec.md` | `deferred-backlog.md` 供应商 MDM 实体 / DDL 落库 |

归档时 `deferred-specs/` 随 change 一并封存进 archive 目录，设计资产不丢失；后续独立 change 启动时可从归档目录取回并按真实实现重新出 delta spec。

## 边界

- 本裁决**不声称**这 3 份 spec 的设计内容有误；恰恰相反，它们是完整的高质量设计储备，只是不属于本 change 的已交付范围。
- 本裁决**不改变** `proposal.md` 的历史正文（Open 时的 AI-first MVP 目标如实保留），只在 `proposal.md` 顶部加范围状态声明。
- 主 spec 不因本 change 新增 `procurement` 能力；MDM 内核作为代码资产存在，其规格化留给后续独立 change。
