# bone-blueprint 元数据字段扩展设计（模拟真实定制场景）

> 版本 v1 ｜ 2026-09-29 ｜ 状态：待拍板
> 关联任务：基于业界真实场景优化 bone-metadata-server 前后端功能；对 bone-blueprint 表模拟真实场景做元数据字段扩展，并经 metadata-server 前后端串联验证打通。

## 一、现状盘点（定量）

### 1.1 blueprint 三表现状 vs 业界电商交易中台标配

| 表 | 现有业务字段 | 业界标配维度 | 缺失能力 |
|---|---|---|---|
| `t_order` | customerId、totalAmount、status、version、createdAt/updatedAt（约 6 项） | 单号/来源/收货/物流/金额三口径/发票/时间轴五刻度/风控（约 20 项） | **缺 70%**：无业务单号、无收货信息、无物流、无运费优惠拆分、支付后无 paidTime |
| `t_order_item` | productId、productName、quantity、unitPrice、subtotal（5 项） | SKU 编码/规格快照/行优惠/赠品标记（约 9 项） | 缺 45%：无 SKU 快照，退货对账不可追溯 |
| `bp_payment` | orderId、amount、channel、status、channelTradeNo、payUrl、paidAt、refundAmount（8 项） | 币种/付款方/渠道手续费/结算/回调原文/支付有效期（约 15 项） | 缺 50%：无回调原文留存（争议无凭证）、无渠道费（对账缺平）、无支付过期时间 |

### 1.2 metadata-server 能力现状

- 建模面：实体/字段/关系/物理结构（publish-preview、发布执行 ALTER TABLE、drop-column/drop-drifted）/模板，全齐。
- 运行时数据面：`RuntimeRecordController`（`/api/v1/runtime/entities/{code}/records`，fields|sort|q、If-Match 乐观锁、Idempotency-Key 幂等）。
- 类型系统（`toSqlType` 映射）：STRING/EMAIL/URL/TEXT/JSON、LONG/INT、DECIMAL（**硬编码 DECIMAL(20,6)**）、BOOLEAN、DATE/DATETIME/TIME。
- **关键缺口**：
  - G-① **无逆向建模**：存量物理表（如 t_order）无法一键采集为目录实体，只能手工逐字段录入；
  - G-② blueprint 三表均未注册目录（实测 `/api/v1/metadata/entities` 租户 0 下 19 个实体无一命中 t_order/bp_payment）；
  - G-③ DECIMAL 精度不可配（发布即 20,6，与业务表的 DECIMAL(10,2) 金额口径漂移，validateForPublish 会判 type drift）。

## 二、业界对标字段设计（本次扩展目标集）

参照：电商交易中台（订单域/支付域通用模型）、Salesforce Order Management、SAP BRM 的标准字段集。按「领域核心字段（机制 A）」与「元数据扩展字段（机制 B）」分列（机制见第三章）。

### 2.1 t_order 订单主表

| 字段 | 类型 | 必填 | 索引 | 机制 | 业界出处/场景价值 |
|---|---|---|---|---|---|
| order_no | STRING(32) | 是 | 唯一 | A | 业务单号，对外展示与客服检索键，与物理 id 分离（全行业标配） |
| channel_source | STRING(32) | 否 | 有 | A | 订单来源渠道 APP/H5/小程序/POS，运营分析第一维度 |
| receiver_name | STRING(64) | 否 | 无 | B | 收货人 |
| receiver_phone | STRING(20) | 否 | 无 | B | 收货电话（脱敏存储） |
| receiver_region | STRING(128) | 否 | 无 | B | 省/市/区（三级合并存储） |
| receiver_address | STRING(255) | 否 | 无 | B | 详细地址 |
| freight_amount | DECIMAL | 否 | 无 | A | 运费；金额三口径 total = items + freight − discount |
| discount_amount | DECIMAL | 否 | 无 | A | 优惠总额（营销核算） |
| paid_time | DATETIME | 否 | 无 | A | 支付完成时刻；与 createdAt/shipAt/deliverAt 构成生命周期时间轴 |
| buyer_message | STRING(255) | 否 | 无 | B | 买家留言 |
| invoice_title | STRING(128) | 否 | 无 | B | 发票抬头 |
| logistics_company | STRING(64) | 否 | 无 | B | 承运商 |
| logistics_no | STRING(64) | 否 | 有 | B | 运单号，履约跟踪键 |
| expected_delivery_time | DATETIME | 否 | 无 | B | 承诺送达（SLA 监控） |

