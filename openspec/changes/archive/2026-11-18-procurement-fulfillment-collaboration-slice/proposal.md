# Proposal：采购履约核心协同闭环切片

## Why

采购域已交付供应商主数据（01）、交易 PR+PO（04），平台能判定「供应商是否合格」并完成「下单」，但 PO 下达之后的协同链路在代码中零实现：`bone-init.sql` 无 `t_proc_po_ack` / `t_proc_asn` / `t_proc_invoice_submission` / `t_proc_ir_exception`，仓库无 PO 确认、发货通知、自助开票、发票例外聚合，也没有 `/api/v1/procurement` 的履约端点。结果是 S2P 主链路断在「PO 已下达」之后：供应商无法确认、无法发货、无法开票，采购方拿不到收货与发票协同凭证，端到端闭环无从谈起。

现在做的理由是依赖恰好就绪：LLD 05 卷（v0.3）已给出 PO Ack / ASN / 发票提交 / IR 例外的字段级契约、状态机与负向探针，而它们的操作对象正是上一轮已交付并验证的 `PurchaseOrder`（`SENT` 状态、`po_no`+`po_version`、行净价快照）。先有 PO 才能让履约拿到真实上游，而不是靠桩数据；同时 05 卷明确「协同全量二期、门户只读一期」，本轮取其中可独立验证的协同闭环，规模与上一轮 PR+PO 切片相当，可完整交付而非留一地桩。

## What Changes

- **新增履约业务模块**（对齐总体 §4.2 模块划分与 §8.1 单应用多模块，应用范式对齐已有 `bone-procurement-transaction`）：`bone-apps/bone-procurement/bone-procurement-fulfillment`（DDD 四层：domain / application / adapter/web / infrastructure），挂到 `bone-procurement` 聚合器与 `bone-procurement-app` 装配。
- **新增 5 张表** 写入 `bone-init.sql`：`t_proc_po_ack`、`t_proc_asn`、`t_proc_asn_line`、`t_proc_invoice_submission`、`t_proc_ir_exception`；金额列 `DECIMAL(19,4)` + `currency CHAR(3)`、数量 `DECIMAL(19,6)`、时间 UTC `DATETIME(3)`，均带租户隔离与 `@Version` 乐观锁列；唯一键覆盖幂等业务键。
- **新增内部端点**（`/api/v1/procurement`，复用现有采购权限体系：`procurement:fulfillment:read/write`）：
  - `POST /purchase-orders/{no}/ack` 行级 PO 确认（855 类比，`po_no+po_version` 幂等 + CAS，未确认终态拒绝）
  - `POST /asns` 发货通知（856 类比，`asn_no` 唯一，必须引用已确认 PO 行，包装层级/SSCC 校验）
  - `POST /invoices` 自助开票（810 类比，`(tenant_id,invoice_no,vendor_id)` 去重，金额 ≤ PO 未开票余额，只提交不裁决）
  - `GET /purchase-orders/{no}/fulfillment` 履约视图（Ack/ASN/开票/例外聚合只读）
  - `GET /ir-exceptions` 发票例外列表（cursor 分页）
  - `POST /ir-exceptions/{no}/supplement` 例外补料/申诉（无放行权限）
- **新增领域模型与状态机**（显式迁移表 + fail-closed，未列出即拒绝）：
  - `PoAck`：`ack_type` 三态 FULL/PARTIAL/REJECT + 行级确认结果；`(po_no,po_version)` 幂等，已关闭 PO 不可确认
  - `ASN`：`CREATED → TRANSMITTED → RECEIVING_MATCHED → RECONCILED`，`CANCELLED` 终态；未确认 PO 直接发 ASN 拒绝（`31503`）
  - `InvoiceSubmission`：`DRAFT → SUBMITTED → MATCH_PENDING → MATCHED / BLOCKED / REJECTED`；997 语法回执仅置「语法已确认」绝不置 `MATCHED`（`31506`）
  - `IRException`：`OPEN → SUPPLEMENTED → UNDER_REVIEW → RESOLVED / REJECTED`；采购域不可自行放行（`31505`），`RESOLVED` 只由 Finance 事件回写
