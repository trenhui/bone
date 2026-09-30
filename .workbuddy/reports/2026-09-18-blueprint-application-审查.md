# bone-blueprint / application 包代码审查

日期：2026-09-18 ｜ 范围：`bone-blueprint/src/main/java/com/bone/blueprint/application/**`（44 个文件 / 2101 行）
对照规范：`doc/architecture/Bone-DDD-最终实践方案.md`（v5.5.9）E-3.7 / E-4.1~4.4 / E-5.x / E-10.1~10.3 / E-13.1

---

## 一、`query/dto` 与 `query/qry` 的职责区别（规范口径）

规范 E-13.1（L1576-1591）把两者定义为**读用例的两端**，而不是两类平级模型：

| 包 | 后缀 | 唯一语义 | 禁止 |
|---|---|---|---|
| `application/query/qry/` | `*Query` | 读用例**入参**（应用自有的输入载体） | 不得用 `*Qry` 类名（`Qry` 属 adapter）；不得出现在 adapter 方法签名 |
| `application/query/dto/`（E-4.2 L1022 亦允许 `projection`） | `*Dto` / `*Projection` | 读用例**出参**（读模型 / 投影 / 对外 DTO） | adapter 的 `*Resp` 不是它，不得用 resp 命名 |

配套两条硬边界：
- AS-02（L868）：`*Command` / `*Query` 是用例输入的**默认载体**；adapter 的 `*Req` / `*Qry` / `*Resp` 不得进入 application。
- E-13.1（L1591）：`application` 方法签名上出现 `Req/Qry/Resp` 即违规。

对应到本模块的现状（入参 / 出参对照）：

| | 类 | 实际角色 | 生产链路消费情况 |
|---|---|---|---|
| 入参 | `qry/OrderDetailQuery` | record(1 字段) | **0 引用（死代码）** |
| 入参 | `qry/PaymentDetailQuery` | record(1 字段) | **0 引用（死代码）** |
| 入参 | `qry/OrderPageQuery` | record(4 字段) | 仅 MapStruct 生成的 `toOrderPageQuery` 与 1 个单测；`OrderController.page()` 实际传 4 个散装标量 |
| 出参 | `dto/OrderDto`(+内嵌 `OrderItemDto`) | 对外 DTO | `getById` / `page` 返回 |
| 出参 | `dto/PaymentDto` | 对外 DTO | `getById` 返回 |
| 出参 | `dto/OrderHeadProjection` | 读侧**投影行** | 分页 / 超时扫描 |
| 出参 | `dto/OrderWithItemsProjection` | 读侧**投影行** | 详情 / 库存事件处理器 |
| 出参 | `dto/PaymentProjection` | 读侧**投影行** | 详情 / 超时扫描 / 对账 |

结论：**`qry` = 入参`*Query`，`dto` = 出参**。本模块的问题是 `qry` 三个类全部没有真实消费方，`dto` 又混装了「对外 DTO」与「读侧投影行」两类东西。

---

## 二、与规范的偏差（按严重度）

### P1-1 `findOrderWithItems.sql` 漏掉明细侧软删条件，且无排序

```sql
FROM t_order o
LEFT JOIN t_order_item oi ON o.id = oi.order_id
WHERE o.id = :orderId AND o.tenant_id = :tenantId AND o.deleted = 0
```

- `t_order_item.deleted` 确实存在（`bone-init.sql:591`），但 Join 条件里没有 `oi.deleted = 0`，而同一文件的 `t_order` 侧补了。`OrderQueryAdapter.findStatusById` 也手工补了 `deleted = 0`，说明本模块其它读侧 SQL 都记得这条，只有这一处漏。
- 后果不止"多显示一行"：`OrderCreatedEventHandler`（预留库存）与 `OrderPaidEventHandler`（确认扣减）**正是按这个投影逐行调用远程库存**，软删明细会被真实下单/扣减。
- 附带：无 `ORDER BY`，`OrderDetailAssembler.fromRows` 用 `rows.get(0)` 当订单头、items 顺序依赖 DB 返回顺序 → 接口明细顺序不稳定。
- 修法：`LEFT JOIN t_order_item oi ON o.id = oi.order_id AND oi.deleted = 0`，末尾加 `ORDER BY oi.id`。

### P1-2 `appendPaymentFailed` 的 Outbox 与业务写不在同一事务（契约违反 + 丢事件窗口）

`OrderOutboxWriter.appendPaymentFailed` 的契约写「**须与支付单置 FAILED 同事务**」，实现 `OrderOutboxWriterImpl` 五个方法全部 `@Transactional(propagation = MANDATORY)`。但唯一调用点在 `PaymentFailedEventHandler`：

