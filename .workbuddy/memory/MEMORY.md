# Bone 项目长期记忆（2026-09-18 23:20 再压缩）

## 1. 构建 / 门禁 / CI
- **pre-commit → `scripts/check.sh`**：`[1/5] mvn spotless:check` 全反应堆扫描（本机 ~14 分钟，前台 120s 被杀）；`[2/5]` 只对变更模块跑 `*ArchitectureTest`。**任何人在途格式违规都会阻塞别人的提交** → 提交前先 `mvn -o -pl <module> spotless:check`。
- **spotless「1 秒返回 0」不可信**（命中缓存）→ 删 `target/spotless-index` 重跑；判据看输出有无 `format violations` / `BUILD FAILURE`。格式回写必须 `spotless:apply` 在 worktree 跑 + `diff -rq` 确认只改自己文件。
- **熔断器**：连续失败 3 次写 `.git/hooks/.check-fail-count`，达 3 阻断全部提交（需 `rm`）。他人文件导致的红：**不清熔断器**。
- **`check.sh` 的 ArchUnit 曾长期静默跳过**（09-17 修复，原因 `grep ... || true | sed` 被解析为 `(grep) || (true|sed)`）⇒ 修好前的历史提交都没真过 ArchUnit。同坑：`|| true` 放管道末尾；判退出码用 `PIPESTATUS[0]`。
- **CI**：`ci.yml` 只跑 `main/master/develop`，**不跑 `release/mvp-v1.0`**（该分支后端测试/ArchUnit 从不执行，红灯不暴露；本地 pre-commit 仍会跑）。`iam-gateway.yml`、`docs-compliance.yml` 用 `on.push.paths` 不带分支过滤 ⇒ `release/*` 也触发。
- `ci.yml` backend-quality 实际调用：`spotless:check`、`mvn verify`（含 ArchUnit）、`jacoco:check`（门槛实测 10%）、`check-ddd-doc-code-sync.py --strict`、`check-ddd-doc-drift.py`、`check-ddd-gate-state.py`(+`generate --check`)。gitleaks / DDL 检查**不在 CI**。
- `collect-all-compliance.sh --check` ~15 分钟须后台跑。

## 2. Git / 共享工作树纪律
- **类型改名/移动必须一个提交落地**（`git mv` + 声明 + import + 单测）；只暂存 `git mv` 会被后续 `git commit` 连索引扫走 → 编译中断提交。
- **提交前索引树自证**：`TREE=$(git write-tree) && git grep -lE "<旧符号>" $TREE -- <模块>` 须为空；验证提交健康用 `git worktree add --detach <sha>`，**不要用工作树**。
- **`git commit -F /tmp/msg.txt --only -- <paths>`**（选项在 `--` 前）；一律后台跑；避免 `git add -A`。
- **ArchUnit `FreezingArchRule` 写 `archunit_store/`** ⇒ **绝不在脏主树跑模块测试**（会改写已入库 114 个基线）。验证一律独立 worktree。
- **复刻主树进 worktree 的标准动作**：`git diff HEAD > p.patch` + `git -C $WT apply`，**未跟踪文件必须另拷**（用 `git -c core.quotepath=false ls-files --others -z`，`git status --porcelain` 会给中文路径加引号导致 `cp` 失败）。空目录不会被列出。
- **「静默 ≠ 冻结」**：他人在途改动可静默数小时后复活 → 动手前重测 mtime（`stat -f "%Sm %N"`）。
- Gitee SSH 会抖动（kex reset）重试即可；Gitee/GitHub 两个 pushurl 顺序推送。

## 3. ArchUnit / 基线政策
- `archunit_store/` 入库；**基线只可收缩**；存量违规按 ADR-0027「freeze + 登记（LYR-03），修复后收缩」。
- **README 明文「不 freeze」**：`domainMustNotDependOnOuterLayers`、P0-5/6（`*MustNotUseQueryBuilder`）、`domainRepositoriesShouldOnlyDeclareWhitelistedMethods` ⇒ 正解是 ReadPort 拆分，不是 freeze。例外：masterdata P0-6 已 freeze；iam 登记 E-9.3 137 行 + E-4.4 12 行债务。
- **`release/mvp-v1.0` 上本来就是红的**：bone-masterdata（P0-5 ×12 + P0-6 ×14）、bone-iam（E-9.3 ×4 + E-4.4 ×2）。成因 `777cb395` 给 `Criteria` 加 `@ReadSideOnly`。
- **待裁决分歧（他人引入）**：blueprint `ArchitectureTest#domain_no_query_builder` 被改为内联规则 + 豁免 `..domain.repository..`；`BoneDddArchRules` 的 save/publishFrom 放宽为 `startsWith("save")`。与 README「样板不 freeze」冲突。
- **空规则不等于要删**：`ArchitectureTest` 里 `command_handlers_must_not_depend_on_application_service`（ADR-0028）当前 0 匹配但带 `allowEmptyShould(true)`，是**有意保留的回归门禁**（防 CommandHandler 复活）；同类无注释的才是治理债。

