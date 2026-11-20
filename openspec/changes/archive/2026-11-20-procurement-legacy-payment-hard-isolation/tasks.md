# Tasks — 存量采购付款逃逸处理器硬隔离（D-06）

## 1. 条件装配与配置开关

- [x] 1.1 在 `bone-app-purchase` 新增 `PaymentIsolationProperties`（`@ConfigurationProperties(prefix="procura.purchase-pay-handler")`），字段 `enabled` 默认 `false`；以单元测试断言缺省为 false、显式 true 生效。 <!-- comet-task:ee3da192-c2d2-449f-a066-722895bdc966 -->
- [x] 1.2 新增专用配置类，用 `@ConditionalOnProperty(name="procura.purchase-pay-handler.enabled", havingValue="true", matchIfMissing=false)` 控制 `purchaseOrderPayHandler` 注册，并在组件扫描 REGEX 排除中排除该 bean，使其成为唯一注册入口；验证方式：缺省配置 ApplicationContextRunner 断言 `doesNotHaveBean`。 <!-- comet-task:86d7705f-7791-4720-83c3-69075627df38 -->
- [x] 1.3 新增始终注册的 `LegacyPaymentIsolationReporter`，启动期按开关输出禁用 / 启用日志（禁用态含"已硬隔离 / 生产以 PaymentRequested 为准"，启用态含"仅演示占位、禁止生产出金"）；验证方式：日志断言测试覆盖两态。 <!-- comet-task:95e57f72-e45c-42d1-aa01-cb58e99369c0 -->

## 2. 装配测试与业务逻辑不回归

- [x] 2.1 改造 `PurchaseApplicationAutoConfigurationWiringTest`：缺省 / `false` 断言 `doesNotHaveBean(PurchaseOrderPayHandler.class)`，显式 `true` 断言 `hasSingleBean`；验证方式：该测试类两态用例全绿。 <!-- comet-task:c61dade2-cdf3-48fd-b859-c37c1d35b9dc -->
- [x] 2.2 保留 `PurchaseOrderPayHandlerTest` 全部参数守卫用例不变，证明存量付款逻辑未被改写；验证方式：`mvn -o -f bone-apps/bone-app-purchase/pom.xml clean test` 全绿且该类用例数不减。 <!-- comet-task:387efe6d-b557-4af9-87b8-ac10f14a36b3 -->

## 3. 决策寄存器落地记录

- [x] 3.1 在 `doc/ai-product/procuraAI/decisions/DECISION-REGISTER.md` 把 D-06 状态从 `Accepted + 外部依赖` 更新为 `Landed`，验收判据列补本 change 与实现 `file:line` 证据，owner 列保持"未指派（用户授权本轮落地，未伪造签署）"；验证方式：frontmatter 守护与主门禁不转红。 <!-- comet-task:3bcf97b9-53f0-4681-a3c9-5d42e19b6a82 -->
- [x] 3.2 追加变更说明（注明日期 2026-11-20 与依据），不改 CHANGELOG 历史轮次快照、不写假 owner；验证方式：git diff 仅含 D-06 行与新增说明，无历史数字改动。 <!-- comet-task:7854e808-cc2f-4611-bdc8-139c43fe929c -->

## 4. 集成验证

- [x] 4.1 模块级：`mvn -o -f bone-apps/bone-app-purchase/pom.xml clean test` 全绿（JDK21 + 本地 Maven）。 <!-- comet-task:761612a9-9de0-4ac6-b727-64bd12e53b64 -->
- [x] 4.2 procuraAI 门禁不回归：`check_procura_alignment.py`、`--self-test`、`--manifest`、`generate_asyncapi.py --check`、`check_procura_evalset.py`(+`--self-test`)、DDL 三项、迁移清单两项均绿；验证方式：逐条命令退出码 0。 <!-- comet-task:c0159430-14a6-4a92-907e-4b2fd4ca1f75 -->
