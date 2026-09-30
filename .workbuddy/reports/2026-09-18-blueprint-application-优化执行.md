# bone-blueprint / application 优化执行报告

日期：2026-09-18 ｜ 依据：`.workbuddy/reports/2026-09-18-blueprint-application-审查.md`
范围：`application/**` 的 3 个 P1 + 4 项重复代码 + 3 项文档漂移

---

## 一、验证结论（可复现）

在**独立 worktree** 里复刻「主树全部在途改动（含并发会话的 ADR-0028 式重构）+ 我的改动」后：

| 检查 | 结果 |
|---|---|
| `mvn -o -pl bone-blueprint test` | **176 tests, 0 failures, 0 errors；BUILD SUCCESS**（含 `ArchitectureTest` 26 条） |
| `spotless:check`（删 `target/spotless-index` 绕开缓存） | **165 files clean，0 needs changes** |
| `archunit_store/` | **无漂移**（基线只收缩不扩张） |
| `blueprint-compliance-collector --check` | **up to date**（已同步重算 `doc/_generated/blueprint/`） |
| `check-ddd-doc-code-sync.py --strict` / `check-ddd-gate-state.py` | **OK** |
| `check-ddd-doc-drift.py` | **失败**——非本批改动所致，见 §四 |

复刻方法：`git worktree add --detach <tmp> HEAD` → `git diff HEAD -- . ':(exclude).workbuddy' | git -C <tmp> apply` → 未跟踪文件 `tar` 复制 → 在 worktree 内构建。**主树全程未跑过模块测试**（避免改写已入库的 114 个基线文件）。

## 二、已落地改动

### P1-1 读侧 SQL 漏软删条件（真实数据缺陷）
`src/main/resources/sql/order/findOrderWithItems.sql`
```sql
LEFT JOIN t_order_item oi ON o.id = oi.order_id AND oi.deleted = 0
... AND o.deleted = 0
ORDER BY oi.id
```
修掉「软删明细仍驱动远程库存预留/扣减」；`ORDER BY oi.id` 让明细顺序稳定（此前 `OrderDetailAssembler.fromRows` 取 `rows.get(0)` 当订单头）。已复核该 SQL 仍被 `infrastructure/query/OrderQueryAdapter` 引用，修复仍然生效。

### P1-2 失败路径 Outbox 脱离业务事务（丢事件窗口）
- `application/PaymentApplicationService#processCallback` 失败分支：与成功路径**同形态**——`markFailed` → 取出 `PaymentFailedEvent`（在 `publishFrom` 清空前）→ `saveWithVersionCheck` → **同事务** `orderOutboxWriter.appendPaymentFailed(...)`。
- **删除** `application/event/PaymentFailedEventHandler.java`：它是该 Outbox 写入的唯一调用点，形态为 `AFTER_COMMIT + REQUIRES_NEW`（此时支付单早已提交），与端口契约「须与支付单置 FAILED 同事务」直接矛盾；保留它会与事务内写入形成**重复事件**。无测试引用它。
- 端口的 javadoc 无需改动——现在契约与实现一致。

### P1-3 幂等服务在 application 层收发 HTTP 类型
- `application/support/BlueprintIdempotencyService`：新增嵌套 `record ReplayedResponse(int status, String location, ApiResponse<?> body)`；`replay(...)` 由 `Optional<ResponseEntity<ApiResponse<T>>>` 改为 `Optional<ReplayedResponse>`，`remember(...)` 收 `ReplayedResponse`；移除 `ResponseEntity`/`HttpHeaders` 依赖。**快照落库的 JSON 结构不变**，与既有数据兼容。
- `adapter/web/controller/OrderController`：新增私有 `toHttpResponse(ReplayedResponse)` 渲染 `ResponseEntity`——HTTP 状态码与响应头只由 adapter 决定。
- 同步测试：`BlueprintIdempotencyServiceTest`、`OrderControllerContractTest`。
- README 同步：删掉「本模块是其分层更干净的版本」这类空口自评，改为陈述实际形态（应用层不出现 HTTP 类型 + `ReplayedResponse`）。

### 重复代码
| # | 处理 |
|---|---|
| #1 `extractEvent` / `extractDomainEvent` 两份 | 新增 `application/support/DomainEvents#extract(List<DomainEvent>, Class<T>)`；`PaymentApplicationService`、`PaymentSucceededEventHandler` 各自删除私有副本并改用之 |
| #2 两个库存处理器逐行同构 ~35 行 ×2 | 两个 handler 接到 `application/support/OrderItemInventoryExecutor#forEachItem(...)`（并发会话已建好该骨架但**尚未接线**；接线同时修掉了它们仍引用已删除包 `query.dto.OrderWithItemsProjection` 导致的**编译不过**） |
| #6 `OrderDto` 手写 Builder | 并发会话已自行改为 `@Builder`，无需处理 |
| #7 集成事件 `SCHEMA_VERSION` + `fromDomain` | **未做**，见 §四 |

