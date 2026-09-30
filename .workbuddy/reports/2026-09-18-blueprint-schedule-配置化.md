# blueprint 定时任务参数配置化 — 执行报告

> 日期：2026-09-18 22:12–22:22 ｜ 分支 `release/mvp-v1.0` ｜ HEAD `47b87aac`
> 范围：上一轮审查/优化中**唯一待拍板项**——三个扫描 Job 的 cron 与超时阈值硬编码，而同模块 `OrderOutboxRelayJob` 已走配置，形态不一致。

---

## 一、做了什么

三个扫描任务的**周期（cron）与门限（超时阈值 / 宽限期）**全部外置为配置，默认值保持不变：

| 任务 | 周期键（默认） | 门限键（默认） |
|---|---|---|
| `CancelExpiredOrderJob` | `bone.blueprint.schedule.cancel-expired-orders-cron`（`0 0/5 * * * ?`） | `order-timeout-minutes`（30） |
| `CloseExpiredPaymentJob` | `close-expired-payments-cron`（`0 0/5 * * * ?`） | `payment-timeout-minutes`（30） |
| `OrderPaymentInconsistencyJob` | `payment-inconsistency-check-cron`（`0 0/10 * * * ?`） | `confirm-grace-minutes`（10） |

**为什么值得做**：超时窗口是**运营策略**（各业务线 / 渠道的支付时限不同），而这三个任务的行为完全由门限决定（「扫过阈值的行」）。硬编码为 `static final` 意味着每次调整都要改代码、走一次发版。

---

## 二、改动清单（8 个文件）

| 文件 | 改动 |
|---|---|
| `adapter/schedule/CancelExpiredOrderJob.java` | 删 `ORDER_TIMEOUT_MINUTES` 常量 → `@Value` 注入字段；cron → 占位符；日志改用字段 |
| `adapter/schedule/CloseExpiredPaymentJob.java` | 同上（`PAYMENT_TIMEOUT_MINUTES`） |
| `adapter/schedule/OrderPaymentInconsistencyJob.java` | 删 `CONFIRM_GRACE_MINUTES` → `@Value`；cron → 占位符；`@Transactional` 未动 |
| `src/main/resources/application.yml` | 新增 `bone.blueprint.schedule.*` 段（3 cron + 3 门限，带注释） |
| `CancelExpiredOrderJobTest.java` | `@InjectMocks` → 显式构造 + `ReflectionTestUtils.setField`；阈值测试拆为「默认 30」+「覆盖 45 断言门限位移」 |
| `CloseExpiredPaymentJobTest.java` | 同上（构造 + setField） |
| `OrderPaymentInconsistencyJobTest.java` | 同上（构造 + setField） |
| `README.md` | 打洞表补登记 `OrderPaymentInconsistencyJob` + 新增配置键说明 |

顺带修正两处 javadoc 过期表述：「经应用层 **Handler** 执行」→「经应用服务执行」（蓝图层已无 Handler，ADR-0028）。

---

## 三、关键判断：为何用 `@Value` 而不是新建 `@ConfigurationProperties`

**第一版我写错了，自查后撤除**——过程比结论更值得记：

1. 初版仿 `OrderOutboxProperties` 建了 `infrastructure/config/BlueprintScheduleProperties` + `BlueprintScheduleConfiguration`。
2. **自查发现跨层**：分层规范是 `adapter → application → domain ← infrastructure`，即 **adapter 与 infrastructure 平级、adapter 不得依赖 infrastructure**。让 `adapter/schedule` 的 Job 注入 `infrastructure/config/*Properties` 正是跨层。
3. **实证**：`grep OrderOutboxProperties` —— 注入方**全部在 `infrastructure/` 内**（`OrderOutboxPortAdapter`、`OrderOutboxRelayPortAdapter`），**没有任何 adapter 类注入它**。
4. **adapter 层读配置的既有范式是就地 `@Value`**：`PaymentController`（`@RequiredArgsConstructor` + **非 final 字段** + `@Value`）、`OrderPaidIntegrationListener`、`OrderOutboxRelayJob`（`@Scheduled` 占位符）。
5. 结论：**adapter 层配置一律就地 `@Value`；`infrastructure/config/*Properties` 只服务 infrastructure 内部。** 已删掉那两个新类。

> 另注：项目对 `@ConfigurationProperties` 有一条明确偏好——`OrderOutboxProperties` 的 javadoc 写明「由占位符读取的键**不在本类声明字段**，避免与占位符形成重复真源 / 死字段」。故即便在 infrastructure 内，cron 这类 `@Scheduled` 直读的键也不该进 Properties。

---

## 四、验证（复刻主树全部在途改动进独立 worktree）

| 检查 | 结果 |
|---|---|
| `mvn -o -pl bone-blueprint test` | **184 tests, 0 failures, BUILD SUCCESS**（+1 = 新增配置断言；含 `ArchitectureTest` 27 条） |
| 三个 Job 测试单独运行 | **9/9 通过**（Cancel 4 / Close 2 / Inconsistency 3） |
| `spotless:check`（绕缓存） | **170 files clean, 0 needs changes, 0 skipped by caching** |
| `archunit_store` 漂移 | **无** |
| 三道 DDD 文档门禁 | drift / doc-code-sync / gate-state **全 OK** |
| 合规产物 | `--check` **up to date**（无需重算） |

主树**全程未跑模块测试**（避免污染 `archunit_store`）。格式回写前用 `diff -rq 主树 worktree` 确认 spotless 只改了我自己的 3 个文件（javadoc 段落 reflow），未波及并发方文件。

---

## 五、未做 / 待你定

1. **🟡 ADR-0029 会冲击这三个 Job（建议在其实施前对齐）**
   并发会话 22:11 新增 `doc/architecture/adr/0029-sdk-auto-tenant-filter.md`（状态：**提议，待架构师审批**）：SDK 将对租户表**自动注入 `tenant_id`**，并规定「租户表 + `TenantContext` 为空 + 未显式关闭 → **抛 `MissingTenantContextException`（不静默）**」；逃生舱条款**点名**「后台作业 / Outbox 中继 / 定时任务等 `TenantContext` 为空的上下文」须 `disableTenantFilter()` 或先 `setTenantId`。
   **这三个 Job 恰恰就是「全租户扫描 + 无 `TenantContext`」**（它们的读端口是 `*AllTenants`）。该 ADR 一旦实施，它们会被异常打挂、或必须显式声明逃生舱。
   **本轮未预置逃生舱**——ADR 尚未批准，不按未决方案改代码。实施时须一并处理。
2. **键名一致性目前无机器守护（已知缺口）**
   6 个键的 `@Value` 与 `application.yml` 已**人工逐条核对一致**。但 blueprint **无任何容器级测试**，所以「键名写错 / 读了不存在的键 → 静默回落到默认值」这类错误**当前不会被任何检查发现**。要机器守护需引入 `ApplicationContextRunner` 类测试（断言上下文可启动 + 属性绑定成功），属本模块尚无的测试形态——**要不要加，你定**。
3. **`*.yml` 仅在默认档写入**：`application.yml` 给了默认值，`dev` / `prod` / `mq` 三档未覆盖（无明确运营值可填）。生产若要独立窗口，按需在这三档加 `bone.blueprint.schedule.*` 覆盖即可。