> 说明：shipAt/deliverAt 已可由状态机推导，本期不入库；risk_level/tags 留作二期。

### 2.2 t_order_item 订单明细

| 字段 | 类型 | 必填 | 机制 | 场景价值 |
|---|---|---|---|---|
| sku_code | STRING(64) | 否 | B | SKU 编码，库存对账键 |
| sku_spec | STRING(255) | 否 | B | 规格快照（下单时快照，防后续改规格污染历史单） |
| discount_amount | DECIMAL | 否 | B | 行优惠（营销归因到行） |
| gift_flag | BOOLEAN | 否 | B | 赠品标记（赠品行不参与金额计算） |

### 2.3 bp_payment 支付单

| 字段 | 类型 | 必填 | 机制 | 场景价值 |
|---|---|---|---|---|
| currency | STRING(8) | 是(默认CNY) | A | 币种，跨境前置 |
| pay_expire_at | DATETIME | 否 | A | 支付有效期，驱动超时关单（业界 15min~24h） |
| payer_account | STRING(128) | 否 | B | 付款账号（脱敏） |
| channel_fee | DECIMAL | 否 | B | 渠道手续费，对账缺平依据 |
| settle_status | STRING(16) | 否 | B | 结算状态 UNSETTLED/SETTLED |
| callback_raw | JSON | 否 | B | 渠道回调原文，争议仲裁与审计凭证（支付域标配） |
| refund_no | STRING(32) | 否 | B | 退款单号，与 channelTradeNo 对账 |

### 2.4 数量汇总

- 合计新增 **25 字段**：机制 A（领域硬编码）9 个、机制 B（元数据驱动）16 个。
- 类型分布：STRING 14、DECIMAL 5、DATETIME 4、BOOLEAN 1、JSON 1 —— 全部落在 metadata-server 现有 11 类之内，**无需扩类型系统**。

## 三、扩展机制设计（关键架构决策）

| 维度 | A 物理硬编码 | B 元数据驱动 |
|---|---|---|
| 路径 | 改 Order/Payment 领域模型 → DDL → API DTO → 前端（5 层） | 目录加字段 → publish-preview → 发布自动 ALTER TABLE → runtime 数据面即刻 CRUD（0 行 Java） |
| 强类型/领域行为 | 完整（金额校验、状态机可引用） | 无（动态 Map 读写） |
| 响应定制速度 | 慢（全链路） | 快（分钟级，可视化） |
| 风险 | 改核心链路需回归 | 不进入 blueprint 应用层 API |

**推荐 A+B 分层**：
- 机制 A 只收「领域必用」的 9 个核心字段（单号、来源、运费/优惠、paidTime、币种、有效期）——它们参与金额不变量与状态机；
- 机制 B 承接长尾定制 16 个字段——验证核心卖点：**不改一行 Java 即可给业务表扩展字段，并经 metadata-app 可视化操作与 runtime 数据面读写**，这正是"真实定制场景"的本义。

## 四、打通链路设计（前后端串联验证，五步）

