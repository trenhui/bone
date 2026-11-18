# Tasks：采购履约核心协同闭环

## 1. 模块脚手架与装配

- [x] 1.1 新建 `bone-apps/bone-procurement/bone-procurement-fulfillment` Maven 模块（pom 依赖对齐 transaction），加入 `bone-procurement/pom.xml` 聚合器；验证 `mvn -o -f bone-procurement/pom.xml validate` 通过且模块被识别 <!-- comet-task:36071334-d5a9-4026-9c95-befb46980aa4 -->
- [x] 1.2 `bone-procurement-app` 装配履约模块扫描与依赖；验证 `ProcurementApplication` 启动类测试（能力开关）仍绿、应用能加载到履约包 <!-- comet-task:cc774fd4-9346-4a67-a7a9-e2cfbad6933b -->
- [x] 1.3 建立 DDD 四层包骨架（domain/application/adapter/web/infrastructure）与 `ArchitectureTest`（依赖方向、Controller `@PreAuthorize`、仓储白名单）；验证架构测试通过且注入违规转红 <!-- comet-task:4deb3a2f-d0ea-4468-a9b1-a089da703c69 -->

## 2. DDL 与领域模型

- [x] 2.1 `bone-init.sql` 新增 5 张履约表（`t_proc_po_ack`、`t_proc_asn`、`t_proc_asn_line`、`t_proc_invoice_submission`、`t_proc_ir_exception`），金额/数量/时间遵循 LLD §14 类型基线，唯一键覆盖幂等业务键；验证建表脚本可执行且 `ProcurementEntityDdlCoverageTest` 同构列级对账通过 <!-- comet-task:2cc2eace-f0cc-4ab7-b99f-650ac7b5ddf8 -->
- [x] 2.2 实现 `PoAck` 聚合与 ack_type 三态、行级确认建模（`(po_no,po_version)` 幂等）；验证 `PoAckTest` 覆盖幂等/终态拒绝 <!-- comet-task:7df46352-31c3-4319-9eb3-b53f2a2ed667 -->
- [x] 2.3 实现 `ASN` 聚合（`CREATED→TRANSMITTED→RECEIVING_MATCHED→RECONCILED`，`CANCELLED` 终态）与 `AsnLine`/SSCC 包装引用校验；验证 `AsnStatusTest` 显式迁移表 + 非法迁移 fail-closed 用例通过（注入必红） <!-- comet-task:da88a7b2-546b-46a3-b4d1-e0e6107921dc -->
- [x] 2.4 实现 `InvoiceSubmission` 聚合与状态机（`DRAFT→SUBMITTED→MATCH_PENDING→MATCHED/BLOCKED/REJECTED`），997 语法回执不越级；验证 `InvoiceSubmissionStatusTest` 覆盖 `31506` 拒绝 <!-- comet-task:77e485e9-e8d0-418c-97a7-a01f00e9972c -->
- [x] 2.5 实现 `IRException` 聚合与状态机（`OPEN→SUPPLEMENTED→UNDER_REVIEW→RESOLVED/REJECTED`），采购域无 `RESOLVED` 入口；验证 `IrExceptionStatusTest` 覆盖 `31505` 越权拒绝 <!-- comet-task:67ff4551-34a6-41c9-abcd-91fd9c348ef5 -->
- [x] 2.6 实现 `Money` 复用/对齐 transaction 的 Money 语义与 `DECIMAL(19,4)` 精度；验证金额/币种/D1–D6 精度用例通过 <!-- comet-task:670b0086-5d66-458d-a649-e4f28e357960 -->

## 3. PO 履约状态受控解锁

- [x] 3.1 `PoEvent` 新增 `ACKNOWLEDGE`，`PoStatus` 仅为 `SENT→ACKNOWLEDGED` 新增一条迁移，其余履约保留态仍注册空表 fail-closed；验证 `PoStatusTest` 增补断言「仅此一条新迁移、保留态仍拒绝」 <!-- comet-task:8557b7c5-6a95-4145-84a9-56b629c42d82 -->
- [x] 3.2 确认锚点：PO 标记为已确认后成为 ASN 可引用的上游；验证 transaction 模块既有用例全绿、无越级迁移 <!-- comet-task:2d3f3d48-cb9a-487d-b569-a450658434c2 -->

## 4. 端口与 fail-closed 桩

- [x] 4.1 定义 `PoQueryPort`（只读 PO 状态/行/净价快照）、`InventoryAclPort`（GoodsReceived）、`FinanceAclPort`（InvoiceMatched/PaymentRecorded）、`ToleranceConfigPort`；验证端口 default 方法 fail-closed（返回不可用/UNKNOWN） <!-- comet-task:1de44504-ff99-40af-84c3-7e0c543a3d61 -->
- [x] 4.2 `PoQueryPortAdapter` 只读实现（经 transaction application service）；验证集成测试能读到真实 PO 与行快照 <!-- comet-task:bd2d4506-392c-4593-a27d-727a291486f5 -->
- [x] 4.3 跨上下文默认桩实现（运行期不触发真实上游）+ 领域测试夹具注入事件；验证 M5-4/M5-6/M5-11 负向探针：上游不可用时状态保持待态、不推断通过 <!-- comet-task:88cd6e79-7f27-47f5-84f3-15daf6f44af6 -->
- [x] 4.4 `ToleranceConfigPort` 默认严格实现 + 按供应商/品类的可配置读取（禁止硬编码阈值）；验证 TDS 校验 M5-12 注入必红、还原必绿 <!-- comet-task:0bb8eae0-06ab-42ee-9cac-71c025ecfa7f -->

