# Bone 项目长期记忆（2026-09-19 定稿，精炼版）

> 只留影响未来决策的真源与坑；细节流水见 `.workbuddy/memory/2026-09-1x.md`，架构决策见 `doc/architecture/adr/`。

## 1. 构建 / 门禁 / 假绿
- pre-commit `scripts/check.sh`：`[1/5]` 全反应堆 `spotless:check`（~14min，前台 120s 被杀）；`[2/5]` 只跑变更模块 `*ArchitectureTest`。他人在途格式违规会阻塞 ⇒ 先 `mvn -o -pl <module> spotless:check`。
- spotless 失败时**直接打印期望 diff ⇒ 照抄手改**，别 `apply`（重排整模块含他人在途文件）；"1 秒返回 0"=缓存命中 ⇒ 删 `target/spotless-index` 重跑。
- 熔断器：连续失败 3 次写 `.git/hooks/.check-fail-count` 阻提交（需 `rm`）；他人红不清熔断器。判退出码用 `PIPESTATUS[0]`。
- ⚠ **规则存在 ≠ 规则覆盖**：凡谓词以 `..X..Y..` 结尾都按逃逸怀疑。实例：共享三入站规则谓词 `..adapter..controller..` ⇒ `adapter.schedule/messaging/rpc` 全逃逸永远绿——**2026-09-19 已收口**：三条改为 `adapters*NotDependOn{GodObjects|DomainRepository|DomainService}`，谓词放宽为 `..adapter..`，仓储规则仅对 `..adapter.schedule..` 开受控例外（ADR-0030 C3），旧名 `adapterControllers*` 保留为 `@Deprecated` 别名（8 个模块仍在调）。**放宽这类谓词前必须实测影响面**：扫全仓「非目标包内是否存在该类依赖」，0 违规才动刀。
- ⚠ **假绿排查**：「恰好通过」问"判定合规还是没看见它"。`oneAggregatePerTransaction` 对非 `AggregateRoot` 实体返 null ⇒ 子实体 `save` 免检；`FreezingArchRule` 冻结恒绿；`cmd || true | sed` 使检查失退出能力。
- ⚠ **编程式事务死角**：`TransactionTemplate` lambda 不被 `getMethodCallsFromSelf()` 追 ⇒ 须人工核聚合数。
- 新门禁必须负向探针验活（临时调违规→报红→还原）；空规则≠要删（`allowEmptyShould(true)` 可能是有意回归门禁）。非 clean 构建删类后 `target/` 残留 `.class` ⇒ bean 名冲突假红，`mvn clean test` 解。
- ArchUnit 1.2.1：精确豁免单类 `doNotHaveFullyQualifiedName(...)` 挂 `noClasses().that()`；`JavaCall.Predicates.target(...)` 泛型是 `AccessTarget.CodeUnitCallTarget`。
- ⚠ **`FreezingArchRule` 基线会因「工具链重编译」失效（2026-09-20 实测，bone-iam 既有红）**：基线按 violation 标识串（含行号+方法名）冻结；重编译后 lambda 归属由 `lambda$N(...)` 漂到外层方法（如 `ApplicationPageQueryHandler.handle(...)`）⇒ 基线匹配不上 ⇒ 报「新违规」，且 ArchUnit 会**删除**基线中「不再出现」的条目（本次 2 个 store 各被删 7 行，已 `git checkout --` 还原）。失败项全在既有类（`ApplicationPageQueryHandler` E-4.2 ×4、`AccountDetailQueryHandler`/`RoleDetailQueryHandler` E-2 ×2），**已用「HEAD 干净 worktree 跑同一条命令」自证与改动无关**。增量编译会掩盖该漂移，`mvn clean` 才暴露 ⇒ 别据此判断自己改坏了门禁，也别把基线改动静默提交。
- ⚠ **R8 聚合纯单测门禁**：`AggregatePureUnitTestCoverageTest.everyAggregateRootHasAPureUnitTest` 要求每个聚合根有同名 `<Aggregate>Test`（纯单测、无容器）。新增聚合根必须同时补测试，否则构建红。

## 2. Git / 共享工作树
- 改名/移动一个提交落地（`git mv`+声明+import+单测）。
- `FreezingArchRule` 写 `archunit_store/` ⇒ 跑测试前 `cp -r` 备份、跑后 `diff -rq` 自证。
- 复刻进 worktree：未跟踪文件用 `git ls-files --others -z`（`git status --porcelain` 中文路径引号致 `cp` 静默失败）；提交前 `TREE=$(git write-tree) && git grep -lE "<旧>" $TREE` 自证；`git commit -F <msg> --only -- <paths>` 非 `git add -A`。
- 「静默 ≠ 冻结」：他人在途改动可静默数小时后复活 ⇒ 动手前重测 mtime。Maven 须绕沙箱（`dangerouslyDisableSandbox`，`mvn -o` 可跑）。`ci.yml` 只跑 `main/master/develop`（不跑 `release/*`），但 `on.push.paths` 型在 `release/*` 触发；gitleaks/DDL 不在 CI。

