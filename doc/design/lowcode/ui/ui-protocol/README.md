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
| `bone-ui-schema.schema.json` | JSON Schema 真源（设计器内嵌同一份，自检含"真源机校"） |
| `bone-ui-schema-designer.html` | **权威单文件设计器（配置界面真源）**：在 DEMO 引擎+IDE+三场景之上叠加实体目录绑定（CHILD_COLS / CHILD_TABLE 元数据驱动子表 + Σ 合计行，支撑 SUB-1 明细合计提交规则）、品牌化与高端视觉美化；内嵌 schema.json，自检含真源机校+语义+变体组合冲烟三道硬门禁；经三套机器门禁（协议机校 / 实体绑定 / 启动冒烟）零漂移验证，报销/智能理赔/采购三场景联调（正向命中 + 负向不命中）全绿 |
| `archive/bone-ui-schema-demo.html` | 历史参考（v1.9 引擎+IDE+三场景基线，已被 designer 完全覆盖；仅作回溯，不作为规格引用） |
| `validate-designer-gates.js` | 门禁1：设计器产出 vs schema.json 真源机校 + 语义自检 + 三场景运行时探针（正向命中 + 负向不命中） |
| `validate-entity-binding.js` | 门禁2：实体目录生成/绑定零漂移 + 10 种字段类型映射全覆盖 |
| `smoke-boot.js` | 门禁3：真实 boot + 渲染路径零异常（含 CHILD_TABLE 子表渲染与 Σ 合计） |
| `check-contrast.py` | 对比度门禁（深浅主题可读） |

## 设计器边界（避免把演示能力误读成协议能力）

- **协议内**：结构/分区/控件、linkageRules、submitRules、actionBar、variants、`dictionaries` 覆写。
- **v1.2 待评审**：`enabled`（规则级启停）、`pageHeader`（页头信息条）——已落 Schema 描述并带待评审前缀，见协议 §13。
- **设计器演示扩展（`x_` 前缀，非协议）**：`x_cols` 分区栅格。导出 JSON 过不了 `schema.json` 属**预期**：设计器「自检」面板会显式报出；且 v1.8 起设计器内嵌本 `schema.json`，浏览器内即可真源机校（打开自检 → 真源机校 PASS/FAIL）。
- **明确不做**：在 UI 层新建/删除码表（协议 §3.1 禁止）、按主体做码表覆盖（差异化归 `variants` + 租户隔离，见 §12）。
