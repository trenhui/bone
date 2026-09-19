# Bone 项目长期记忆（2026-09-19 定稿，精炼版）

> 只留影响未来决策的真源与坑；细节流水见 `.workbuddy/memory/2026-09-1x.md`，架构决策见 `doc/architecture/adr/`。

## 1. 构建 / 门禁 / 假绿
- pre-commit `scripts/check.sh`：`[1/5]` 全反应堆 `spotless:check`（~14min，前台 120s 被杀）；`[2/5]` 只跑变更模块 `*ArchitectureTest`。他人在途格式违规会阻塞 ⇒ 先 `mvn -o -pl <module> spotless:check`。
- spotless 失败时**直接打印期望 diff ⇒ 照抄手改**，别 `apply`（重排整模块含他人在途文件）；"1 秒返回 0"=缓存命中 ⇒ 删 `target/spotless-index` 重跑。
- 熔断器：连续失败 3 次写 `.git/hooks/.check-fail-count` 阻提交（需 `rm`）；他人红不清熔断器。判退出码用 `PIPESTATUS[0]`。
- ⚠ **规则存在 ≠ 规则覆盖**：共享三规则 `adapterControllersMustNotDependOn{DomainRepository|DomainService|GodObjects}` 谓词均 `..adapter..controller..` ⇒ `adapter.schedule/messaging/rpc` **全逃逸永远绿**。凡谓词以 `..X..Y..` 结尾都按此怀疑。
- ⚠ **假绿排查**：「恰好通过」问"判定合规还是没看见它"。`oneAggregatePerTransaction` 对非 `AggregateRoot` 实体返 null ⇒ 子实体 `save` 免检；`FreezingArchRule` 冻结恒绿；`cmd || true | sed` 使检查失退出能力。
- ⚠ **编程式事务死角**：`TransactionTemplate` lambda 不被 `getMethodCallsFromSelf()` 追 ⇒ 须人工核聚合数。
- 新门禁必须负向探针验活（临时调违规→报红→还原）；空规则≠要删（`allowEmptyShould(true)` 可能是有意回归门禁）。非 clean 构建删类后 `target/` 残留 `.class` ⇒ bean 名冲突假红，`mvn clean test` 解。
- ArchUnit 1.2.1：精确豁免单类 `doNotHaveFullyQualifiedName(...)` 挂 `noClasses().that()`；`JavaCall.Predicates.target(...)` 泛型是 `AccessTarget.CodeUnitCallTarget`。

## 2. Git / 共享工作树
- 改名/移动一个提交落地（`git mv`+声明+import+单测）。
- `FreezingArchRule` 写 `archunit_store/` ⇒ 跑测试前 `cp -r` 备份、跑后 `diff -rq` 自证。
- 复刻进 worktree：未跟踪文件用 `git ls-files --others -z`（`git status --porcelain` 中文路径引号致 `cp` 静默失败）；提交前 `TREE=$(git write-tree) && git grep -lE "<旧>" $TREE` 自证；`git commit -F <msg> --only -- <paths>` 非 `git add -A`。
- 「静默 ≠ 冻结」：他人在途改动可静默数小时后复活 ⇒ 动手前重测 mtime。Maven 须绕沙箱（`dangerouslyDisableSandbox`，`mvn -o` 可跑）。`ci.yml` 只跑 `main/master/develop`（不跑 `release/*`），但 `on.push.paths` 型在 `release/*` 触发；gitleaks/DDL 不在 CI。

## 3. SDK 真源
- `Repository<T,ID>` 自身声明 `findByCriteria/countBy/updateByCriteria/aggregate(...)`，依赖 `@ReadSideOnly` 的 `Criteria` ⇒ 读侧 DSL 焊进写侧。**无聚合级联落库**（集合 `@Transient`）、**无派生查询/动态代理**、接口内**禁 `static`/`private` 方法**。
- `publishFrom(aggregate)` + `@NoDomainEvent` 类级豁免；`applicationSaveMustPairWithPublishOrExempt` 类级判定。
- `@TenantScope`：**MANUAL** 默认；软删不自动注入（须手写）；AUTO 无锚点+JOIN → `IllegalStateException`。模板回退：`sql/...` → `sql-templates/...` → `@Sql`。
- 三条阻塞：`@Version`(已落地)/聚合级联(CORE-11)/强类型ID(E-7.1)。blueprint=L3 参考实现（非照抄范式）；已删全部 `*CommandHandler`/`*QueryHandler`(ADR-0028)。