| 方法 | 调用点 | 与业务写同事务？ | 契约一致？ |
|---|---|---|---|
| `appendPaymentSucceeded` | `PaymentApplicationService.processCallback`（事务内） | ✅ | ✅ |
| `appendOrderPaid` | `PaymentSucceededEventHandler`（REQUIRES_NEW，与订单确认同事务） | ✅ | ✅ |
| `appendPaymentInconsistent` | 同上 / `OrderPaymentInconsistencyJob` | ✅ | ✅ |
| `appendPaymentRefunded` | `PaymentRefundedEventHandler`（与**订单** REFUNDED 同事务） | ✅（对订单侧） | 语义含糊 |
| `appendPaymentFailed` | `PaymentFailedEventHandler`（AFTER_COMMIT + REQUIRES_NEW） | ❌ 支付单 FAILED 早已提交 | ❌ |

失败路径的崩溃窗口：支付单已 FAILED 提交 → 进程崩溃 → 失败集成事件永久丢失（支付单是终态，没有补偿扫描覆盖它）。成功路径反过来是在同事务写的，两条路径形态不对称。
修法：在 `processCallback` 的失败分支内直接 `orderOutboxWriter.appendPaymentFailed(...)`，`PaymentFailedEventHandler` 相应删除或降级为兜底；与成功路径保持同一形态。

### P1-3 `BlueprintIdempotencyService` 在 application 层返回 HTTP 类型

- 代码：`application/service/BlueprintIdempotencyService#replay` 返回 `Optional<ResponseEntity<ApiResponse<T>>>`，`remember(...)` 收 `ResponseEntity<ApiResponse<T>>`。
- 规范 E-10（L1396）：「（技术能力编排类）可被 Controller 直接注入；同样**不得返回 `ResponseEntity` 等 HTTP 类型**——HTTP 状态码与响应头由 Controller 决定」，而该条正是拿这个类当示例（L1400）。
- 三处自述互相矛盾：`bone-blueprint/README.md:257-260` 称「本模块是其分层更干净的版本」；`OrderController#create` javadoc 称「应用层不出现 HTTP 类型」；类自身 javadoc 只说「分层折中…属目标态改造」。
- 修法：`replay()` 返回协议无关快照（如 `record ReplayedResponse(int status, String location, Object body)`），`remember()` 收同型快照，Controller 负责渲染 `ResponseEntity`；快照 JSON 结构不必变，只换外壳类型。

### P2 一致性 / 文档漂移

| # | 问题 | 证据 | 修法 |
|---|---|---|---|
| 1 | 租户解析**四种**策略并存 | `currentTenantId()`（ship/deliver/initiate/processCallback/refund/getById/page）；`resolveTenantId` 显式优先（cancel）；内联三元（closeExpired）；`event.tenantId()!=null?:0L`（consume） | 端口上加 `long resolve(Long explicit)`，或命令基类统一兜底 |
| 2 | `port/out/package-info` 称「adapter 禁止直引此包下的端口（E-4.2 分层约束）」 | 与实现矛盾：`adapter/schedule/OrderPaymentInconsistencyJob` 直接 import `OrderOutboxWriter`；且 E-4.2 是"读侧"章节无此句，E-10.1 明确 adapter 可依赖 application | 改注释（推荐，adapter 用技术端口是合理的）；若要收紧须先立规则 |
| 3 | **3 个**集成事件 javadoc 写「**为何在 domain 包**」（原报告误记为 5 个：`PaymentFailed` / `PaymentRefunded` 两份没有该段） | 实际包为 `application/event/integration`；`OrderOutboxWriterImpl:184` 已 `instanceof IntegrationEnvelope` 消费 → 迁移完成、注释未跟上 | 改写为"为何在 application"并说明与 infrastructure 的依赖方向 |
| 4 | javadoc 指向不存在的构件 | `OrderController`「读侧直接依赖 QueryHandler」、`PaymentController`「仍走独立 CommandHandler」、`OrderCreatedEventHandler`「CreateOrderCommandHandler 显式逐条 save」、jobs 与 `OrderQueryPort`「经应用层 Handler 执行」——**⚠ 本条是对「并发会话在途工作树」的观察，不是对 HEAD 的观察**：HEAD 上这些 `*CommandHandler`/`*QueryHandler` 是**存在**的，注释当时正确；是另一会话的在途重构删了它们 | 现状本身合规（AS-01 已改口径为"默认入口非唯一入口"），把注释改成 ApplicationService |
| 5 | README 与代码漂移 | `README.md:143-144` 称两个 Job「已在任务内显式打印 `[打洞]` 日志」，实际日志前缀是 `[全租户扫描]` | 改 README 或改日志前缀，二选一 |
| 6 | `OrderDetailAssembler.fromRows` 抛 `BizException(404, ORDER_NOT_FOUND)` | 纯静态装配工具承担"资源不存在"判定；`PaymentApplicationService.getById` 是 `orElseThrow` → 两种形态 | 装配器只映射，判空抛错上移 ApplicationService |
| 7 | 两个 `package-info` 的清单过期 | `query/port/package-info` 未列 `findStatusById` / 两个全租户扫描方法，仍写「PaymentQueryPort（findById 单表查询，为未来 JOIN 扩展预留）」；`port/out/package-info` 漏 `IdempotencyStore`、`ConsumedEventPort` | 补齐清单 |
| 8 | `query/support/` 是规范外目录 | 规范只说「结果放 `application/query/dto` 或 `projection`」 | 可保留，但建议 `dto` 只放对外 DTO，投影迁 `projection` |
| 9 | `*Assembler` 两层同词 | `application/query/support/*Assembler` 与 `adapter/*/assembler/*Assembler`；规范正文 `Assembler` 出现 0 次 | E-13 默认 Advisory，仅在模块内统一即可 |