## 4. SDK 能力真源（评估落地性必看）
- `com.bone.metadata.sdk.Repository<T,ID>` **自身**声明 `findByCriteria/countBy/updateByCriteria/aggregate(...)`，全依赖 `@ReadSideOnly` 的 `Criteria` ⇒ 读侧 DSL 焊进写侧仓储。**写路径（domain / 应用服务写方法）不查询**，读侧下沉 `application/query/port`。
- `BaseRepository`：`save/insert` 返 `ID`、`update` 返 `boolean`、`updateByCriteria` 返 `int`；**无聚合级联落库**（集合须 `@Transient`）；**写路径不消费 `@Version`**（只 `WHERE pk=?`）。
- 34 个 `domain.repository` 全是空接口；SDK **不支持派生查询/动态代理**。
- `bone-core` 事件：`DomainEventPublisher.publishFrom(aggregate)`；`@NoDomainEvent` 类级豁免。`applicationSaveMustPairWithPublishOrExempt` 是**类级**判定。
- **三条 SDK 阻塞**：① `@Version` 乐观锁（P0/CORE-07）② 聚合级联落库（P1/CORE-11）③ 强类型 ID（P2/E-7.1）。
- **`bone-blueprint` = L3 参考实现**：当前 `D1 + Shared` + 子实体级 `OrderItemRepository`——已登记 SDK 技术债，**不是可照抄范式**；L2/L3 默认 `D0 + Separated`。2026-09-18 起 blueprint 已删除全部 `*CommandHandler`/`*QueryHandler`，改语义化 `*ApplicationService`（ADR-0028）。

## 5. 规范权威性与冲突裁定
- **E-13 命名默认 Advisory**（不阻断 CI）⇒ 报命名问题**不得**说成 CI 违规。
- **adapter DTO**：`adapter/web/dto/request|response/`（E-13.1 优先于旧 AGENTS.md）；新增一律此形态。
- `domain/gateway/*ReadPort` 是已登记存量（Legacy-E-9.5），新读侧端口用 `application/query/port` + `*QueryPort`。
- **入站适配器命名（E-13.0/13.5）**：协议由包路径承载，**类名不带协议标记**，标记下沉 DI 标识。`*Assembler`(adapter) / `*Converter`(infrastructure)。各协议自持 DTO。
- **`*Controller` 必须在 `controller/` 子包内**（`..adapter..controller..` 匹配，平铺即逃逸）。测量陷阱：`@RestControllerAdvice` 含子串 → 正则 `@RestController\b(?!Advice)`。
- **新门禁 `springComponentBeanNamesMustBeUnique`（v5.5.9 Hard）**：按 `AnnotationBeanNameGenerator` 口径推算 bean 名，把同名撞车提前到构建期。
- **配置读取分层（09-18 实证）**：`infrastructure/config/*Properties` 的注入方**只在 infrastructure 内**（`OrderOutboxProperties` 仅被两个 `*PortAdapter` 注入）；**adapter 层读配置一律就地 `@Value` 占位符**（范式 `PaymentController`：`@RequiredArgsConstructor` + 非 final 字段 + `@Value`）。副作用：`@Value` 字段 Mockito 不注入 ⇒ 单测用 `ReflectionTestUtils.setField`。
- **ADR-0029（提议，待批）** 会让 SDK 对租户表自动注入 `tenant_id`，`TenantContext` 为空即抛 `MissingTenantContextException` ⇒ 三个全租户扫描 Job 须在实施时补逃生舱（`disableTenantFilter()` / `setTenantId`），未批准前不改代码。

