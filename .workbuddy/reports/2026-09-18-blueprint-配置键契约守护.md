# blueprint 配置键契约守护 — 执行报告（含一处真实缺陷修复）

> 日期：2026-09-18 23:05–23:30 ｜ 分支 `release/mvp-v1.0` ｜ HEAD `47b87aac`（未提交）
> 承接：上一轮《定时任务参数配置化》报告「五、未做 / 待你定」第 2 条——**键名一致性无机器守护**。

---

## 一、先对账，结果抓到一个真 bug

上一轮我说「6 个键已人工核对一致，但键名接错不会被任何检查发现」。本轮第一步就是**把全模块的引用键与定义键做一次机器对账**，结果在**我没碰过的文件**里发现一处已存在的错配：

| 位置 | 键名 | 结果 |
|---|---|---|
| `PaymentController:53` 读取 | `bone.payment.callback.allowed-source-ips` | ❌ |
| `application.yml:25` 定义 | `bone.blueprint.payment.callback.allowed-source-ips` | 差一个 `blueprint` 段 |

**后果（安全相关）**：占位符带默认值（`:` 后为空串）⇒ Spring **不报错、不打日志**，静默回落到默认值空串 ⇒ `allowedSourceIps()` 返回空列表 ⇒ `isSourceAllowed()` 恒为 `true` ⇒ **支付回调的来源 IP 白名单永久失效**。运维在 yml 里配了白名单，实际一个都不拦。

**为什么一直没暴露**：jacoco 显示 `allowedSourceIps()` 的 **4 个分支全部未命中**（该逻辑零测试覆盖），且失效形态是"更宽松"而不是"报错"，运行期毫无征兆。

---

## 二、改动清单（4 个文件）

| 文件 | 改动 |
|---|---|
| `adapter/web/controller/PaymentController.java` | ① **修键名** → `bone.blueprint.payment.callback.allowed-source-ips`；② 修两处 ADR-0028 后的过期 javadoc（原写"发起支付/回调仍走独立 `CommandHandler`"、"验签在 adapter 边界完成"——实际验签在 `PaymentApplicationService#processCallback` 经 `PaymentSignaturePort` 完成） |
| `src/test/java/com/bone/blueprint/ConfigKeysContractTest.java` | **新增**：配置键契约守护（详见下节） |
| `src/test/java/com/bone/blueprint/adapter/web/controller/PaymentControllerCallbackSourceTest.java` | **新增**：回调来源白名单契约测试，4 个用例（补上此前的 0 覆盖分支） |
| `TEST_GUIDE.md` | 登记两个新测试；顺带修正三处过期：架构门禁 26→**27** 条、`OrderPaidConsumptionApplicationServiceTest`（已随类删除）→ `integration/consumer/OrderPaidIntegrationEventConsumerTest`、结构树里已删除的 `OrderPaidConsumptionApplicationService` |

---

## 三、守护测试怎么设计（`ConfigKeysContractTest`）

扫 `src/main/java` 的 `.java` + `src/main/resources` 的 `*.yml`，用 snakeyaml 把 `application*.yml`（含 dev/prod/mq）展平为点式键，然后双向断言：

| 断言 | 方向 | 覆盖的错误 |
|---|---|---|
| `referencedKeysMustBeDefined` | 引用的 `bone.*` 键 ⊆ yml 定义 | **键名写错 → 静默回落默认值**（本次那个 bug） |
| `scheduleKeysMustBeReferenced` | yml 的 `bone.blueprint.schedule.*` ⊆ 源码引用 | 死配置（写了却没人读，运维以为调过） |

**为什么反向只做 `schedule.*`**：其余 `bone.*` 段由 `@ConfigurationProperties` 的 binder 消费（如 `OrderOutboxProperties`），yml 里没有对应占位符，全量反向断言会全是假阳性。

**为什么不用 `ApplicationContextRunner`**：本模块无容器级测试形态；且即使起了上下文，带默认值的 `@Value` **仍然不会失败**——容器级测试抓不到这类错误。源码级对账才是唯一能拦住它的手段。源码不可见时（非模块根工作目录）走 `Assumptions` 跳过，不误报。

---

## 四、反向验证：三条失败路径都实测过

| 注入的错误 | 实测结果 |
|---|---|
| 把键名改回 `bone.payment...` | ❌ `referencedKeysMustBeDefined` 失败：`占位符键 bone.payment.callback.allowed-source-ips 未在任何 application*.yml 中定义（会静默回落到默认值）` |
| yml 的 schedule 段加一个 `bogus-window-minutes` | ❌ `scheduleKeysMustBeReferenced` 失败：`yml 定义的 bone.blueprint.schedule.bogus-window-minutes 没有任何源码引用（死配置）` |
| 删掉 yml 里整个 `bone.blueprint.*` 段 | ❌ 两条同时失败（引用的键全部无定义） |

即：**它不是"加了个测试就绿"的装饰品**，三种退化都能拦。

---

## 五、验证（复刻主树全部在途改动进独立 worktree，主树未跑模块测试）

| 检查 | 结果 |
|---|---|
| `mvn -o -pl bone-blueprint test` | **190 tests, 0 failures, BUILD SUCCESS**（184 → 190：+4 白名单契约、+2 配置键契约；含 `ArchitectureTest` 27 条） |
| `spotless:check`（删 index 绕缓存后单跑） | **172 files clean, 0 needs changes, 0 skipped by caching** |
| 三道 DDD 文档门禁 | drift **OK**（114 白名单符号）／doc-code-sync `--strict` **OK**／gate-state + `generate --check` **OK**（5 张表无漂移） |
| 合规产物 `collect.py --check` | **up to date**（无需重算） |
| `archunit_store` | 主树**零改动**（测试只在 worktree 跑） |

spotless 首跑仍报 3 个文件需格式化（javadoc 段落 reflow），在 worktree `apply` 后用 `diff -rq` 确认**只动了本轮这 3 个文件**，才回写主树。

---

## 六、未做 / 待你定

1. **🟡 同类风险只在 blueprint 被守护，其他模块没扫**——平台 18 个模块里可能还有同款「键名错配 + 带默认值静默回落」。要覆盖全平台有两个位置可选：① 做成 `bone-architecture-test` 的共享规则（**属 `scripts/` / 共享库改动 = L3，需架构师审批**）；② 先写个一次性排查脚本跑一遍、只报不改。**要不要做、做哪种，你定。**
2. **🟡 ADR-0029 逃生舱仍未预置**（上一轮遗留，未变）：三个全租户扫描 Job 在 SDK 自动注入 `tenant_id` 后会因 `TenantContext` 为空被打挂。ADR 未批准，不按未决方案改代码。
3. **`dev` / `prod` / `mq` 三档仍未覆盖 `bone.blueprint.schedule.*`**（无明确运营值可填），生产若要独立窗口按需补覆盖即可。
4. **提交仍未执行**：HEAD 恒 `47b87aac`，在途 233 项（含并发会话的重构）。本批与对方重构**必须同批提交**，单独提交会编译不过；另 `git add/commit` 在本环境被沙箱拦截，需你在本机终端执行。
