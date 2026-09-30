# bone-blueprint application —— 剩余三项优化执行报告

> 时间：2026-09-18 16:24–16:40 ｜ 分支 `release/mvp-v1.0` @ `47b87aac`（工作树含并发会话在途改动）
> 上游：`.workbuddy/reports/2026-09-18-blueprint-application-审查.md`（问题清单）、`.workbuddy/reports/2026-09-18-blueprint-application-优化执行.md`（P1 执行）
> 本报告覆盖上一轮明确留白的 3 项：#4/#5 错误码、#7 集成事件样板、#9 定时任务样板。

## 一、结论速览

实地读代码后，三项的成立情况**并不一致**——两项成立且已落地，一项经评估**不成立**（抽象成本高于收益），但同一段代码暴露了另一个实质缺陷，已一并修复。

| 项 | 审计原判 | 实地评估 | 处理 |
|---|---|---|---|
| **#4/#5** 错误码表携带默认状态 | 机制改造 | **成立，且是真实缺陷** | 已落地（新增门面 + 全量收口 + 补测试） |
| **#7** 集成事件 `SCHEMA_VERSION` + `fromDomain` 样板 | 重复代码 | **部分成立**：常量重复成立；`fromDomain` 是语言限制的必然产物，不可消 | 常量已收敛（5 份 → 1 份）；`PortAdapter` 的抽象**经评估放弃**，理由见 §3.2 |
| **#9** 定时任务 for+try/catch 样板 | 重复代码 | **不成立**：7 行样板，抽象后净增行数且可读性下降 | 未按原判抽象；改为修复同段代码的**可观测性缺陷**（见 §4） |

---

## 二、#4/#5：错误码与 HTTP 状态配对收口

### 2.1 根因（比"重复代码"更严重）

`BizException(int code, String message)` 的首参被 `GlobalExceptionHandler#toHttpStatus` 当作 **HTTP 状态**，
而**业务码只能拼进 message**（`"BP_ORDER_NOT_FOUND: 123"`）。于是「码」与「状态」天然是两份数据：

- `BlueprintErrorCodes.ORDER_NOT_FOUND` 是纯字符串常量，状态 `(404)` 只写在 javadoc；
- 抛出点写 `new BizException(404, ORDER_NOT_FOUND + ": " + id)`，状态再写一遍；
- 登记文档 §6 的 `BP_` 表是**第三份**。

三份数据靠人工保持一致，**没有任何机制发现不一致**。而 `Bone-错误码登记.md` §17 明文写着「**禁止**把 HTTP 码与业务码混为同一个整数」——
现状正是它反对的形态。

附带发现的两处真实误报（同一根因）：
- `OrderApplicationService.create()` 库存不足用 `BizException.of("商品库存不足: …")` ⇒ 默认码 **500**，把业务校验失败报成服务端故障（污染 5xx 告警与 SLO）。
- `PaymentApplicationService.processCallback()` 支付单不存在用 `NotFoundException("支付单不存在: …")`，**message 里没有业务码**，前端无法按码聚合；而同模块另 4 处同一语义用 `BP_PAYMENT_NOT_FOUND`——同一错误两种表达。

### 2.2 落地内容

**新增 `bone-blueprint/common/BlueprintErrors.java`**（唯一配对真源 + 抛出工厂）：

| 组成 | 作用 |
|---|---|
| `Map<String,Integer> DEFAULT_HTTP_STATUS` | 「码 → HTTP 状态」唯一真源，与登记文档 §6 `BP_` 表逐行对应 |
| `static { checkEveryCodeRegistered(); }` | 反射校验 `BlueprintErrorCodes` 的每个 String 常量都已登记；漏登记则**类加载即抛**（fail fast，而不是让异常被兜底成 400） |
| `of(code)` / `of(code, detail)` / `of(code, detail, cause)` | 抛出工厂，message 统一组为 `码: 上下文` |
| `supplier(code, detail)` | 供 `Optional.orElseThrow` 使用；延迟构造，正常分支不付异常构造开销 |
| `httpStatusOf(code)` | 未登记码直接抛 `IllegalStateException`，**不做兜底**——兜底会让"配错"变成"静默 400" |

