# 元数据 UI 设计资产（索引）

> **专题包入口**：[`../README.md`](../README.md)
> **产品定案**：PRD **v0.50**（§0.6 / §0.7 / §2.5.9）
> **数据面设计**：[`../BONE-低代码Runtime数据面设计.md`](../BONE-低代码Runtime数据面设计.md)
> **业务串联**：[`../cases/费用报销系统-元数据建模方案.md`](../cases/费用报销系统-元数据建模方案.md)（L0 = 原型；L1 = 缺口探针）

## 资产分工

| 资产 | 职责边界 | 不做什么 |
|------|----------|----------|
| `元数据工作区前端融合方案.md` | **已落地的融合记录**：四 Tab 工作区 As-Is→To-Be、`file:line`、W0–W2 切片、门禁；属「当前态」工程基线 | 不是下一阶段设计 |
| `ui-protocol/`（协议族 v1.0） | **下一阶段详细设计**：业务对象界面 JSON 协议 + 前后端技术方案 + Schema；属「目标态」 | 不重复融合方案已落地内容（见 `ui-protocol/README.md` 分工） |
| `bone-metadata-ui-prototype.html` | 评审可点原型：UID-9 四 Tab + 场景包 + 双界面 + 逃逸弹层 | 生产代码 |
| `walkthrough-2026-10-09.md` + `preview-walk-*` / `preview-v39-*` / `preview-v32-*` / `preview-scene-*` | 走查证据与截图（**有效，以这些为准**） | 代替 PRD 验收口径 |
| `archive/preview-1～14-*.png`、`archive/preview-opt-*.html` | **历史归档**（见 `archive/README.md`）：10-09 前旧叙事（画布/旧文案），未随 v0.39 重截 | 不再作为评审依据 |
| `业务建模三入口页UI优化方案.md` | 诊断证据：D1–D4 / B1–B5 / O1–O5，每条绑定代码行号 + 业界对标（"是什么问题"） | 决策结论（归综述） |
| `业务建模三入口页最佳方案综述.md` | 决策综述：取舍与关键交互决策（"为什么这样改"）；结论载体 `../cases/mock-best-scheme.html`（外部引用，勿移动） | 过程稿（归 `../cases/archive/`） |
| 本 README | 入口与合理性边界 | — |

## 下一阶段：界面协议驱动渲染（ui-protocol）

详见 [`ui-protocol/README.md`](./ui-protocol/README.md)。核心：从「静态数据表建模」走向「业务对象建模」，保持声明式轻量（补表达力，不建画布/公式引擎/第二数据模型）。

## UI 是否合理（结论）

| 判定 | 项 | 为什么 |
|------|-----|--------|
| ✅ 保留 | 实体工作区 **结构｜界面｜数据｜发布**（无四步条） | 对齐 NocoBase/Airtable；PRD UID-9 |
| ✅ 保留 | **场景包只换数据**（巡检/补货/访客/**报销 L0**） | UC-04 通用性；步骤由 Phase UI 驱动 |
| ✅ 保留 | 自动/手写双卡片；报销 APPROVED→「确认打款」逃逸 | §0.7 + case L0；对称补货入库 |
| ❌ 不做进原型 | 报销 L1（父子/FILE/类别主数据） | `FIELD_TYPES` 无 REFERENCE/FILE；会误导 Must |
| ⚠ 旧图 | `archive/preview-1～14-*.png` / `archive/preview-opt-*.html`（已归档，见 `archive/README.md`） | 以 `preview-walk-*` / `preview-v39-*` / `preview-v32-*` / `preview-scene-*` 为准 |

## 四场景 ↔ 模板 ↔ case

| 侧栏场景 | 实体编码 | 验证什么 | 生产模板（T4） | case |
|----------|----------|----------|----------------|------|
| 设备巡检 | `t_device_inspect` | 够用 | `device_inspection` | — |
| 供应链补货 | `t_replenishment` | 边界+入库逃逸 | `replenishment` | — |
| 访客登记 | `t_visitor_pass` | 换皮 | `visitor_pass` | — |
| **费用报销** | `t_expense_claim` | 办公域 DECIMAL + 打款逃逸 | `expense_claim` | **L0**（L1 仅文档） |

## 推荐评审路径（约 5 分钟）

1. 打开原型 → `#scene=expense`（或侧栏「费用报销」）→ 「▶ 0→1 走通」
2. 结构看 DECIMAL/ENUM；数据 APPROVED 行点「确认打款」
3. 「下一步」串联换场景，确认步骤不变
4. 对照 case **L0**；缺口只读 case **L1 / §4**，勿要求原型演示父子表

深链：`#scene=expense` · `#scene=repl` · `#loop` · `#auto`
