# Bone Metadata SDK 能力需求（源自 bone-blueprint 落地阻塞）

> **来源**：`bone-blueprint` 按 [Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md) 落地时的三条**不可在模块内自行解决**的阻塞项。
> **性质**：能力需求（非 ADR）。提交给 `bone-metadata-sdk` 团队评估排期。
> **状态**：需求一（`@Version`）已落地（ADR-0031）；需求二写侧 MVP 已落地（`@Cascade`）。**明确不做**：强类型 ID / PO 分离（原需求三）、级联读回填与批量级联。blueprint 侧登记见 `bone-blueprint/README.md` 与 [E-4.1](./Bone-DDD-最终实践方案.md#e-41-写侧) / [E-6.3](./Bone-DDD-最终实践方案.md#e-63-po-分离信号)。

---

## 背景

`bone-blueprint` 是 Bone 的 DDD 参考实现（订单 / 支付限界上下文，L3 档位：Outbox + 幂等 + 补偿）。在落地 E-5.3（并发策略）、CORE-11（聚合 1:1 落盘）、E-7.1（强类型 ID）时，持久化边界曾被 SDK 能力阻断。需求一、二写路径已收敛；其余项按下表**明确不做**，AI / 评审不得当作待办推进。

---

## 需求一：通用写路径启用原生 `@Version` 乐观锁

**阻塞项**：#4（订单 / 支付单的 DB 级并发护栏，E-5.3 / CORE-07）

**状态**：已落地（ADR-0031）。`DynamicUpdateBuilder` 追加 `WHERE version = :old` 与 `SET version = version + 1`；`BaseRepository.update` 在 0 行时抛 `OptimisticLockingFailureException`，成功后回写实体 version。blueprint `Order` / `Payment` 已标 `@Version`。

**验收标准**：两个并发事务更新同一条聚合，后提交者抛出冲突异常，且 `version` 递增——写路径已满足。

---

## 需求二：聚合级联落库（子实体随根持久化）

**阻塞项**：#3 / CORE-11 偏差（聚合根是唯一持久化入口）

**状态**：写侧 MVP 已落地（`@Cascade` + `AggregateCascadeWriter`）。`BaseRepository.insert` / `update` / `save` 在根落盘后回填外键、逐条 insert/update，并在 `update` 时清除孤儿行（软删优先）。blueprint `Order.items` 已标注 `@Cascade(foreignKey = "orderId")`，应用层不再调用子实体仓储；`OrderItemRepository` 已删除。

**明确不做（非目标，勿排期、勿 PR）**：

- `findById` / 根重载**不**回填 `@Cascade` 集合；读明细继续走本聚合投影（如 `OrderRepository.findOrderWithItems`）；
- `batchInsert` / `batchUpdate` **不**级联；
- 子实体若带 `@Version`，级联 update **不**回写内存 version。

**验收标准**：单条 `save(order)` 后 `t_order_item` 与聚合内存态一致（含新增 / 修改 / 移除的明细），且全程在一个事务内。写路径已接入。

**blueprint 侧**：不要为了级联再引入子实体仓储；不要实现读回填或批量级联。

---

## 需求三：`@Table` 映射与 `Repository<T, ID>` 支持强类型 ID 值对象

**阻塞项**：#6（E-7.1 重要跨聚合引用优先强类型 ID）

**状态**：**明确不做（非目标）**。不实现 SDK 值对象 ID / `TypeHandler`；不推动 blueprint（及其他 Shared 模块）为强类型 ID 做 PO 分离。

**现状口径（合规，不是待办）**：

- 全平台 SDK 仓储一律 `Repository<T, Long>`；跨聚合引用字段为裸 `Long` 列；
- E-7.1「遗留自增主键按模块记录」适用；blueprint 在 README 登记为存量合规；
- 新模块若仍走 Shared / 无 Converter，继续用 `Long`，**不要**为「理想强类型」强行拆 PO。

**曾请求、现已取消的能力**（仅作历史说明，勿再实现）：

1. `@Table` 实体 ID / 引用字段声明为值对象，经 `value()` / `TypeHandler` 与物理列互转；
2. `Repository<T, ID>` 的 `ID` 泛型安全使用值对象。

**blueprint 侧**：不引入 `OrderId` / `CustomerId` 等值对象替换裸 `Long`；不做 `OrderItemPO` + Converter 迁移。

---

## 优先级与结论

| 需求 | 结论 |
|------|------|
| 一、原生 `@Version` 乐观锁 | **已落地**（ADR-0031） |
| 二、聚合级联落库（写侧） | **已落地**（`@Cascade` MVP） |
| 二补充：读回填 / 批量级联 | **明确不做** |
| 三、强类型 ID + 连带 PO 分离 | **明确不做** |

---

## 附：blueprint 并发护栏（现状）

需求一落地后，写路径以 SDK `@Version` 为 DB 级护栏；以下仍作为补充：

1. **支付回调并发**：`channel_trade_no` **唯一索引**兜底（幂等去重键，防双写）；
2. **钱货不一致**：`OrderPaymentInconsistencyJob` 周期对账（仅告警、不改单），补偿"支付成功但订单确认丢失"窗口；
3. **状态机**：`Order` / `Payment` 状态迁移防非法跃迁。
