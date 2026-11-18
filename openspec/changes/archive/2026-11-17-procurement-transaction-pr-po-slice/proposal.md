# 采购交易 PR/PO 可验证切片

## Why

采购域 01 供应商主数据已交付（Supplier/Vendor 双角色状态机 + 5 个治理端点 + 前端治理页），但 04 交易模块在代码中零实现：`bone-init.sql` 无任何 `t_proc_*` 表，仓库无 `Requisition`/`PurchaseOrder` 聚合，也没有 `/api/v1/procurement/*` 端点。结果是平台能判定「供应商是否合格」，却无法完成「实际下单」这一步，整条 S2P 主链路断在供应商之后。

现在做的另一个理由是依赖恰好就绪：PO 下单最硬的前置是 Vendor 开通闸门（LLD 04 卷 M4-1/M4-2、总体 §12.2 必过项），而该闸门的数据源正是上一轮已交付并验证过的 `SupplierApplicationService.currentState()`。同时 PO 是 05 履约（Ack/ASN/GR/三单匹配）的操作对象，先有 PO 才能让履约模块拿到真实上游，而不是靠桩数据。

## What Changes

- **新增采购域模块组**（对齐总体 §4.2 模块划分与 §8.1 单应用多模块，应用范式对齐 `bone-apps/synap-claim`）：
  - `bone-apps/bone-procurement`（pom 聚合器）
  - `bone-apps/bone-procurement/bone-procurement-app`（Spring Boot 应用与装配，仅启动类/配置/能力开关）
  - `bone-apps/bone-procurement/bone-procurement-transaction`（交易上下文业务模块，DDD 四层）
- **新增 4 张表** 写入 `bone-init.sql`：`t_proc_requisition`、`t_proc_po`、`t_proc_po_line`、`t_proc_price_snapshot`；金额列统一 `DECIMAL(19,4)` + `currency CHAR(3)`，时间列 UTC `DATETIME(3)`，均带租户隔离与 `@Version` 乐观锁列
- **新增 6 个端点**（`/api/v1/procurement`）：
  - `POST /requisitions` 建请购
  - `POST /requisitions/{no}/convert` 转 PO（一次）
  - `POST /purchase-orders` 建 PO（含 Vendor 闸门校验）
  - `POST /purchase-orders/{no}/submit` 提交审批
  - `POST /purchase-orders/{no}/send` 下达并冻结价格快照
  - `GET /purchase-orders/{no}` 查详情（含行与快照引用）
- **新增领域模型与状态机**（显式迁移表 + fail-closed，未列出即拒绝）：
  - `Requisition`：`DRAFT → SUBMITTED → APPROVED → CONVERTED`，`CANCELLED` 终态
  - `PurchaseOrder`：`DRAFT → SUBMITTED → APPROVED → SENT`，`ACKNOWLEDGED`/收货段状态保留枚举但本期不推进；`CANCELLED` 终态
  - `PurchaseOrderLine`：随根一致性，`po_amount = Σ line_amount`
  - `PriceSnapshot`：只追加、不可变，含 `computed_hash`
- **Vendor 闸门**：通过业务侧 `VendorQueryPort` 读供应商投影；非 `ACTIVE_VENDOR` → `31003`，法人实体不匹配 → `31004`
- **前端交易控制台**：请购单与 PO 的列表、建单、提交/下达、详情；落在**新建独立子应用** `bone-frontend/apps/bone-procurement-app`（3014 / activeRule `/procurement`），复刻平台既有 qiankun 范式；供应商治理页从 `bone-masterdata-app` 迁入该应用
- **新增权限码**：`procurement:requisition:write/read`、`procurement:purchase-order:write/read`、`procurement:transaction:read/write`（含菜单种子）

## Capabilities

### New Capabilities

- `procurement/requisition`: 请购单从建单到转采购订单的生命周期行为，含审批后才可转单、转单后终态、预算引用与金额校验
- `procurement/purchase-order`: 采购订单生命周期、Vendor 开通闸门与法人隔离、订单金额与行金额一致性、下达时价格快照冻结与不可变、终态不可回写、非法迁移拒绝
- `procurement/transaction-console`: 交易控制台前端界面行为，含请购与 PO 的列表分页、建单表单、状态推进按钮的允许事件展示、失败降级与权限降级

### Modified Capabilities

`procurement/supplier-mdm-console`（前端落点变更）：供应商治理页从 `bone-masterdata-app` 迁入 `bone-procurement-app`，**行为契约不变**（5 端点、双角色状态、降级与 PII 白名单），仅改宿主应用、路由前缀（`/masterdata#/suppliers` → `/procurement#/suppliers`）与菜单种子；测试随文件迁移并保持全绿。后端 `procurement/supplier-mdm` 需求不变。

## Impact

- **新增代码**：3 个 Maven 模块（含 1 个聚合器）、`com.bone.procurement.transaction` 四层代码；新建前端子应用 `bone-procurement-app`（含 shell 注册与硬编码清单同步）与交易控制台页面
- **DDL**：`bone-init.sql` 新增 4 张 `t_proc_*` 表（本期首次引入采购业务表，此前该文件 `t_proc_` 零命中）
- **API**：6 个新端点与 6 个新权限码（requisition/purchase-order/transaction 各 read+write）；错误码沿用 LLD 04 卷 `31001–31004` / `31401–31405`
- **菜单/权限种子**：新增 2 个 migration SQL（权限码绑超管 + 「采购」菜单目录与 2 个叶子），须通过 G40 权限码与图标白名单门禁
- **依赖**：`bone-procurement-transaction` 通过 `VendorQueryPort` 依赖 `bone-masterdata` 的供应商状态查询；不直连对方表
- **不改动**：存量 `bone-apps/bone-app-purchase` 适配层（12 类，按能力基线 §7.2 路径 A 保留，不重写不删改）
- **明确非目标**：
  - Transfer / RTV 及其补偿编排
  - `PurchaseCost` + `PriceCondition` 条件引擎、PPV / Savings / 价格合规率投影（价格链留待与 03 合同价格链一起做；本期快照仅冻结行净价）
  - 预算系统真实占用/释放（`BudgetAclPort` 一期无真实上游，L19 待决；本期只记录 `budget_ref` 与金额，不做跨系统占用）
  - GR 收货推进、ASN/Ack、发票匹配与付款（属 05 履约与 Finance 域，PA-04 边界）
  - Outbox / Inbox / MQ / Saga（单应用单库，PA-08 明确不引入）
  - `bone-procurement-app` 的 Orchestrator 跨上下文编排（本期无编排需求）
- **已知偏差（如实记录，不在本期偿还）**：上一轮供应商模块落在 `bone-masterdata`（包 `com.bone.masterdata`），与 §4.2 要求的 `bone-procurement-supplier-mdm` 不符；本期不搬迁，作为独立后续 change 偿还