- **跨上下文 INBOUND 端口（fail-closed）**：定义 `InventoryAclPort`（`GoodsReceived`）/ `FinanceAclPort`（`InvoiceMatched` / `PaymentRecorded`）端口与默认拒绝桩，运行期不触发真实上游；用领域测试夹具验证「只回写、不裁决」状态机，杜绝假绿。与上一轮 `VendorQueryPort` 做法一致。
- **容差配置化**：发票金额校验的容差按供应商/品类可配置（禁止硬编码阈值），本轮提供配置端口 + 严格默认实现；TDS=ΣIT1±SAC 逐行舍入后汇总校验。
- **前端履约页**：在现有独立子应用 `bone-frontend/apps/bone-procurement-app`（3014 / activeRule `/procurement`）增加「履约协同」页：PO 确认、ASN 登记、开票提交、例外看板与补料，复刻平台既有 qiankun + `@bone/ui` 范式，不新建独立门户、不引入外部账号体系。
- **新增权限码**：`procurement:fulfillment:read`、`procurement:fulfillment:write`（含菜单种子，绑超管，通过 G40 权限码/图标白名单门禁）。

## Capabilities

### New Capabilities

- `procurement/po-acknowledgement`: PO 行级确认（855 类比）——`po_no+po_version` 幂等 CAS、FULL/PARTIAL/REJECT 三态、已关闭 PO 拒绝确认、确认后 PO 进入可发货态。
- `procurement/asn`: 发货通知（856 类比）——`asn_no` 唯一去重、必须引用已确认 PO 行、包装层级与 SSCC 校验、显式状态机与非法迁移拒绝。
- `procurement/invoice-submission`: 供应商自助开票（810 类比）——`(tenant_id,invoice_no,vendor_id)` 去重、金额 ≤ PO 未开票余额、TDS 校验、997 语法回执不得置 `MATCHED`、只提交不裁决匹配结果。
- `procurement/ir-exception`: 发票例外台账——例外码/补料/申诉/状态，状态只由 Finance 事件回写，采购域无放行接口，重复 `exception_code` 幂等。
- `procurement/fulfillment-console`: 履约协同前端行为——PO 确认/ASN 登记/开票提交/例外看板与补料，列表分页、状态推进按钮的允许事件展示、失败与权限降级。

### Modified Capabilities

（`procurement/purchase-order` 的行为不变——本轮不修改 PO 聚合状态机，仅新增以 PO 为上游的履约能力；无需 delta spec。）

## Impact

- **新增代码**：1 个 Maven 模块（`bone-procurement-fulfillment`，DDD 四层）；`bone-procurement-app` 增加该模块的组件扫描；前端 `bone-procurement-app` 新增履约协同页与 API。
- **DDL**：`bone-init.sql` 新增 5 张 `t_proc_*` 履约表（首次引入履约业务表）。
- **API**：6 个新端点与 2 个新权限码；错误码沿用 LLD 05 卷 `31501–31506` 段（ASN/GR 不符、发票号重复、PO 未确认、超未开票余额、越权放行、997 当作业务接受）。
- **菜单/权限种子**：新增 migration SQL（权限码绑超管 + 「采购 → 履约协同」菜单叶子），须通过 G40 门禁。
- **依赖**：`bone-procurement-fulfillment` 通过 `PoQueryPort` 只读依赖 `bone-procurement-transaction` 的 PO 状态与行快照，通过 `InventoryAclPort` / `FinanceAclPort` 承接跨上下文 INBOUND（默认 fail-closed，不直连对方表、不接真实上游）。
- **不改动**：存量 `bone-apps/bone-app-purchase` 适配层（按能力基线 §7.2 路径 A 保留）；已交付的 supplier-mdm、transaction 模块与前端采购子应用宿主。
- **明确非目标（本轮留待后续 change / LLD 标注二期）**：
  - 独立 `supplier-portal` 前端 + BFF + 外部用户体系（UserType.EXTERNAL_USER 仅枚举、L23 待决）
  - 真实 EDI/X12 网关对接与 997/999 实际解析（L21 待决，本轮为 REST+JSON 语义类比）
  - `GoodsReceived` / `InvoiceMatched` / `PaymentRecorded` 真实消费与 GR 只读投影、三通道对账作业（依赖 Inventory/Finance ACL 交付 F-A，未交付前端口预留、运行期不触发）
  - HMAC Webhook 验签与四类负向探针（属门户/集成边界，二期）
  - Finance 真实裁决三单匹配与放行（PA-04，采购域只提交、只回写）
  - Outbox / Inbox / MQ / Saga（单应用单库，PA-08 明确不引入）
- **已知偏差（如实记录）**：外部账号隔离与门户安全在本轮以「内部采购角色 + 供应商维度可见性过滤」近似，不作为 B2B 外部用户体系交付；跨租户/跨供应商越权仍按 403 + 零数据 fail-closed 验证。
