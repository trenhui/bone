# 场景 case（Mode B 探针）

> **专题包入口**：[`../README.md`](../README.md) · **UI 资产入口**：[`../ui/README.md`](../ui/README.md)
> 后续场景 case 一律放本目录（或其子目录），勿散落到 `design/` 根或其他模块目录。

## 资产分组

### A. 业务示范 + 缺口探针

| 文档 | 说明 |
|------|------|
| [`费用报销系统-元数据建模方案.md`](./费用报销系统-元数据建模方案.md) | L0 = PRD §2.5.9 / 原型；L1 = 缺口探针（不进 Must）。报销对象图、状态机、行策略样例真源 |

### B. 三入口页 UI 诊断与决策

| 文档 | 角色（两者**有意分层**，勿合并） |
|------|------|
| [`../ui/业务建模三入口页UI优化方案.md`](../ui/业务建模三入口页UI优化方案.md) | **诊断证据**（已归 ui）：D1–D4 / B1–B5 / O1–O5，每条绑定代码行号 + 业界对标（"是什么问题"） |
| [`../ui/业务建模三入口页最佳方案综述.md`](../ui/业务建模三入口页最佳方案综述.md) | **决策综述**（已归 ui）：取各家之长的取舍与关键交互决策（"为什么这样改"） |

### C. 交互稿版本链（现行可用）

> 同一页面的历史稿已移入 `archive/`（见 `archive/README.md`），本表只列现行基线。

| 资产 | 角色 |
|------|------|
| [`mock-best-scheme.html`](./mock-best-scheme.html) | **最终交互稿基线（唯一落地依据，SPA 三视图 + 演示状态开关）**。⚠ 外部 `doc/plans/scheme-impl-changes.md` 引用本文件，**勿移动/改名** |
| `archive/mock-model-design-v3.html` · `archive/mock-entity-management-v3.html` · `archive/mock-runtime-data-expense-v2.html` | 三入口页 v3 过程稿（已归档，结论进 `mock-best-scheme.html`） |
| `mock-expense-flow.html` · `archive/mock-expense-act1~3.html` | 报销场景端到端串联（7 步动线 + 三幕过程稿） |
| `optimized-model-design.png` · `optimized-entity-management.png` | 菜单 IA 定稿后深度诊断效果图 |
| `最终稿-模型设计.png` · `最终稿-业务对象.png` · `最终稿-业务数据.png` | 最终交付三屏截图（由 `mock-best-scheme.html` 渲染） |
| `expense-flow-act1~3.png` | 报销端到端串联设计稿（三幕） |

### D. 界面协议样板（ui-protocol 配套）

> 协议驱动渲染的样例与 Demo，详见 [`../ui/ui-protocol/README.md`](../ui/ui-protocol/README.md)。

| 资产 | 说明 |
|------|------|
| [`ui-schema-samples/expense-claim-ui-schema.example.json`](./ui-schema-samples/expense-claim-ui-schema.example.json) | 报销 v1.0 首个落地样板（协议 §6 走读） |
| [`ui-schema-samples/mock-expense-dynamic-v1.html`](./ui-schema-samples/mock-expense-dynamic-v1.html) | 协议驱动动态渲染 Demo v1（引擎跑测 19 项全绿） |
| `ui-schema-samples/{claim-outpatient-fasttrack, crm-opportunity, equity-research-card, erp-purchase-order, procurement-review-report}-ui-schema.example.json` | v1.1 通用性复核五场景样板（协议 §10.1） |

## 历史归档（`archive/`，不再作为依据）

`archive/mock-model-design.html` / `archive/mock-model-design-v2.html` / `archive/mock-entity-management.html`（v1/v2 效果图，被 v3 取代）等，完整清单见 [`archive/README.md`](./archive/README.md)。

返回专题包：[`../README.md`](../README.md)