## 4. 文档治理
- 权威真源=`doc/architecture/Bone-DDD-最终实践方案.md`；改完跑 `check-ddd-doc-drift.py` + `ci/check-ddd-doc-code-sync.py --strict` + `check-ddd-gate-state.py`；改模块/采集器重算 `doc/_generated/<mod>/`（`ci/collect-all-compliance.sh --check` ~1.5s）。
- HC 状态唯一真源=`G-1.7`（由 `gate-state.json` 渲染，手改必打回）。入口文件只写编号不复制约束/状态。
- 正文禁用 `R[1-9]`；引用未落地规则名判假⇒登记 `KNOWN_MISSING`；doc-code-sync `--strict` 校验命名样例右侧类真实存在。
- E-13 默认 Advisory（命名问题≠CI 违规）。出站实现一律 `*Adapter`（业务端口→`infrastructure/gateway/<外部系统>`，技术端口→`infrastructure/<能力>`）；占位用 `Mock` 前缀（禁 `gateway/mock` 包）；`*Assembler`(adapter)/`*Converter`(infra)；`*Controller` 必在 `controller/` 子包。

## 5. 工程约定
- `BizException(int,..)` 首参 HTTP 状态，业务码拼 message；`BizException(String)` 默认 500。blueprint 收口 `common/BlueprintErrors`。
- 幂等平台能力 `IdempotencyService`（application 层零 `ResponseEntity`）。集成事件 `fromDomain` 入参须散装标量。
- adapter 层就地 `@Value`（Mockito 不注入⇒ `ReflectionTestUtils.setField`）。⚠ 带默认值占位符写错键名**静默失效**（实例 IP 白名单永久失效）。
- **Outbox 写须与业务写同事务 `MANDATORY`** ⇒ AFTER_COMMIT 落 Outbox 须另开 `REQUIRES_NEW`。

## 6. ADR-0030 写侧仓储合并本聚合读
- 判据：本聚合读（含全租户扫描）→ 域仓储；跨聚合/报表/搜索 → `application/query/port`+`infrastructure/query`。**blueprint 现已无 `*QueryPort`**。
- 代价：C1 读侧护栏消失（握域仓储=握写能力）；C2 聚合内实体事件侧不可达（SDK 无级联→`items`@Transient→订阅器绕道投影，CQRS 反转）；C3 定时 Job 直连域仓储是受控授权形态（`adapter/schedule` 现 3 类）。
- 通道：单列过滤→Criteria+`disableTenantFilter()`；联表扁平→`@Sql` 外置。同一域仓储按方法混用通道正常。

## 7. 其他应用模块 DDD 符合性（2026-09-19 实测）
- 候选具四层：`bone-iam`/`bone-integration`/`bone-masterdata`/`bone-system`/`bone-notification`/`bone-engine/bone-metadata-server`；`bone-file`/`bone-gateway` 无 domain（非 DDD 业务模块，不纳入）。
- **结论：已普遍达标**（ArchitectureTest 17–25 条、强类型 ID、无乱分包、无内嵌静态类）。**唯一 concrete 违规=masterdata 三出站 `*Gateway`/`*Impl`→`*Adapter`，已修**（clean test 51 绿）。
- masterdata `DataQualityQueryPort`/`MasterDataQueryPort` 是跨聚合读（ADR-0030 不折叠）。
- 待裁定差异：iam/integration 的 schedule Job 缺 blueprint 那两条 `schedule`/`*AllTenants` 模块级门禁；仅 integration 有完整 Outbox。system/file/gateway/notification 未进 `doc/_generated` 采集。

## 8. 跨项目坑
- `.gitignore` 通用规则会吞源码（`log/` 吞 `...domain.model.log`；`*.properties` 吞测试资源）⇒ 致害规则后加窄豁免。
- 改架构前先查「既有门禁是否已就此表过态」（忽略会把有意旁路当疏漏修 ⇒ 弄红门禁更糟）。
- 「注释与实现矛盾」双向排查：既可能代码错，也可能注释停在改造前。
- `.claude/agents/**` 不在门禁覆盖内，极易过期。
