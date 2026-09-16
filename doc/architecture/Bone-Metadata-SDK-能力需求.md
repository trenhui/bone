# Bone Metadata SDK 能力需求（源自 bone-blueprint 落地阻塞）

> **来源**：`bone-blueprint` 按 [Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md) v5.5.3 落地时的三条**不可在模块内自行解决**的阻塞项。
> **性质**：能力需求（非 ADR）。提交给 `bone-metadata-sdk` 团队评估排期。
> **状态**：已登记，待 SDK 排期。blueprint 侧对应技术债已在 `bone-blueprint/README.md` 明示。

---

## 背景

`bone-blueprint` 是 Bone 的 DDD 参考实现（订单 / 支付限界上下文，L3 档位：Outbox + 幂等 + 补偿）。在落地 E-5.3（并发策略）、CORE-11（聚合 1:1 落盘）、E-7.1（强类型 ID）三条规范时，均在**持久化边界**被 SDK 当前能力阻断。以下三项均已在 blueprint 侧以「技术债登记 + 替代护栏」的方式诚实记录，但**真实修复点在 SDK**。

---

## 需求一：通用写路径启用原生 `@Version` 乐观锁

**阻塞项**：#4（订单 / 支付单的 DB 级并发护栏，E-5.3 / CORE-07）

**现状（已核验源码）**：

- `com.bone.metadata.sdk.domain.annotation.Version` 存在，`TableMetadataResolver` 能识别该注解并缓存 versionColumn；
- 但**写路径不消费它**：`BaseRepository#update` → `DynamicUpdateBuilder` / `ConditionalUpdateBuilder` 生成的 SQL 仅为 `SET … WHERE pk = ?`，**不追加** `WHERE version = ?`，也不做 version 自增；
- 因此聚合上声明 `version` 列**不产生任何并发保护**——属"看起来有、实际没有"的静默风险。

**为何 blueprint 无法自行绕过**：

- 唯一能构造带版本条件更新的入口是 `Repository#updateByCriteria(T, Criteria)`；
- 而 `Criteria` 标注 `@ReadSideOnly`，依 P0-5（`domainMustNotUseQueryBuilder`）与 E-9.3（`readSideDslOnlyInQueryLayer`），**`domain` 与 `application` 层禁止依赖**；
- 故 blueprint 无法在合法分层内实现乐观锁，只能落到 `infrastructure`——但那已越过应用层事务边界，不是正解。

**请求能力**：

1. 在通用写路径（`update` / `save` 的更新分支）消费 `versionColumn`：
   - SQL 追加 `WHERE <pk> = ? AND <version> = ?`；
   - 命中 0 行时抛出明确的**乐观锁冲突异常**（或返回 0 让调用方判定），而非静默成功；
   - 成功时 `version = version + 1` 并回填实体。
2. 提供可关闭开关（默认开启），便于存量模块灰度。

**验收标准**：两个并发事务更新同一条聚合，后提交者抛出冲突异常或被明确拒绝，且 `version` 递增。

**blueprint 侧后续动作**：能力就绪后，仅需给 `Order.version` / `Payment.version` 标注 `@Version` 即可生效，无需改业务逻辑。

---

## 需求二：聚合级联落库（子实体随根持久化）

**阻塞项**：#3 / CORE-11 偏差（聚合根是唯一持久化入口）

**现状**：

- SDK **不支持聚合级联**：`OrderRepository.save(order)` 不会持久化 `order.items`；
- blueprint 被迫将 `Order.items` 标 `@Transient`（避免 SDK 误映射为 `t_order` 列），并在应用处理器中**显式逐条 `save(OrderItem)`**；
- 为此引入了 `OrderItemRepository`——给**子实体**建仓储，与 CORE-11「聚合根是唯一持久化入口，子实体随根落盘，**不为子实体建立独立聚合级 Repository**」字面冲突；
- 该偏差已在 `bone-blueprint/README.md` 的 E-6.3 节显式登记（含被迫原因与一致性边界未削弱的论证）。

**请求能力**（任选其一，推荐 1）：

