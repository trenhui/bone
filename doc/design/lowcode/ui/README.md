# 元数据 UI 设计资产（索引）

> **专题包入口**：[`../README.md`](../README.md)  
> **产品定案**：PRD **v0.40**（§0.6 / §0.7 / §2.5.9）  
> **工程细则**：[`元数据工作区前端融合方案.md`](./元数据工作区前端融合方案.md)  
> **业务串联**：[`../cases/费用报销系统-元数据建模方案.md`](../cases/费用报销系统-元数据建模方案.md)（**L0** = 原型；**L1** 不进原型）  
> **串联序**：巡检 → 报销 → 补货 → 访客（= 原型 `cycleScene`）  
> **打款逃逸**：拟议 `ExpenseClaimApplicationService.confirmPayout`（禁订单 `PaymentApplicationService`）  
> **数据面设计**：[`../BONE-低代码Runtime数据面设计.md`](../BONE-低代码Runtime数据面设计.md)

## 资产分工

| 资产 | 职责 | 不做什么 |
|------|------|----------|
| `bone-metadata-ui-prototype.html` | 评审可点原型：UID-9 四 Tab + 场景包 + 双界面 + 逃逸弹层 | 生产代码；L1 头+明细 |
| `元数据工作区前端融合方案.md` | `file:line` / W0–W2 / 门禁 | 产品 Why/What（归 PRD） |
| `walkthrough-*.md` + `preview-*.png` | 走查证据与截图 | 代替 PRD 验收口径 |
| 本 README | 入口与合理性边界 | — |

## UI 是否合理（结论）

| 判定 | 项 | 为什么 |
|------|-----|--------|
| ✅ 保留 | 实体工作区 **结构｜界面｜数据｜发布**（无四步条） | 对齐 NocoBase/Airtable；PRD UID-9 |
| ✅ 保留 | **场景包只换数据**（巡检/补货/访客/**报销 L0**） | UC-04 通用性；步骤由 Phase UI 驱动 |
| ✅ 保留 | 自动/手写双卡片；报销 APPROVED→「确认打款」逃逸 | §0.7 + case L0；对称补货入库 |
| ✅ 保留 | ENUM 建模门 / 落地路线 Tab | 防「原型能演 = 生产能建」 |
| ❌ 不做进原型 | 报销 L1（父子/FILE/类别主数据） | `FIELD_TYPES` 无 REFERENCE/FILE；会误导 Must |
| ⚠ 旧图 | `preview-1～14-*.png` 未随 v0.39 重截 | 以 `preview-walk-expense-*.png` / `preview-v39-plan.png` 为准 |

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