1. **逆向建模导入（新增能力，见 §五 O1）**：`POST /api/v1/metadata/entities:import-from-table`，采集 information_schema 生成实体+字段草稿（DRAFT），解决 G-①；三表一键导入。
2. **注册与校验**：三实体 deliveryMode=RUNTIME，发布走 publish-preview → 与存量物理表结构比对（0 漂移）→ PUBLISHED。此时 DECIMAL 口径问题（G-③）必须先解，否则 validate 判 type drift。
3. **元数据扩展（机制 B）**：经 metadata-app 建模工作台对三实体加 16 个 B 字段 → publish-preview 预览 16 条 ALTER TABLE ADD COLUMN → 发布执行 → 实库扩列完成（加列不破坏存量数据，实现已保证不加 NOT NULL）。
4. **双向读写验证**：
   - blueprint 侧：起 8082，走真实下单 → 支付链路写入 t_order/bp_payment；
   - metadata 侧：RuntimeRecordController 对 t_order 分页/q 查询/更新（If-Match），确认**同一张表两套读写互通**、租户隔离一致（X-Tenant-Id: 0 vs 1001 双向探测）。
5. **前端串联**：metadata-app（3004）EntityDetail 查看三实体字段全景 → RuntimeDataManagement 对扩展字段做 CRUD → publish-preview 面板核对 ALTER 语句；blueprint 流程经 API 层验证（无独立微应用，见待拍板 #4）。

## 五、metadata-server 前后端优化清单（场景驱动）

| 编号 | 优化项 | 优先级 | 说明 |
|---|---|---|---|
| O1 | **逆向建模导入**：后端 `entities:import-from-table`（information_schema → 实体+字段草稿，标识符/保留字校验）+ 前端「从存量表导入」向导 | P0 | 业界元数据平台标配；本场景第 1 步的前置 |
| O2 | DECIMAL 精度可配：MetaField 增加 precision/scale 语义（复用 numeric_precision 列），发布与漂移检测按可配口径 | P0 | 不解则 t_order 金额字段注册即 drift（G-③ 阻断项） |
| O3 | RuntimeDataManagement 补强：q 查询、If-Match 409 冲突、Idempotency-Key 重放三个真实场景的 UI 呈现 | P1 | 数据面能力已实现但 UI 未暴露 |
| O4 | publish-preview 增加「即将 ADD 的列」逐条清单高亮（现有摘要已含 DDL，加前端渲染） | P2 | 发布前人工确认体验 |
| O5 | 字段注释/业务语义必填校验（comment 为空 WARNING 级） | P2 | 业界元数据治理要求语义完备 |

## 六、实施批次

| 批次 | 内容 | 交付物 |
|---|---|---|
| Batch 1 | O1+O2 后端（含单测、spotless、ArchUnit 过门禁） | metadata-server 新端点 + 前端导入向导 |
| Batch 2 | 三表导入注册 + 发布 + 机制 B 16 字段扩展发布 | 实库 ALTER 完成 + 目录快照报告 |
| Batch 3 | 机制 A 9 字段：blueprint 领域模型 + DDL + API 五层改造（含金额三口径不变量） | blueprint 全链路字段 |
| Batch 4 | §四第 4/5 步串联验证（双租户、双向读写、UI 巡检截图） | 验证报告 |

## 七、待拍板清单

1. **A+B 分层机制**是否认可？机制 A 最小集 9 字段是否同意（或全部 25 个都走 B 先验证，A 留二期）？
2. **O1 逆向导入权限口径**：管理员专属（metadata:model:write）且仅允许导入本租户 schema 的表，是否同意？
3. **O2 DECIMAL 精度**：放开 precision/scale 会改 DDL 生成与漂移检测口径，属 L3；是否本轮做（不做则导入时金额字段按 20,6 建模、靠漂移锚定豁免）？
4. **blueprint 无独立前端**：串联验证用「metadata-app UI + blueprint API 层」替代 UI 全链路，还是需要为 shell 新增 blueprint 菜单（工作量大，建议不做）？
5. **实库扩列方式**：机制 B 的 ALTER TABLE 由元数据引擎在发布时自动执行（本次验证的核心卖点），不走 `scripts/migration/` 人工脚本——此口径是否认可（与 ADR 生产迁移策略不冲突：本验证仅限本地 dev 库）？
