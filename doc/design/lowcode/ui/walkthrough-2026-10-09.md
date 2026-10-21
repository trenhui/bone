# 原型闭环走查记录 · 通用性验证

> 原型：`bone-metadata-ui-prototype.html`（**v0.40**）。  
> 结论：**换场景不换路径**；串联序 **巡检 → 报销 → 补货 → 访客**。  
> **报销**：[`../cases/费用报销系统-元数据建模方案.md`](../cases/费用报销系统-元数据建模方案.md) **L0**；打款拟议 `confirmPayout`（非订单 Payment）。  
> 索引：[`README.md`](./README.md)

## 〇、版本基线

| 版本 | 要点 |
|------|------|
| v0.32 | C1/C2/C3 实测前置；ENUM 建模门；落地路线 Tab |
| v0.37 | 双界面分叉；发布成功三动作；B1–B4 |
| **v0.39** | 第四场景包 **费用报销 L0**（`t_expense_reimbursement`）；打款逃逸对称补货入库；与 case L0/L1 分层 |

### 实测三处推翻（仍有效）

| # | 实测结论 | 原型/PRD 落点 |
|---|---|---|
| C1 | MetaList/MetaForm 全仓零命中 | 落地路线 Tab；PRD §0.6.1 → 从零新建 |
| C2 | `FIELD_TYPES` 曾无 ENUM；模板丢 `enumValues` | ENUM 建模门；T1–T3 |
| C3 | `/…/data` 静默看错实体 | W0-P0 首位；负向断言 |

## 一、走查矩阵（4 场景 × 闭环步骤）

| 闭环步骤 | 设备巡检（够用） | 供应链补货（边界） | 访客登记（换皮） | **费用报销 L0（办公）** |
|---|---|---|---|---|
| ① 新建 RUNTIME | `t_device_inspect` | `t_replenishment` | `t_visitor_pass` | `t_expense_reimbursement` |
| ② 结构 | 6 字段 | 9 字段 + writeOnce | 7 字段 | 8 字段 · **DECIMAL** + 双 ENUM |
| ③ 界面 | Meta* 同源预览 | 同左 | 同左 | 同左 · 金额右对齐 |
| ④ 发布 | L1 热加载 | + 跨实体逃逸标注 | 同巡检 | 同巡检 |
| ⑤ 数据 | CRUD + 412 | 状态动作 + 📦 到货入库 | 同构 MetaList | 提交/审批/驳回 + **💰 确认打款** |
| ⑥ 1→n | 热加载字段 | `expected_arrival_date` | `plate_no` | **`cost_center`** |

顶部阶段条：「场景包只换数据 · 步骤不变（PRD §2.0）」。

## 二、关键截图证据

| 证据点 | 截图 |
|---|---|
| 补货 · 结构 | `preview-scene-replenishment.png` |
| 补货 · 发布 | `preview-walk-repl-pub.png` |
| 补货 · 数据 | `preview-walk-repl-data.png` |
| 访客 · 结构 | `preview-walk-visitor-fields.png` |
| 访客 · 界面 | `preview-walk-visitor-ui.png` |
| 巡检 · 界面 | `preview-walk-device-ui.png` |
| 落地路线（v0.32 历史） | `preview-v32-plan.png` · `preview-v32-enum-gate.png` |
| **报销 L0 · 结构** | `preview-walk-expense-fields.png`（**2026-10-10** 重截） |
| **报销 L0 · 界面** | `preview-walk-expense-ui.png` |
| **报销 L0 · 数据 + 打款** | `preview-walk-expense-data.png` |
| **落地路线 v0.39（含报销串联卡）** | `preview-v39-plan.png` |

## 三、通用性机制（为何加报销仍成立）

1. **场景包 = 纯数据**（字段/listCols/enumPill/escape），步骤由 Phase UI 驱动；  
2. **Meta\* 与业务名解耦**——报销只需新场景对象，不改四 Tab；  
3. **边界卡随场景**：补货→入库；报销→打款（case L0 §1.4）；  
4. **L1 不进 UI**：父子/FILE 只在 case §3–§4，避免「原型已演示 = Must」。

## 四、遗留提醒

- 生产 ENUM 依赖 T1–T3：**原型能演徽标 ≠ 生产能建枚举**（评审必说清）。  
- 历史 `preview-1～14-*.png` / `preview-1-canvas.png` 仍是 **10-09 前**旧叙事（画布/旧文案），**未批量重截**；以 `preview-walk-*` / `preview-v39-*` / 现场原型为准。

## 五、推荐路径（串联 case）

| 顺序 | 动作 | 对照 |
|------|------|------|
| 1 | `#scene=expense` → 走通闭环 | case **L0** §1 / §5 |
| 2 | APPROVED → 💰 确认打款 | case §1.4 逃逸 |
| 3 | 串联切补货看 📦 入库 | 对称 UC-05 |
| 4 | 只读 case **L1** | 缺口 G1–G5；不要求原型演示 |