### 待裁定（不由审查单方面决定）

`OrderApplicationService` 承载 6 个用例（create/cancel/ship/deliver/getById/page），共 201 行。
- 支持现状：L1508 说默认骨架就是「Controller + ApplicationService + Repository」，E-3.7 允许门面；读侧 L1025 明确「读侧不与写侧对称，不硬套 Query/QueryHandler」。
- 不支持：L1618 写「`ApplicationService` = 一个业务用例 + 一个主要事务」，E-13.2 的示例命名是 `OrderShippingApplicationService`（一用例一类）。
- 建议的最小动作：把两个读方法拆到 `OrderQueryApplicationService`（读侧依赖与事务语义都不同），写侧保留门面，两边都不破现有 Controller。

---

## 三、重复代码清单

| # | 位置 | 重复量 | 建议 |
|---|---|---|---|
| 1 | `PaymentApplicationService.extractEvent(Payment, Class)` vs `PaymentSucceededEventHandler.extractDomainEvent(Order, Class)` | 同一段"从聚合已注册事件里取首个指定类型"，2 份 | 抽 `application/support/DomainEvents.extract(aggregate, type)`；根治见优化 D |
| 2 | `OrderCreatedEventHandler.handle` / `OrderPaidEventHandler.handle` | 逐行同构 ~35 行 ×2（查投影、`hasItem` 校验 + 同款 error 文案、for + try/catch），差异只有 `reserveStock`/`confirmStock` 与文案 | 抽 `InventorySyncSupport.forEachItem(rows, event, (productId, qty) -> ...)` |
| 3 | 「加载聚合或抛 404」 | `OrderApplicationService.cancel/ship/deliver` 三段**逐字相同**；`PaymentApplicationService.loadPayment` 已抽，但 `processCallback`/`refund`/`closeExpired` 又各自内联同一段 | `OrderApplicationService` 抽 `loadOrder(id, tenantId)`；Payment 侧全部改用已有 `loadPayment` |
| 4 | `catch (DomainException) → BizException(409, CODE + ": " + msg, ex)` | 5 处（initiate Tx2 / markFailed / confirmSuccess / refund / closeExpired） | 静态工厂 `statusConflict(code, ex)` |
| 5 | `new BizException(404, CODE + ": " + id)` | 约 8 处，HTTP 状态码硬编码散落 application / event / adapter | 错误码表携带默认状态码，异常只传码 + 动态值 |
| 6 | `OrderDto` 手写 Builder（96 行）vs 同包 `PaymentDto` `@Builder`（12 行） | 同包两种风格，约 85 行可省（D0/E-8 的 `@Data/@Setter` 禁令只约束 **domain**，application 不受限） | `@Getter @Builder` |
| 7 | 5 个集成事件各带 `SCHEMA_VERSION = "1.0"` + `fromDomain(...)` | 结构性样板 ×5；`fromDomain` 与 canonical 构造器等价 | 二选一保留（保留 `SCHEMA_VERSION`，`fromDomain` 可删） |
| 8 | `Projection → OrderDto → OrderSummaryResp/OrderDetailResp` | 两层同构映射，字段 1:1（含 `items` 内嵌结构） | 要么让 adapter 直用投影出 `*Resp`（需把 `getById/page` 返回类型改为投影），要么保留现状并接受一次额外拷贝——现状不算违规 |
| 9 | 3 个定时任务的 `for + try/catch + 计数日志` | `CancelExpiredOrderJob` / `CloseExpiredPaymentJob` / `OrderOutboxRelayJob` | 抽 `TenantScanRunner`（含逐笔隔离异常的语义） |

---

## 四、死代码（可直接删）