## 6. DDD 文档治理（三道检查 + 单真源）
- 三道：`scripts/check-ddd-doc-drift.py`、`scripts/ci/check-ddd-doc-code-sync.py --strict`、`scripts/check-ddd-gate-state.py`（+`generate --check`）。改文档后三个都跑。
- **HC 状态唯一真源 = `Bone-DDD-最终实践方案.md` 的 `G-1.7`**。HC-003/006/008 = Planned；HC-001/004 = Manual；HC-005 = Active 但门槛 10%。状态表由 `doc/architecture/gate-state.json` 渲染，手改必被打回；文档+JSON+脚本+`ci.yml` 同批提交。
- **入口文件（AGENTS.md / copilot-instructions / CLAUDE.md）不得复制约束或门禁状态**。AGENTS.md 已收敛为 35 行薄引用，正文在 `doc/agents/` 六册（沿用原 § 编号）。
- **不要新建摘要/导读文件**。领域事件判据两层：① 是不是业务语言的事实（禁以"当前无下游"豁免）② 是否 `publishFrom()` / Durable。CORE 表编号禁止重排（01-08 通用、09-12 工程）。
- **实测计数口径（G-1.8）**：`--metrics` 现算，排除 `bone-architecture-test` fixture；数字随重构频繁变，引用前必须现跑。
- docs-compliance CI 精确比对 `compliance.json` 的 `as_is`+`backlog`：改采集器或模块源码后**同提交重算 `doc/_generated/<module>/`**（`tools/blueprint-compliance-collector/collect.py --check` ~90s）。改 `Bone-DDD-最终实践方案.md` 无需重算。
- 既有断链（仅登记未修）：`doc/CODE_WIKI.md` 25 条相对链接按仓库根写；`doc/architecture/bone-前端架构.md:168` 引用不存在的 `START_GUIDE.md`。

## 7. 错误码 / 集成事件 / 幂等（2026-09-18 落地）
- **`BizException(int code, …)` 首参是 HTTP 状态**，业务码只能拼进 message；`BizException(String)` 默认 500 ⇒ 用它等于把"查不到/冲突"报成服务端故障。
- **blueprint 已收口**：`common/BlueprintErrors` = 「码 → HTTP 状态」唯一真源（`Map` + 静态块反射校验 `BlueprintErrorCodes` 全部常量，漏登记即类加载失败）；抛出走 `of/supplier`；`httpStatusOf` **不兜底**。其他模块仍是旧形态，迁移参照 blueprint。
- **`saveWithVersionCheck` 是 blueprint 本地仓储方法**，不在 `check-ddd-doc-drift.py` 的 `API_SOURCES` ⇒ 文档里当平台 API 演示会变红（已解：文档改为说明性叙述）。
- 幂等已升格平台能力：`bone-core: com.bone.core.idempotency.IdempotencyService`（出参 `ReplayedResponse`），application 层零 `ResponseEntity`。
- **信封版本 ≠ 载荷版本**：`application/integration/event/IntegrationEnvelope.CURRENT_SCHEMA_VERSION`（载荷）vs `OrderOutboxEnvelopeFactory.ENVELOPE_SCHEMA_VERSION`（投递封装），不合并。集成事件 `fromDomain` 入参必须是散装标量（避免契约依赖 domain）。

## 8. 配置键 / 占位符（2026-09-18 新增）
- **带默认值的占位符写错键名会静默生效失败**（Spring 不报错、无日志）。实例：`PaymentController` 读 `bone.payment.callback.allowed-source-ips`，yml 定义在 `bone.blueprint.payment.` 下 ⇒ **支付回调来源 IP 白名单永久失效**（回落到空串＝不限制来源），jacoco 显示该分支 0 覆盖所以从未暴露。已修键名并补 `ConfigKeysContractTest`。
- **`ConfigKeysContractTest`（模块根）**：扫 `src/main/java` 的 `.java` + `src/main/resources` 的 `*.yml`，用 snakeyaml 展平 `application*.yml`，断言 ①引用的 `bone.*` 键必须已定义 ②`bone.blueprint.schedule.*` 定义键必须被引用（防死配置）。**只做单向全量**：其余 `bone.*` 由 `@ConfigurationProperties` binder 消费，反向断言全是假阳性。源码不可见时 `Assumptions` 跳过。
- 三 Job 的 cron/门限已配置化（`bone.blueprint.schedule.*`，见 `application.yml`），`dev/prod/mq` 三档未覆盖。

## 9. 其它
- **`.gitignore` 通用规则会吞源码**（曾致 P0，`3273172a` 修）：`log/` 匹配任意层级吞掉 `...domain.model.log`；`*.properties` 吞测试资源。**靠后者优先**，修法是在致害规则**之后**加窄豁免。排查 `git ls-files --others --ignored --exclude-standard | grep -E "/src/(main|test)/"` + `git check-ignore -v`。
- `.workbuddy/` 在 `.gitignore`，但 `memory/MEMORY.md` 已被 `b9285821` 跟踪（按目录显式 `git add`）。
- 前端：设计系统 `@bone/ui`（`bone-frontend/packages/ui`），主色 `#1677FF`，14px，PingFang SC，CSS 变量 `--bone-color-*`。
- ⚠ `.claude/agents/skills/backend-java.md` **严重过期**（仍在教 MyBatis Mapper + `application.dto`），与「唯一 bone-metadata-sdk」冲突，喂 AI 的文件，优先处理。