**全量收口 28 处抛出点**（含 5 处测试自造异常），调用点不再出现状态数字：

```java
// 前：状态与码各写一遍
.orElseThrow(() -> new BizException(404, BlueprintErrorCodes.ORDER_NOT_FOUND + ": " + id));
// 后：只表达业务语义
.orElseThrow(BlueprintErrors.supplier(BlueprintErrorCodes.ORDER_NOT_FOUND, id));
```

**新增错误码 `BP_ORDER_STOCK_INSUFFICIENT`（409）** 替换库存不足的默认 500；`NotFoundException` 那处统一为 `BP_PAYMENT_NOT_FOUND`。

**配套改动**：
- `BlueprintErrorCodes` 的常量 javadoc **删掉状态数字**（`（404）` 等），只留语义——状态真源唯一。
- `OrderApplicationService` / `PaymentApplicationService` / 3 个 handler / `OrderDetailAssembler` / `PaymentController` / `BlueprintIdempotencyService` 的 `BizException` import 全部清理。
- 新增 `BlueprintErrorsTest`（6 条）：完整性（反射遍历所有码常量）、关键映射抽查、message 形态、cause 透传、supplier 延迟性、未登记码 fail fast。
- 文档同步：`Bone-错误码登记.md` §6 补 `BP_ORDER_STOCK_INSUFFICIENT` 行 + 改写「样板落地范围」段（抛出方走门面，状态真源在表）；`Bone-DDD-最终实践方案.md` 两处示例（§E-3.7 的 `cancel`、§E-5.3.1 的 `ship`）由 `new BizException(409, …)` 改为门面调用。

---

## 三、#7：集成事件版本常量收敛

### 3.1 已落地

`"1.0"` 在仓库里有 **6 份**：5 个集成事件各一份 `SCHEMA_VERSION` + `OrderOutboxEnvelopeFactory` 的 `ENVELOPE_SCHEMA_VERSION`。

判断：**信封版本与载荷版本不是同一个槽位**（前者描述投递封装格式，后者描述事件载荷结构，独立演进），故**不合并**。
但 5 个事件各自的 `SCHEMA_VERSION` 值相同、且只被自己的 `fromDomain` 使用——**这是真重复**。

做法：`IntegrationEnvelope` 增加 `String CURRENT_SCHEMA_VERSION = "1.0"`，5 个 record 删掉自己的常量、改引接口常量（并在接口注释里写明两者语义区别）。
收益：改契约版本从「改 5 处、漏改无提示」变为「改 1 处」。

### 3.2 经评估放弃的部分（诚实记录）

**`fromDomain` 工厂本身无法消除**——它是 Java record 无默认参数值的必然产物，唯一作用是补 `schemaVersion`。
审计里把它记为"样板"，但实测：**改掉它需要把入参改为领域事件对象**，那会让跨边界契约在编译期依赖 `domain` 包，
下游模块只想依赖契约时被迫传递依赖本上下文的领域模型——**契约的边界价值随之消失**。代价高于收益，放弃。

**`OrderOutboxPortAdapter` 的 `event == null ? null : …` 5 处三元**同样放弃抽象：实测抽出泛型辅助后，
每个方法仅省 1 行（14 → 13），却引入一层 lambda 间接、可读性下降。**抽象成本 > 收益**。

改为补一段注释说明这个权衡（放在 `OrderPaidIntegrationEvent#fromDomain`，其余 4 个事件引用它）：
即这些同类型参数（多个 `Long`）顺序错位编译器不报错，**调用点必须逐个核对实参顺序**——这是一个真实的残留风险，已显式记录。

---

## 四、#9：定时任务——原判不成立，改为修复可观测性缺陷