| 类 / 方法 | 引用情况 |
|---|---|
| `application/query/qry/OrderDetailQuery` | 0 引用 |
| `application/query/qry/PaymentDetailQuery` | 0 引用（仅被 `OrderDetailQuery` 的 javadoc 提及） |
| `application/query/qry/OrderPageQuery` | 仅 MapStruct 生成的 `toOrderPageQuery` + `OrderAssemblerTest`；`OrderController.page()` 传散装参数 → **生产链路无消费** |
| `OrderSummaryAssembler.fromOrder(Order)` | 0 引用 |

取舍建议（二选一，别停在中间态）：
- **A（推荐，最小成本）**：删掉整个 `qry` 包——规范 L1025 明确读侧不对称，`page(customerId, status, pageNum, pageSize)` 这种直查参数就是标量，不需要输入对象。
- **B**：保留 `qry` 并把 `ApplicationService.page/getById` 的签名改成吃 `OrderPageQuery` / `OrderDetailQuery`，同时删掉 `OrderAssembler.toOrderPageQuery`（adapter 不该替应用层造输入对象）。
现状属于 A 没做完、B 没开始：包在、类在、签名没用。

---

## 五、优化建议（按收益）

- **A. 补 SQL 正确性**（P1-1）：`AND oi.deleted = 0` + `ORDER BY oi.id`。收益：修掉一个真实库存缺陷。
- **B. 失败路径 Outbox 同事务**（P1-2）：与成功路径对齐形态。收益：消除资金事件的丢事件窗口。
- **C. 幂等服务去 HTTP 化**（P1-3）：快照模型与渲染分离。收益：让 README/注释与代码一致，且符合规范明文。
- **D. 向 SDK 提需求：`DomainEventPublisher.publishFrom(aggregate)` 返回已发布事件列表**。收益：一步消除"先 `extract` 再 `publishFrom`"的两步反模式（当前 3 处使用、未来每个走 Outbox 的模块都要写一遍），并能顺带删掉重复项 #1。
- **E. `OrderWithItemsProjection` 降级为明细专用投影**：事件处理器只需 `(productId, quantity)`，却依赖整行 JOIN 与 head 列。新增 `List<OrderItemProjection> findItemsByOrderId(tenantId, orderId)`（可排序、无 head），详情查询继续用联表投影。收益：事件处理器不再受 head 列/排序问题牵连。
- **F. `OrderDto` 换 `@Getter @Builder`**：省 ~85 行手写样板。
- **G. 投影迁 `application/query/projection/`**：让 `dto/` 只表示"对外 DTO"，`projection/` 只表示"读侧行"，与规范 L1022 措辞对齐。

---

## 六、符合规范、无需改动的部分（同样是结论）

- **依赖方向正确**：`application/**` 只引用 `domain` + `application/port/out` + `application/query/port`，没有任何 `infrastructure` import，也没有 adapter 的 `Req/Qry/Resp` 进入方法签名（E-10.1 / E-13.1 硬边界守住）。
- **读侧端口落位正确**：`application/query/port/*QueryPort` + `infrastructure/query/*QueryAdapter`，与 E-4.2 的目标形态一致（本模块被规范点为"读侧已完成迁移"的范例）。
- **技术端口 vs 业务网关的划分判据用对了**：`OrderOutboxWriter` / `OrderMessageSender` / `OrderOutboxRelayPort` / `TenantProvider` / `PaymentSignaturePort` / `IdempotencyStore` / `ConsumedEventPort` 在 `application/port/out`；`PaymentGateway` / `InventoryGateway` 在 `domain/gateway`，且每个类的 javadoc 都写出了 E-4.3 / E-10.2 的判据而不只是引编号。
- **事务形态是规范点名的正确形态**：成功回调的 Outbox 与业务写同事务；跨聚合两段式用 AFTER_COMMIT + REQUIRES_NEW；`OrderPaidConsumptionApplicationService` 让"幂等抢占与业务动作同事务"并解释了为什么不能拆（否则重试变丢事件）。
- **消费型应用服务的类型与位置正确**：`OrderPaidConsumptionApplicationService` 与 `application/event/*EventHandler` 的职责边界（入站集成事件的幂等 + 业务动作 vs 领域事件的下游动作）与 E-13.2 的描述一致。
- **`*Projection` 用 `@Getter` + 全参构造（不用 `@Data/@Setter`）**：与 D0/E-8 的领域约束风格一致，读侧行不可变，做法正确。

---

## 附：本次审查用到的验证手段

- 全包源码通读（44 文件）；引用关系用 ripgrep 逐符号核对（死代码结论均以"0 引用/仅测试引用"为据）。
- 规范口径取自 `Bone-DDD-最终实践方案.md` E-13.1（L1576-1591）、E-4.2（L1003-1025）、E-10 技术编排类（L1388-1400）、E-10.1/10.2（L1419-1456）、AS-02（L868）。
- 软删列、表名以 `bone-init.sql` 为准；Outbox 事务语义以 `OrderOutboxWriterImpl` 的注解与端口 javadoc 双向比对为准。
