# 提案：procuraAI 业务模块骨架（批次4：寻源 / 准入 / 风险绩效 + 核心 DDL）

## Why

前三个批次已交付合同 AI 审查首切片、AI 决策治理横切模块、F3/F4/G15 安全治理护栏，但 procuraAI 的 02 卷（寻源与准入）、03 卷（风险绩效）在代码层仍是零实现：`t_proc_*` 表仅 9 张交易/履约表，M02/M03/M07 业务上下文无模块、无聚合根、无端点。用户明确选择「连 DDL 一起落库」，本批次落地三块业务骨架 + 6 张核心表 DDL，使 LLD 定义的领域不变量在代码层可执行，且不越过 owner 红线（AI 授标、自动授信、绩效权重冻结、外部征信接入）。

## What Changes

- **核心 DDL 落库**：`bone-init.sql` 新增 6 张 `t_proc_*` 表（t_proc_sourcing_project / t_proc_rfx / t_proc_qualification / t_proc_onboarding / t_proc_risk_alert / t_proc_performance_score），同步 `数据库开发规范.md §2.17`（9 → 15）；全部 init-only、必备列齐全、幂等键与索引对齐 LLD §4。
- **寻源模块 bone-procurement-sourcing**：SourcingProject / Rfx 聚合根 + 显式状态迁移表（INV-01 DRAFT 后关键字段冻结、INV-03 截止时间单调、INV-09 授标必须具名人工）；2 个 REST 端点；22 例测试（含 13 条共享架构规则）。
- **准入模块 bone-procurement-qualification**：Qualification（INV-10 EXPIRED 不得作准入依据、INV-11 OCR 须人工复核）/ Onboarding（INV-12 APPROVED 前不发事件、INV-13 单供应商仅一个 IN_REVIEW，DDL 生成列条件唯一）；3 个 REST 端点；22 例测试。
- **风险绩效模块 bone-procurement-risk**：RiskAlert（来源幂等、UNKNOWN 严重度 fail-closed 转人工、处置具名）/ PerformanceScore（只读投影，禁止自动决定供应商去留）；2 个 REST 端点；22 例测试。
- **权限种子与清单修复**：0130 新增 7 个权限码（950 号段，绑定超管）；补登记 0127/0128/0129 三个前序权限种子脚本，修复迁移清单 M1 既存红项（executableCount 130 → 134）。

**非目标（明确不做）**：AI 辅助招标体检 / 问答 / 起草 / 品类分类（批次5，且评测集需 owner）、M02 AI 评分接入与授标建议（owner 红线）、M07 动态风险画像与绩效权重冻结（owner）、外部征信数据接入（HMAC ACL，需 owner）、Award/Bid/Quotation/CAPA 完整聚合（后续切片）、生产持久化仓储替换内存骨架（L3）、evalset 27→200（owner R-3，批次2 转 backlog）。不伪造效果数字、不伪造 owner 决策。

## Capabilities

### New Capabilities

- `procurement/business-module-skeleton`: procuraAI 寻源/准入/风险绩效骨架的可观察行为——寻源生命周期 fail-closed 迁移与人工授标、证照过期/OCR 人工复核与准入人工终决、预警来源幂等与 UNKNOWN 转人工、绩效只读不自动授信，以及核心 DDL 与规范清单一致。

### Modified Capabilities

- （无。新增 3 个模块与 6 张表，不改批次1/3 已归档的治理 spec。）