## 5. 应用服务与命令

- [x] 5.1 `PoAckApplicationService.ack`：供应商身份绑定 + po_no+po_version CAS 幂等 + 已关闭 PO 拒绝 + 驱动 PO `ACKNOWLEDGED`；验证 `PoAckApplicationServiceTest` 覆盖 M5-1/INV-01/INV-02 <!-- comet-task:6c9a275e-9c6b-46a0-b4fd-b78b808c7d5b -->
- [x] 5.2 `AsnApplicationService.create/transmit`：asn_no 唯一 + 行引用已确认 PO + 数量/包装校验 + 显式迁移；验证 `AsnApplicationServiceTest` 覆盖 M5-3（未确认拒绝）、INV-03/04、包装引用 <!-- comet-task:8a9a8454-eb0f-4185-826d-d6e87385e89d -->
- [x] 5.3 `InvoiceSubmissionApplicationService.submit`：去重 + 未开票余额校验 + TDS 校验 + 只提交不裁决；验证 `InvoiceSubmissionApplicationServiceTest` 覆盖 M5-2/M5-5/M5-12/M5-7 <!-- comet-task:ff682fa0-1650-46ea-b021-304ec2eeb3df -->
- [x] 5.4 `IrExceptionService` 登记/补料 + Finance 事件回写（只回写不裁决）；验证 `IrExceptionServiceTest` 覆盖 M5-6、重复码幂等、Finance 事件置位 <!-- comet-task:38992773-de03-4101-92db-1d3e7b32bf23 -->
- [x] 5.5 履约只读聚合查询（PO Ack/ASN/开票/例外）与例外 cursor 分页列表；验证查询测试覆盖分页与跨供应商过滤零数据 <!-- comet-task:3ec32de2-24e7-4822-beef-dd7aacb3f7a1 -->

## 6. Web 契约与权限

- [x] 6.1 6 个内部端点 Controller（`/api/v1/procurement/*`），`@PreAuthorize` + `procurement:fulfillment:read/write`，错误码 `31501–31506` + `409 invalid_transition`；验证 `ProcurementControllerContractTest` 同构扩展全绿 <!-- comet-task:4f963f5a-f61d-4a85-8dc9-c5c15a2ba2db -->
- [x] 6.2 `ProcurementExceptionHandler` 扩展履约异常到 RFC 9457 兼容错误体；验证契约测试断言错误码与 problem+json 结构 <!-- comet-task:147ae7a1-c999-42c8-9c15-24c87fdde4e4 -->
- [x] 6.3 新增权限码 migration SQL（绑超管）+「采购 → 履约协同」菜单叶子（避开 MySQL 保留字别名）；验证 migration 在 MySQL exit=0、G40 权限码/图标门禁通过、无残留临时表 <!-- comet-task:37784fa5-1035-4802-9539-d11bc967220b -->

## 7. 前端履约协同页

- [x] 7.1 `fulfillmentApi.ts`（6 端点）+ `fulfillment` types；验证 `fulfillmentApi.test.ts` 覆盖请求/响应映射 <!-- comet-task:b198ac5f-f688-4985-a5ef-fc326030f829 -->
- [x] 7.2 履约协同页 4 视图（待确认 PO / ASN 登记 / 发票提交 / 例外看板与补料）+ 路由 `/transactions/fulfillment` + 菜单；验证 `FulfillmentConsole.test.tsx` 挂载/空态/按钮按态展示 <!-- comet-task:3771a426-fa9b-423b-8a9e-91c13642ae76 -->
- [x] 7.3 失败与权限降级（错误码可读提示、只读降级、能力未启用提示）；验证 vitest 覆盖 31504 保留表单、只读隐藏写按钮、403 无权提示 <!-- comet-task:0a9a56ed-6c70-4b3f-97e6-cb2d7aec0ada -->
- [x] 7.4 前端 typecheck + eslint(json errorCount=0) + vitest 全绿；验证三条命令 exit=0 <!-- comet-task:b1134621-0d19-4fbb-bfc9-7d597810a07a -->

## 8. 集成验收与证据

- [x] 8.1 后端 `mvn -o -f bone-procurement/pom.xml clean test` 全绿（transaction + fulfillment 全部用例）；验证退出码 0、用例数与基线一致 <!-- comet-task:dab49fea-4d8b-4e52-a7b4-61bb298ec32d -->
- [x] 8.2 全仓门禁 G32/G40/G41 + procuraAI 对齐门禁未转红；验证相关门禁脚本 exit=0，A10.16 黑名单未命中 <!-- comet-task:14ce3733-592e-4a64-8bf0-725cf7070186 -->
- [x] 8.3 真实浏览器 CDP E2E：登录 → 采购菜单 → 履约协同页 → 确认/ASN/开票/例外交互（含降级）；验证截图与 evidence JSON 落 `outputs/`，无白屏 <!-- comet-task:027977d0-b29d-45e2-89b1-3c3a8a5962f7 -->

## Workflow follow-up

- Verify 阶段完成独立复审与最终集成审查后归档 change。
- 归档后按用户授权完成 commit / push（当前 worktree 分支 `comet/procurement-fulfillment-collaboration-slice`）。
