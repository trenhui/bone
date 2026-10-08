# Bone 主数据 ↔ blueprint 端到端验证报告（实时复核 · 2026-10-01）

> 复核人：AI
> 方法：对**正在运行**的服务（gateway:8888 / masterdata:8084 / blueprint:8082 / iam:8081 / redis:6379 全部 LISTEN）独立重放关键场景，不依赖报告自述。
> 配套：本报告是对《前后端打通验证报告》（对话内，未落盘）的**实时复核与纠错**，重点修正其中一处与线上不符的数字漂移。

---

## 0. 结论

1. **任务真实落地、服务常驻**：7 处断链修复代码均真实接线，masterdata/blueprint 以新 jar 常驻。
2. **核心链路打通且通过**：我独立重放的 masterdata 写入治理、blueprint 主数据驱动交易均符合预期。
3. **⚠️ 一处文档漂移必须纠正**：报告 B 段称「VIP 折扣率主数据化生效 → 227.04（0.88）」，但**线上 VIP 实付折扣率 = 0.80，订单金额 = 206.4**。0.80 来自**定价规则中心（PRICING_RULE）的 `vip=0.80` 运营规则**，它压过了主数据 `CUSTOMER_LEVEL.VIP=0.88`。报告对该点的"主数据驱动"表述不成立。

---

## 1. 实时验证通过项（直接打真实服务）

| 项 | 实时结果 | 结论 |
|----|----------|------|
| IAM 登录（admin / approver） | token 正常（tenantId=0） | ✅ |
| fix#2 keyword 搜索 `MD_PRODUCT` / `zzznotexist` | total=1 / total=0 | ✅ 修复生效 |
| fix#5 实体 `entityCode` 回显 | 返回 `MD_PRODUCT` | ✅ 修复生效 |
| fix#1 实体 disable 不带 body | HTTP **400** | ✅ 修复生效 |
| fix#3 缺必填字段建记录 | HTTP **400** `MD_RECORD_FIELD_VALIDATION_FAILED: 字段[code]为必填项；字段[price]为必填项` | ✅ 字段定义实时生效 |
| fix#6 blueprint 下单（VIP 客户1001 × 商品1001×2） | HTTP **201**，`productName=无线鼠标`、`unitPrice=129`（前端假名/1.00 被覆盖） | ✅ 全链路打通 |
| fix#6 未发布商品 99999 下单 | HTTP **409** `BP_ORDER_PRODUCT_NOT_PUBLISHED` | ✅ 门禁生效 |

---

## 2. 仅代码确认、未跑真实流程的项（如实标注）

- **fix#4 发布门禁 / SoD 审批闭环**：代码坐实——`MasterDataRecord.publish()` 第128行「待审批记录不能直接发布」、`RecordApplicationService.publish()` 第136行 `WORKFLOW_APPROVAL_REQUIRED`、`approve/reject` 第181/203行 `SOD_VIOLATION`。未跑真实「提交→审批→发布」闭环。
- **fix#7 前端六态**：仅 `tsc --noEmit` 确认，未做页面级巡检。
- **118 / 223 单测**：本报告用真实服务重放替代，未重跑 `mvn test`。

---

## 3. 关键发现：VIP 折扣率来源漂移（报告必须修正）

折扣率三级回退（`AbstractRateOrderPriceCalculator.resolveDiscountRate`）：

| 级别 | 来源 | VIP 取值 |
|------|------|----------|
| ① | 定价规则中心 `PRICING_RULE`（`MetadataPricingRuleAdapter`） | **0.80** ← 线上实测生效值 |
| ② | 主数据 `CUSTOMER_LEVEL.VIP` | 0.88（库内已查实） |
| ③ | 本地常量 `VipOrderPriceCalculator.discountRate()` | 0.9 |

**实测 VIP 订单 `totalAmount = 206.4 = 258 × 0.80`**。0.80 既不是主数据 0.88、也不是本地常量 0.9，只能来自第①级规则中心下发的 `vip=0.80` 规则。

自洽佐证：MEMBER / ENTERPRISE 两档与报告一致（211.56 / 167.70），因为规则中心**没有**这两档的覆盖规则，回退到第②级主数据 0.82 / 0.65。即规则中心**仅对 VIP 下发了 0.80 覆盖规则**，与主数据 0.88 冲突。

> 逻辑闭环结论（无需再打线上即可确定）：0.80 的唯一可能来源是第①级规则中心。线上 `GET /api/v1/runtime/entities/PRICING_RULE/records` 查询因审批超时未跑，但代码回退链 + 已查实的主数据值 + 已确认的本地常量已将结论锁定。

---

## 4. 最推荐优化方案

### 4.1 决策：vip=0.80 是有意还是遗留？
- **若为遗留/误配**（与"折扣率主数据化"治理意图相悖）：在 `PRICING_RULE` 禁用或删除 `vip=0.80` 规则 → VIP 回归主数据 0.88 → 订单 227.04，与报告意图一致。
- **若为有意活动价**：保留规则，但报告须如实写「VIP 折扣由规则中心 0.80 驱动，实付 206.4」。

### 4.2 治理盲点修复（根因类）
`AbstractRateOrderPriceCalculator.resolveDiscountRate()` 当前仅在「②回退③」时日志；当「①覆盖②」时**无任何日志**，导致"报告写主数据、实际规则中心生效"的静默漂移未被发现。建议在第①级命中时显式 `log.warn`（带规则中心值与主数据值），使覆盖可见。

### 4.3 报告 B 段重基线
- VIP：227.04 → **206.4**（若保留规则中心 0.80）；或维持 227.04（若移除规则）。来源须注明"规则中心优先于主数据"。
- MEMBER / ENTERPRISE 维持 211.56 / 167.70（已与线上一致）。

---

## 5. 待执行（需你确认 / 授权）

1. **实时确证**：授权跑 `GET /api/v1/runtime/entities/PRICING_RULE/records`（服务账号 tenant_admin，tenant 0 与 1001），打印 `vip=0.80` 规则原值 / 启用状态。
2. **数据修复（营收影响，须业务确认）**：若决定移除遗留规则，执行禁用/删除该 `PRICING_RULE` 记录（VIP 0.80→0.88 意味着客户单价上浮，需审批）。
3. **代码修复**：按 4.2 在 `resolveDiscountRate` 增加覆盖日志（小改动、无行为变化）。

---

## 6. 本机复跑（已验证链路）

```bash
# 登录 + 关键场景重放（摘要版）
GW=http://127.0.0.1:8888; MD=http://127.0.0.1:8084; BP=http://127.0.0.1:8082
# 1) keyword 搜索
curl -s "$MD/api/v1/masterdata/entities?pageNum=1&pageSize=50&keyword=MD_PRODUCT" -H "Authorization: Bearer $TOK" -H "X-Tenant-Id: 0"
# 2) 缺必填建记录（期望 400）
curl -s -X POST "$MD/api/v1/masterdata/records/entity/$PID" -H "Authorization: Bearer $TOK" -H "X-Tenant-Id: 0" -H 'Content-Type: application/json' -d '{"data":{"name":"脏数据"}}'
# 3) blueprint VIP 下单（期望 201，金额 206.4）
curl -s -X POST "$BP/api/v1/orders" -H "Authorization: Bearer $BTOK" -H "X-Tenant-Id: 0" -H 'Content-Type: application/json' -d '{"customerId":1001,"items":[{"productId":1001,"productName":"x","quantity":2,"unitPrice":1.00}]}'
```