1. **级联落库**：`save(aggregateRoot)` 时自动持久化其标注为子实体的集合（含 INSERT / UPDATE / DELETE 差异比对）；
2. 或提供**显式的子实体写入 SPI**，使子实体持久化不必通过"独立 Repository"这种会违反 CORE-11 的形态表达。

**验收标准**：`save(order)` 后 `t_order_item` 与聚合内存态一致（含新增 / 修改 / 移除的明细），且全程在一个事务内。

**blueprint 侧后续动作**：删除 `OrderItemRepository`，明细随根落盘，恢复 CORE-11 完整合规。

---

## 需求三：`@Table` 映射与 `Repository<T, ID>` 支持强类型 ID 值对象

**阻塞项**：#6（E-7.1 重要跨聚合引用优先强类型 ID）

**现状（已核验全仓）**：

- **全平台所有 SDK 仓储一律 `Repository<T, Long>`**——`bone-iam`、`bone-system`、`bone-masterdata`、`bone-notification`、`studio-generator` 及 SDK 自身测试，**无任何非 `Long` 先例**；
- 跨聚合引用字段（`Order.customerId`、`OrderItem.productId`、`Payment.orderId` / `customerId`）均为 `@Table` 映射的 `Long` 列，由 SDK 反射取值后**直接绑定 JDBC 参数**；
- 因此把这些字段改为 `OrderId` / `CustomerId` / `ProductId` 等值对象，会直接破坏持久化（值对象无法写入 `BIGINT` 列）。

**影响**：方法签名中 `id` / `tenantId` / `customerId` / `orderId` 全为裸 `Long`，**编译期无法拦截"订单 ID 与客户 ID 写反"**这类静默数据错乱——这正是 E-7.1 要防的问题。`Payment.create(id, tenantId, orderId, customerId, …)` 有四个相邻 `Long` 参数，风险最高。

**当前合规口径**：E-7.1 明文允许"遗留自增主键按模块记录"，blueprint 已按此登记为存量合规，并在 README 规定**新增强聚合必须使用强类型 ID**；故本项不是违规，而是"想做但 SDK 不支持"。

**请求能力**：

1. 允许 `@Table` 实体的 ID / 引用字段声明为值对象类型，SDK 通过约定（如 `value()` 访问器）或可插拔 `TypeHandler` 与物理列互转；
2. `Repository<T, ID>` 的 `ID` 泛型可安全使用值对象（`findById(OrderId)` / `save` 返回 `OrderId`）。

**验收标准**：`private CustomerId customerId;` 能正确读写 `customer_id BIGINT` 列，且 `Repository<Order, OrderId>` 可用。

**blueprint 侧后续动作**：引入 `OrderId` / `CustomerId` / `ProductId` / `PaymentId`（`record` + 构造期空值校验），替换跨聚合引用的裸 `Long`。建议与需求二（PO 分离 / Converter）一并落地，避免二次返工。

---

## 优先级建议

| 需求 | 优先级 | 理由 |
|------|--------|------|
| 一、原生 `@Version` 乐观锁 | **P0** | 关乎资金正确性；当前**无任何** DB 级并发护栏，仅靠唯一索引与对账 Job 兜底，高并发写同一聚合存在丢失更新风险 |
| 二、聚合级联落库 | **P1** | 解除 CORE-11 被迫偏差，让参考实现可作为其他模块的范式 |
| 三、强类型 ID 支持 | **P2** | 当前按 E-7.1 存量记录条款合规，属"体验与防错"改进，非正确性缺陷 |

---

## 附：blueprint 当前替代护栏（需求一就绪前的真实保护）

在 SDK 乐观锁生效前，`bone-blueprint` 的实际并发保护为：

1. **支付回调并发**：`channel_trade_no` **唯一索引**兜底（幂等去重键，防双写）；
2. **钱货不一致**：`OrderPaymentInconsistencyJob` 周期对账（仅告警、不改单），补偿"支付成功但订单确认丢失"窗口；
3. **状态机**：`Order` / `Payment` 状态迁移防非法跃迁。

> 高并发写同一聚合的**丢失更新**风险在 SDK 乐观锁就绪前仍为已知登记项，非"已修复"。