## 3. SDK 真源
- `Repository<T,ID>` 自身声明 `findByCriteria/countBy/updateByCriteria/aggregate(...)`，依赖 `@ReadSideOnly` 的 `Criteria` ⇒ 读侧 DSL 焊进写侧。**无聚合级联落库**（集合 `@Transient`）、**无派生查询/动态代理**、接口内**禁 `static`/`private` 方法**（`default` 允许）。
- ⚠ **本聚合读的落点规则（2026-09-20 落地 IAM `AppPermissionRepository`）**：`Criteria` 只许出现在 (a) `..domain.repository..`（**扁平包，不含 `domain.app.repository` 这类子包**——bone-iam 内联规则 `domain_no_query_builder` 的豁免谓词就是 `..domain.repository..`）的 `default` 方法里，或 (b) `infrastructure/query`。`..application..` 一律不得出现读侧 DSL（E-4.2，`readSideDslOnlyInQueryAdapter`）。先例：`AccountRepository`（DSL default 放 `com.bone.iam.domain.repository`）；对照 `BoneApplicationRepository`（无 DSL，可在 `domain.app.repository`）。
- ⚠ **SDK 不自动建表**：`MySQLPlugin.generateCreateTableStatement` 只有声明+实现、**全仓无调用方**；表来自仓库根 `bone-init.sql`（手工执行）。新增 `@Table` 聚合必须同步补 DDL 并按 `bone_application` 形态带上 `tenant_id/deleted/version`，否则查询报错。
- ⚠ **枚举持久化有个 `getCode()` 陷阱**：`SqlUtil.toJdbcParameter` 先反射 `getCode()`，拿到非 null 就把它当参数值落库，否则落 `name()`；读回由 `SmartRowMapper` 兜底 `Enum.valueOf` / `of(int)` / `byCode(int)`。⇒ 若新枚举既想要「小写对外表示」又把访问器命名成 `getCode()`，会变成「code 落库、name 读回」错配。正确做法：对外表示另起名（本次用 `AppRole.externalName()`，库里存 `ADMIN/DEVELOPER/VIEWER`，API 输出小写）。`AccountStatus` 是反面样板（`getCode()`→int，列 tinyint，且配 `of(int)`）。
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
- ⚠ **404 会被 catch-all 兜成 500（2026-09-20 平台级修复）**：Spring Boot 3/Spring 6 未匹配路由抛 `NoResourceFoundException`（**不是**旧版 `NoHandlerFoundException`，框架 `bone-web` 只 catch 后者=死代码），而各模块 `@ExceptionHandler(Exception.class)` 会把它兜成 500。修法是各 `GlobalExceptionHandler` 补 `NoResourceFoundException→404`。**框架级 advice 经 `AutoConfiguration.imports` 注册但受 `@ConditionalOnMissingBean(name="globalExceptionHandler")` 约束** ⇒ 自带 handler 的模块（system/masterdata/metadata-server）与自造 handler 的模块（studio-generator，只依赖 `bone-core`）都得各自补。附带效应：修好前 OPTIONS 探测会把「真缺失」与「500」都算 inconclusive，掩盖接口缺口（本次契约缺口从 12 暴露为 23）。
- ⚠ **给 Controller 加构造器参数会打爆手工装配的测试**：`@RequiredArgsConstructor` 加依赖后，`new XxxController(...)` 型单测 test-compile 直接失败（本次 `LogControllerTest`）。改完 grep 一遍 `new <Controller>(`。
- **Outbox 幂等**：补偿触发器型集成事件（如 `OrderStockActionFailed`）用业务身份派生**确定性 eventId** + 先查后插去重（表无唯一索引，靠「单写者」假设；最终兜底是消费端按 eventId 去重）。事实流型事件仍用随机 UUID。
- **装配级测试用 H2，不用 Testcontainers**：装配测试验「上下文能否装配」（bean 名冲突/缺失依赖/循环引用/配置前缀），与 DB 方言无关；Testcontainers 需 Docker 守护进程+拉镜像，离线与无网 CI 不可用。持久化语义留给 `-Pintegration` 本地 MySQL 集成测试。坑：`fixedDelay` 型 Job 无法用 `-` 关闭且启动即跑一次，须 `@MockBean` 摘掉，否则在空库上报 `BadSqlGrammarException` 且被吞。
- **改共享门禁后必查冻结基线**：新谓词=新规则描述=新冻结键；跑完 diff `archunit_store/`，**新增条目必须为空文件**（空=零违规=真绿；非空=把存量违规冻结后放行=假绿）。旧描述的孤儿条目无害但会膨胀。

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