### 文档漂移
- 3 个集成事件的 javadoc「为何在 **domain** 包」→「为何在 **application** 包」，并写清依赖方向（写侧由 infrastructure 按端口契约构造、读侧由 adapter MQ 端与 application 消费型服务解析，故放在 application 才能保持依赖向内）。**注**：审查报告原文写「5 个」，实为 3 个（`PaymentFailed`/`PaymentRefunded` 两份没有该段），此为原报告的错误，已在此更正。
- `application/port/out/package-info`：删掉「adapter 层禁止直引此包下的端口」——与实现相反（`OrderOutboxRelayJob`、`OrderPaymentInconsistencyJob` 直接注入），且 E-10.1 只要求依赖向内，adapter → application 是允许方向。
- `README.md`：打洞登记表里不存在的 `[打洞]` 日志前缀改为真实的 `[全租户扫描]`（附实际调用的查询方法名）。
- `OrderController` 类注释里指向已不存在的 `CommandHandler`/`QueryHandler` 的表述一并修正。

## 三、与并发会话的冲突处理（过程记录）

同一工作树内有另一会话在做更大重构（`command/cmd/*` 上提、`port/out/*` 改名 `*Port`、投影迁 `query/projection/`、新增 `application/support/`），方向与我报告建议高度重合。

- **首次测量被推翻**：14:55 的 `git status` 是 30 项，15:01 变成 93 项，HEAD 全程 `47b87aac` ⇒ **`git status` 的行数同样是快照**，判「文件是否被他人占用」必须在动手前那一刻重测。
- **我曾撤出一批改动**：因对方删除了 `OrderWithItemsProjection`，我的新类会让主树编译不过；撤出前**逐文件判归属**（grep 双方各自标记词），发现 `query/support/OrderSummaryAssembler.java` 已是**对方版本**（已 import `query.projection`），未做 `git checkout`——盲目回滚会毁掉对方未提交的工作。
- 随后按用户指示「现在就做，我适配新结构」，在新路径上重做，并顺带补齐了对方未完成的两处接线。

## 四、未做 / 待裁决

1. **`check-ddd-doc-drift.py` 当前是红的**（**非本批改动**）：`doc/architecture/Bone-DDD-最终实践方案.md` L1139/1143/1167/1175 出现 `.saveWithVersionCheck(`，该 API 是 **blueprint 模块本地仓储方法**，不在白名单真源（`API_SOURCES` 只收 bone-core / metadata-sdk 的平台类）。
   - 修法二选一：① 把 blueprint 的 `domain/repository/*` 纳入白名单（属 `scripts/` 改动，按 AGENTS.md §12 是 **L3，需架构师审批**）；② 文档侧把该调用标为非平台 API 的示意代码。
   - **这会让任何提交都撞上文档门禁**，建议优先处置。
2. **重复代码 #7**（5 个集成事件的 `SCHEMA_VERSION` + `fromDomain` 样板）：`fromDomain` 目前被 `OrderOutboxPortAdapter` 在 5 处使用，删除它等于把跨边界构造逻辑挪进 infrastructure。收益是薄样板，改动面反而是契约归属，**不建议在本轮做**。
3. **重复代码 #4/#5**（`BizException(409, …)` 翻译 5 处、`BizException(404, CODE + ": " + id)` ~8 处）：正解是「错误码表携带默认 HTTP 状态」的机制改造，涉及 `common/BlueprintErrorCodes` 与全部 adapter，属独立任务。
4. **重复代码 #9**（3 个定时任务的 `for + try/catch` 样板）：需要与并发会话的 `adapter/schedule/*` 改动合并考虑，本轮未动。
5. **`ArchitectureTest:204` 指向 `..application.command.handler..` 的规则已成空规则**（该包已被删除）。删规则会削弱未来约束、留规则只是空转，属治理决策，未擅自处置。
6. **本批改动无法独立提交**：它依赖并发会话尚未提交的 `OrderItemInventoryExecutor`、`query/projection/`、`port/out/*Port`。单独 `git commit --only` 这批文件会得到**编译不过的提交**。⇒ 需与对方的重构**同批**落地。