### 4.1 原判为何不成立

`CancelExpiredOrderJob` / `CloseExpiredPaymentJob` 的 `for + try/catch` 各 7 行。抽出批处理辅助后：
辅助类 25 行 + 每处调用 5 行（含一个 `row -> "orderId=" + …` 的格式化 lambda）⇒ **净增行数、可读性下降**。放弃抽象。

### 4.2 同段代码的真实缺陷（已修）

两个 Job 的 `try/catch` **吞掉异常后只记 error 日志，日志只打「命中=N 笔」**。
后果：**「命中 10 笔全部失败」与「全部成功」在日志上完全同形**——一次远端故障、一次事务普遍回滚，
看起来都是「扫描完成，命中 10 笔」。而这两个 Job 的 `catch` 恰恰是**预期会命中**的路径（状态已迁移属正常跳过，
说不上是故障），因此日志无法区分「正常跳过」与「真故障」。

修法（两个 Job 同形态）：

```java
int cancelled = 0;
int failed = 0;
for (OrderHeadProjection row : expired) {
  try {
    orderApplicationService.cancel(new CancelOrderCommand(row.getOrderId(), row.getTenantId()));
    cancelled++;
  } catch (Exception e) {
    failed++;
    log.error("取消超时订单失败: orderId={}, tenantId={}", row.getOrderId(), row.getTenantId(), e);
  }
}
// 必须区分「命中」与「实际完成」：只打命中数时，「命中 10 笔全部失败」与「全部成功」在日志上完全同形，
// 一次远端故障或事务回滚会看起来像「扫描正常完成」。
log.info(
    "[全租户扫描] 超时订单取消完成: 阈值={}min, 命中={} 笔, 成功={}, 失败={}（E-2 平台运维入口，README 已登记）",
    ORDER_TIMEOUT_MINUTES, expired.size(), cancelled, failed);
```

顺带把 `CancelExpiredOrderJobTest` 的类注释从「日志仍显示『扫描完成』」精确为「命中 0 笔，成功 0，失败 0」
（租户传错时查询本就查不到行，这条注释描述的隐蔽性依然成立，只是文案已变）。

**未做**（评估后判定为另一个议题，非"样板"问题）：三个 Job 的 cron 表达式与超时阈值**硬编码**，
而 `OrderOutboxRelayJob` 已走配置（`${bone.blueprint.outbox.relay-delay-ms:5000}`）。
统一配置化会让运维可调，但属**设计变更**（新增配置项 + 改构造签名 + 测试适配），需你确认是否纳入。

---

## 五、验证证据

**方法**：把「主树全部在途改动（含并发会话的 156+ 项）+ 我的改动」整体复刻进**独立 worktree**（`/tmp/bone-opt3` @ `47b87aac`）后验证，
**主树全程未跑过模块测试**（避免 `ArchUnit FreezingArchRule` 改写已入库的 `archunit_store/`）。

| 验证项 | 结果 |
|---|---|
| `mvn -o -pl bone-blueprint test` | **182 tests, 0 failures**（上一轮 176 + 我新增 `BlueprintErrorsTest` 6 条）；`ArchitectureTest` **26 条全过** |
| `spotless:apply` 波及范围 | 9 个文件，**全部是我的改动**（无并发方文件被格式化） |
| `spotless:check`（删 `spotless-index` 绕缓存真查） | **167 files, 0 needs changes** |
| `archunit_store/` 基线漂移 | **无** |
| `check-ddd-doc-code-sync.py --strict` | **OK**（我改的 2 处示例未引入未知 API） |
| `check-ddd-gate-state.py` | **OK** |
| `blueprint-compliance-collector` | 已重算，`--check` = up to date |
| `check-ddd-doc-drift.py` | **红：2 处**，但**非本批引入**——见 §6 |

---

## 六、剩余与阻塞项

### 6.1 `check-ddd-doc-drift.py` 的红是并发方在途改动引入的（非本批）

