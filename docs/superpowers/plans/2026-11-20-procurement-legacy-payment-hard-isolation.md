---
change: procurement-legacy-payment-hard-isolation
design-doc: openspec/changes/procurement-legacy-payment-hard-isolation/design.md
base-ref: 2f70dbfb595aa5dc4aa6ad76df6e8c57dc9f4eb1
archived-with: 2026-11-20-procurement-legacy-payment-hard-isolation
---

<!-- comet-task-authority: openspec/changes/procurement-legacy-payment-hard-isolation/tasks.md -->

# 实施计划 — 存量采购付款逃逸处理器硬隔离（D-06）

## 范围与约束

把 `bone-app-purchase` 的 `purchaseOrderPayHandler` 改为"缺省即禁用"的装配层硬隔离，不改存量付款业务逻辑。开关 `procura.purchase-pay-handler.enabled` 默认 `false`；显式 `true` 才注册且仅限演示占位。不碰 CI 三入口判据、DDL、其余 owner backlog。

设计决策与取舍见 `design.md`；可观察契约见 `specs/procurement/legacy-payment-hard-isolation/spec.md`。

## 执行策略

autonomous + tdd + standard review。TDD 顺序：先写/改装配双态测试使其 RED（缺省态当前会 hasSingleBean，与期望 doesNotHaveBean 冲突），再加开关与排除配置转 GREEN；保留既有参数守卫测试作为"业务逻辑未改写"的回归证据。

## 任务映射与步骤

### 组 1：条件装配与配置开关

- <!-- comet-task-ref:ee3da192-c2d2-449f-a066-722895bdc966 --> **1.1 配置属性类**：新增 `PaymentIsolationProperties`（`@ConfigurationProperties(prefix="procura.purchase-pay-handler")`，字段 `enabled` 默认 false）。验收：`PaymentIsolationPropertiesTest` 断言缺省 false、显式 true 生效。
- <!-- comet-task-ref:86d7705f-7791-4720-83c3-69075627df38 --> **1.2 条件装配 + 扫描排除**：新增专用配置类，`@ConditionalOnProperty(name="procura.purchase-pay-handler.enabled", havingValue="true", matchIfMissing=false)` 控制 `purchaseOrderPayHandler` 注册；组件扫描 REGEX 排除该 bean，使其成为唯一注册入口。验收：缺省 ApplicationContextRunner `doesNotHaveBean`。
- <!-- comet-task-ref:95e57f72-e45c-42d1-aa01-cb58e99369c0 --> **1.3 启动期日志守卫**：新增始终注册的 `LegacyPaymentIsolationReporter`，按开关输出禁用/启用日志（禁用含"已硬隔离 / PaymentRequested"，启用含"仅演示、禁止生产出金"）。验收：两态日志断言测试。

### 组 2：装配测试与业务逻辑不回归

- <!-- comet-task-ref:c61dade2-cdf3-48fd-b859-c37c1d35b9dc --> **2.1 双态装配测试**：改造 `PurchaseApplicationAutoConfigurationWiringTest`，缺省/关闭 `doesNotHaveBean`，显式 true `hasSingleBean`。验收：两态用例全绿。
- <!-- comet-task-ref:387efe6d-b557-4af9-87b8-ac10f14a36b3 --> **2.2 业务逻辑不回归**：`PurchaseOrderPayHandlerTest` 全部参数守卫用例保持不变。验收：`mvn -o -f bone-apps/bone-app-purchase/pom.xml clean test` 全绿且用例数不减。

### 组 3：决策寄存器落地记录

- <!-- comet-task-ref:3bcf97b9-53f0-4681-a3c9-5d42e19b6a82 --> **3.1 D-06 状态更新**：DECISION-REGISTER 把 D-06 改为 `Landed`，验收判据补本 change 与实现 `file:line`，owner 列保持"未指派（用户授权本轮落地，未伪造签署）"。验收：frontmatter 守护与主门禁不转红。
- <!-- comet-task-ref:7854e808-cc2f-4611-bdc8-139c43fe929c --> **3.2 变更说明**：注明日期 2026-11-20 与依据，不改 CHANGELOG 历史快照、不写假 owner。验收：git diff 仅含 D-06 行与新增说明。

### 组 4：集成验证

- <!-- comet-task-ref:761612a9-9de0-4ac6-b727-64bd12e53b64 --> **4.1 模块构建**：`mvn -o -f bone-apps/bone-app-purchase/pom.xml clean test`（JDK21 + 本地 Maven）全绿。
- <!-- comet-task-ref:c0159430-14a6-4a92-907e-4b2fd4ca1f75 --> **4.2 门禁不回归**：align(含 self-test/manifest)、asyncapi check、evalset(含 self-test)、DDL 三项、迁移两项逐条退出码 0。

## 失败处理与回退

- REGEX 排除精度导致重复/漏注册：以双态测试锁定 0 或 1，收窄 pattern 后重跑。
- 环境需要恢复付款动作：设置 `procura.purchase-pay-handler.enabled=true`，无需回滚代码。
- 门禁转红：先定位是否本次改动引起；L3 相关判据集合一律不动，必要时回到 Verify 修复。
