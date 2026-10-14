# 业务对象界面 · 协议族（v1.1 详细设计）

> **专题包入口**：[`../../README.md`](../../README.md) · **UI 资产入口**：[`../README.md`](../README.md)
> **前置**：[`../元数据工作区前端融合方案.md`](../元数据工作区前端融合方案.md)（已落地的四 Tab 工作区）
> **样板真源**：[`../../cases/ui-schema-samples/expense-claim-ui-schema.example.json`](../../cases/ui-schema-samples/expense-claim-ui-schema.example.json) + [Demo](../../cases/ui-schema-samples/mock-expense-dynamic-v1.html)
> **参考借鉴（仅思路）**：`/创业项目/code/pk-tpa-saas/tpa-sass-vue3` 的 `RenderEngine`（Page/Form/Block/Table 组件树、linkageRule/submitRule 双轨、Base/Exclusive 两层继承）

## 这一族要解决什么

从「静态数据表建模」走向「业务对象建模」——补表达力（流程/富类型/规则/布局/权限引用），**保持声明式轻量**：不建画布、不建公式引擎、不建第二数据模型（与 `InterfaceTab`「catalog 投影 + 轻覆盖」哲学一致）。

## 与「融合方案」的分工（避免双真源/重复）

| 文档 | 时态 | 边界 |
|------|------|------|
| `../元数据工作区前端融合方案.md` | **当前态 · 已落地** | 四 Tab 工作区（结构/界面/数据/发布）的 As-Is→To-Be、`file:line`、W0–W2、门禁 |
| `BONE-业务对象界面JSON协议设计.md` | **目标态 · 协议** | 界面 JSON 协议规范（三层、控件注册表、linkage/submit/actionBar、变体、i18n） |
| `BONE-业务对象界面前后端技术方案.md` | **目标态 · 工程** | 新表 `meta_entity_ui_schema`、聚合 `UiSchemaDef`、发布快照、前端引擎与编辑器、测试门禁 |
| `bone-ui-schema.schema.json` | **目标态 · 契约** | JSON Schema 真源（以本文件为权威，文档描述仅作说明） |

> 两者关系：协议 = What（结构契约）；技术方案 = How（落库/渲染/发布）。技术方案的「分期与验收」已改为引用本协议 §8/§9 并只列工程侧差异，不再重复。

## 阅读顺序

1. 本 README（看分工）
2. `BONE-业务对象界面JSON协议设计.md` §0–§9（协议主体）→ §10 通用性复核 → §11 业界对标 → §12 变体
3. `BONE-业务对象界面前后端技术方案.md`（落地）
4. `../../cases/ui-schema-samples/expense-claim-ui-schema.example.json`（实例走读）

## 文件清单

| 文件 | 角色 |
|------|------|
| `BONE-业务对象界面JSON协议设计.md` | 协议规范（v1.0 + §10/§11/§12 复核增量 + §13 v1.2 待评审） |
| `BONE-业务对象界面前后端技术方案.md` | 前后端详细设计（v1.1） |
| `bone-ui-schema.schema.json` | JSON Schema 真源 |
| `bone-ui-schema-demo.html` | 可配置 DEMO（单文件双击即开，用于评审/走查；**内嵌本 schema.json，自检含“真源机校”，发布=真源机校+语义+变体组合冲烟三道门硬门禁**——协议 §13.6/§14 同源；协议演进时只改 schema.json，DEMO 校验自动跟随，避免人工镜像滞后） |
| `bone-ui-schema-designer.html` | ⚠️ 实验稿（并行会话产物，boot 存在 5 处 id 脱节空指针，不可作为规格引用；差异化能力已并入 demo，后续以此收敕为单文件） |

## DEMO 边界（避免把演示能力误读成协议能力）

- **协议内**：结构/分区/控件、linkageRules、submitRules、actionBar、variants、`dictionaries` 覆写。
- **v1.2 待评审**：`enabled`（规则级启停）、`pageHeader`（页头信息条）——已落 Schema 描述并带待评审前缀，见协议 §13。
- **DEMO 演示扩展（`x_` 前缀，非协议）**：`x_cols` 分区栅格。导出 JSON 过不了 `schema.json` 属**预期**：DEMO「自检」面板会显式报出；且 v1.8 起 DEMO 内嵌本 `schema.json`，浏览器内即可真源机校（打开自检 → 真源机校 PASS/FAIL）。
- **明确不做**：在 UI 层新建/删除码表（协议 §3.1 禁止）、按主体做码表覆盖（差异化归 `variants` + 租户隔离，见 §12）。