取证：在 worktree 里把 `doc/architecture/Bone-DDD-最终实践方案.md` 恢复成 **HEAD 版本**后重跑，结果是
`OK: 1 个 DDD 文档通过防漂移检查`——**HEAD 上根本没红**。当前红的 2 处是 `L1169/1177` 的
`orderRepository.saveWithVersionCheck(order)`，来自**并发会话尚未提交的文档改动**（同一文件另有 121 行在途差异）。

`saveWithVersionCheck` 是 **blueprint 的本地仓储方法**，不在 `API_SOURCES`（只收 `bone-core` / `bone-sdk` / `bone-metadata-sdk`）。
两条修法都**不在我的权限内**：
1. 加入 `scripts/check-ddd-doc-drift.py` 的 `API_SOURCES` 白名单 —— `scripts/` 改动按 AGENTS.md §12 属 **L3，需架构师审批**；
2. 文档侧标注为非平台 API 示意代码 —— 那是并发方正在编辑的文件。

⇒ **它会挡住任何提交，需要你裁决后由并发方或你处理。**

### 6.2 本批改动仍无法独立提交

依赖并发会话未提交的 `OrderItemInventoryExecutor`、`query/projection/`、`port/out/*Port`、`application/command/*`（`cmd/` 上提）——
单独 `git commit --only` 会产出**编译不过**的提交，必须与那次重构同批落地。**未提交、未清熔断器。**

### 6.3 未做项（含理由）

| 项 | 理由 |
|---|---|
| 三个 Job 的 cron / 超时阈值配置化 | 设计变更（新增配置项 + 改构造签名 + 测试适配），非"样板"议题，需你确认 |
| 集成事件 `fromDomain` 签名改为接收领域事件 | 会让跨边界契约编译期依赖 domain 包，破坏契约边界价值（§3.2） |
| `OrderOutboxPortAdapter` 5 处三元的泛型抽象 | 抽象成本 > 收益（§3.2） |
| `OrderOutboxEnvelopeFactory` 的信封版本与载荷版本合并 | 语义不同（投递封装 vs 载荷结构），合并会埋坑 |

### 6.4 附带修正的旧结论

上一轮审查报告称 `BlueprintErrorCodes` 处"抛出方必须用载码构造器"（登记文档原文）——该表述现已**不再成立**，
本批已把抛出方式改为门面调用，登记文档同步改写。审计报告里"错误码表携带默认状态（#4/#5，属机制改造）"的判读准确，
但实际严重度高于"机制改造"（它是两处 5xx 误报 + 一处无业务码的直接成因）。

---

## 七、改动文件清单

**新增（2）**：`common/BlueprintErrors.java`、`test/…/common/BlueprintErrorsTest.java`

**main 改动（13）**：
`common/BlueprintErrorCodes.java`、`application/OrderApplicationService.java`、`application/PaymentApplicationService.java`、
`application/query/support/OrderDetailAssembler.java`、`application/event/PaymentSucceededEventHandler.java`、
`application/event/PaymentRefundedEventHandler.java`、`adapter/web/controller/PaymentController.java`、
`application/event/integration/IntegrationEnvelope.java`、
`application/event/integration/{OrderPaid,OrderPaymentInconsistent,PaymentFailed,PaymentRefunded,PaymentSucceeded}IntegrationEvent.java`、
`adapter/schedule/CancelExpiredOrderJob.java`、`adapter/schedule/CloseExpiredPaymentJob.java`

**test 改动（3）**：`adapter/schedule/CancelExpiredOrderJobTest.java`、`adapter/schedule/CloseExpiredPaymentJobTest.java`、
`adapter/web/controller/OrderControllerContractTest.java`

**文档（5）**：`doc/architecture/Bone-错误码登记.md`、`doc/architecture/Bone-DDD-最终实践方案.md`、
`doc/_generated/blueprint/{as-is-evidence.md,backlog.md,compliance.json}`（重算产物）
