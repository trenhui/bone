
## Bone Frontend UI 规范 (2026-06-10)
- 设计系统位于 `bone-frontend/packages/ui`（包名 `@bone/ui`）
- 主色已对齐 AntD 5 官方蓝 `#1677FF`，旧版 sky-500 (#0ea5e9) 已废弃
- 基础字号 14px（后台标准），字体族优先 PingFang SC
- CSS 变量命名规范：`--bone-color-*`，旧版 `--primary` 等保留为兼容别名
- 4 类核心组件规范（按钮/表单/表格/弹窗）已整理为可视化文档

## 提交与 CI 纪律（2026-09-17）
- **类型改名/移动必须一个提交落地**：路径（`git mv`）+ 符号声明 + 全部 import/引用 + 单测。只暂存 `git mv` 会被后续任何 `git commit` 连索引扫走，产出「新路径 + 旧符号」的编译中断提交（本仓库已发生过一次）。
- **提交前用索引树自证**：`TREE=$(git write-tree) && git grep -lE "<旧符号>" $TREE -- <模块>` 必须为空。验证提交是否健康，须 `git worktree add --detach <sha>` 检出后再跑构建，**不要用工作树**（含他人在途改动）。
- **`scripts/check.sh` 的 ArchUnit 门禁已修复**（2026-09-17）：原第 26 行 `grep ... || true | sed ...` 因管道优先级高于 `||` 被解析为 `(grep) || (true | sed)`，`MODULE_PATHS` 拿到文件全路径 → `[ -f "$POM" ]` 恒假 → ArchUnit 静默跳过；现已把 `|| true` 移到管道末尾。**修好前的历史提交都没真正过过 ArchUnit。**
- **pre-commit 的 Spotless 是全反应堆级别**：任何人在途的格式违规都会阻塞**别人**的提交（实测被 `CloseExpiredPaymentCommandHandler.java` 第 22 行 `@NoDomainEvent  //` 双空格拦下 13 分钟）。提交前先跑 `mvn -o spotless:check`。
- **bash 管道 + `||` 的陷阱**：写 `a | b || true` 要放在管道末尾，否则语义变成 `a || (b)`。
- **docs-compliance CI 是精确比对**：`scripts/ci/collect-all-compliance.sh --check` 只比对 `compliance.json` 的 `as_is` + `backlog`（不看 `generated_at`）。改 `tools/*-compliance-collector/collect.py` 必须同提交 `doc/_generated/<module>/` 生成物；且生成物引用的文件必须已被 git 跟踪，否则 CI 在该提交上重算必然对不上。
- **判定生成物能否安全提交（免 worktree 法）**：逐条把探针 pattern 分别 `git grep -lE <pat> HEAD -- <root>` 与 `git grep -lE <pat> -- <root>`，命中集合相等 ⇒ 内容改动不影响证据；再确认该模块无新增/删除/改名源文件。
- **docs-compliance CI 在 HEAD 上曾长期为红**：2026-09-17 实测 blueprint 生成物停在 2026-06-03、console 停在 05-28、extension 停在 05-27、metadata 停在 09-11，与现场重算全部不符。已由 `40fae31d`（blueprint）与 `f412f3d0`（console/extension/metadata）追赶，8 个模块全部通过 `--check`。**改任何模块代码/文档后都要跑对应采集器**，否则 CI 立刻变红。
- **`--sync-doc` 幂等性必须自证**：CI 的详设块校验等价于「`--sync-doc` 后文件字节不变」，用 sha256 前后比对验证。extension / metadata 两处已验。
- **判管道退出码禁用 `if cmd | tail`**：退出码来自 `tail`（恒 0），会把失败当成功。用 `PIPESTATUS[0]` 或重定向到文件后取 `$?`。
- **Gitee SSH 会抖动**：`kex_exchange_identification: read: Connection reset by peer`（180.76.x:22）时重试即可成功，推送脚本应带重试。GitHub 与 Gitee 两个 pushurl 是**顺序**推送，前一个失败不影响后一个已成功的那个。
- **共享工作树有并发 Agent**：本仓库出现过连续 `reset HEAD~1` 摘掉他人提交的情况。动手改分支前先看 `git reflog`；提交用 `git commit --only <paths>`（pathspec 模式）避免卷入他人暂存内容。
- **`.gitignore` 通用规则会吞掉源码（已致 P0，2026-09-17 修复于 `3273172a`）**：`log/` 匹配任意层级同名目录 → Java 包 `com.bone.system.domain.model.log` 从未入库，而引用它的类已提交 ⇒ 干净检出编译必断（本地因文件在磁盘上而全绿）。同类 `*.properties` 吞 `src/test/resources/application.properties`。**注意 `.gitignore` 靠后者优先**：文件开头的 `!**/src/main/**` 会被后面靠后的 `log/` 覆盖。排查：`git ls-files --others --ignored --exclude-standard | grep -E "/src/(main|test)/"` + `git check-ignore -v <文件>`。修法：在致害规则**之后**追加窄豁免。
- **`release/mvp-v1.0` 上两个模块的架构门禁本来就是红的（2026-09-17 实测，与任何单次改动无关）**：bone-masterdata（`domain_no_query_builder` 12 次 + `command_no_query_builder` 14 次）、bone-iam（E-9.3 读侧 DSL ×4 + E-4.4 直调 `TenantContext` ×2）。成因是 `777cb395`（09-11）给 `Criteria` 加 `@ReadSideOnly` 使既有用法瞬间违规却未清理/冻结。
- **`ci.yml` 只跑 `main`/`master`/`develop`，不跑 `release/mvp-v1.0`** ⇒ 后端测试与架构门禁在这条分支上**从不执行**，红灯不会暴露；而本地 pre-commit 钩子（`scripts/check.sh`，2026-09-17 修复后真跑 ArchUnit）会对变更模块执行 —— **一旦你改动某模块，该模块的既有红灯就会拦住你的提交**。
- **ArchUnit 的 `FreezingArchRule` 会写 `archunit_store/`**：跑模块测试会更新基线（`stored.rules` + UUID 文件）⇒ 在**主工作树**跑测试会污染并发方的在途基线文件。**验证类构建应放独立 worktree**。
- **基线政策**（`bone-framework/bone-architecture-test/README.md` L52/L61 + ADR-0027）：`archunit_store/` 入库；**基线只可收缩、禁止扩张**；存量违规按 ADR-0027「freeze 基线接受 + 登记（如 LYR-03），修复后收缩」处理；`allowStoreUpdate=true` 只允许收缩，`refreeze=true` 慎用。
- **README 明文指定「不 freeze」的两条规则**（`bone-architecture-test/README.md`「建议 freeze 策略」）：`domainMustNotDependOnOuterLayers` / **QueryBuilder 禁令（P0-5 `domainMustNotUseQueryBuilder`、P0-6 `commandHandlersMustNotUseQueryBuilder`）**，以及 `domainRepositoriesShouldOnlyDeclareWhitelistedMethods`（理由写明「空仓储 / **ReadPort 拆分**后应 0 违规」）。⇒ 对这两类规则做 `freeze + 登记` 属**违背项目书面策略**，正解是 ReadPort 拆分。**但现状有例外**：masterdata 的 P0-6 已被 freeze（store `9574f461` 为 0 行＝零容忍）；iam 的 E-9.3 则登记了 **137 行**债务（`720d8a73`）＋ E-4.4 **12 行**（`80242b05`）——即 iam 靠「登记后逐步收敛」双轨变绿。
- **SDK 基接口把读侧 DSL 焊进了写侧仓储（P0-5/P0-6 的架构根因）**：`com.bone.metadata.sdk.Repository<T,ID>` **自身**声明了 `findByCriteria / findOneByCriteria / countByCriteria / deleteByCriteria / updateByCriteria / aggregate(...)`，全部依赖 `@ReadSideOnly` 的 `Criteria`。全平台 **34 个 `domain.repository` 接口都是空接口**（0 个自定义方法），SDK **不支持派生查询/动态代理**（`BaseRepository` 是具体抽象类，自定义 finder 必须自己 `extends` 它）。⇒ **任何在 `domain` 或 `application.command.handler` 里发起的 SDK 查询都必然撞 P0-5/P0-6**，与作者是否谨慎无关。**不要在 domain / command handler 里做查询**：把查询下沉到 `application/query/port`（新端口）或 `infrastructure/query`（实现）后，再判重/编排。参照物：`bone-system` 的 command handler **0 处** `findBy|countBy|existsBy` 故全绿；`MasterDataRecordService`（只 `findById`+`save`）是领域服务正确范式。
- **pre-commit 有熔断器，别拿它试错**：`.git/hooks/pre-commit` → `./scripts/check.sh`；连续失败 **3** 次即写 `.git/hooks/.check-fail-count`，达 3 后**阻断全部提交**（需 `rm` 该文件才恢复）。已知会红的模块（如 masterdata 的既有 P0-5/P0-6）**在修复前不要反复试提交**。另注意 `scripts/check.sh` 的 `[1/5] mvn spotless:check` 是**全反应堆**扫描，任何人在途的格式违规都会阻塞你的提交；`[2/5]` 只对**变更模块**跑 `*ArchitectureTest`。
- **GitHub Actions 触发面容易判错**：`ci.yml` 只跑 `main`/`master`/`develop`，但多个工作流用 `on.push.paths` **且不带分支过滤** ⇒ 在 `release/*` 上推送也会触发。例：`iam-gateway.yml` 监听 `bone-platform/bone-iam/**` + `doc/architecture/openapi/iam-v1.yaml`；`docs-compliance.yml` 监听 `doc/_generated/**` + `tools/**/*-compliance-collector/**`。**改某模块源码时顺带重算 `doc/_generated/<module>/` 是必须的**（否则该分支 CI 变红）。本机无 `gh` 且沙箱网络受限，CI 结论需用户本机核对。

## 规范权威性与已知冲突（2026-09-17）
- **E-13 命名约定默认是 Advisory**，不阻断 CI。原文（`doc/architecture/Bone-DDD-最终实践方案.md` E-13 章节导语）：「命名属于团队工程一致性，不属于 DDD 原则；默认是 Advisory。模块可以为一致性将其升级为阻断规则，但不得宣称后缀能证明 DDD 语义。」→ 报告命名问题时**不得**说成 CI 违规。
- **⚠ 两份权威文档在 adapter DTO 目录上曾互斥（2026-09-17 已裁定：以 DDD 规范 E-13.1 为准）**：
  - 裁定结果 → 标准形态为 `adapter/web/dto/request/` + `dto/response/`；`AGENTS.md` §5.1 已改为此写法，iam/masterdata/system 共 64 个文件已迁（F2 改集，待提交）。
  - 原冲突：`Bone-DDD-最终实践方案.md` E-13.1 = `request/response`（旧代码 `req` 47 / `resp` 17 随 AGENTS.md；`request`/`response` 仅 blueprint 5+5）。
  - 后续新增模块一律用 `request/` + `response/`，不要再引入 `req/`、`resp/`。
- **`domain/gateway/*ReadPort` 是已登记存量**（`Legacy-E-9.5 读侧端口`，增量迁移），不是违规；新读侧端口一律 `application/query/port` + `*QueryPort`。
- **E-10.3 认可 `domain/{aggregate}` 平铺形态**（事件放 `{aggregate}/event`），与 `domain/model/{...}` 角色目录形态并存时属模块内不一致（bone-system 现状：`domain/alert/` 与 `domain/model/alert/` 并存）。
- **E-13.4 点名 `AlertEvent` 应演进为 `AlertRecord`**（bone-system 的 `AlertEvent` 实为 `AggregateRoot`，映射 `sys_alert_event`，自身发 `AlertResolvedEvent`；职责本就是"记录"）。该条**未登记**在任何 Legacy 表，属规范点名但无跟踪。
- **`*Store` / `*MockRowMapper` / `*DtoMapper` 多为假阳性**：E-13.3 禁的是「以 `*Store`/`*Dao`/`*Mapper` 替代仓储」，`*IdempotencyStore`（技术端口）、JDBC `RowMapper`、query 侧 DTO 装配器均不适用。规范中 `Assembler` 出现 0 次，故 `*Assembler` 也不是规范用语。

## SDK 能力真源（评估规范可落地性时必须先看这三处）
- **`BaseRepository`**：`save(T)` 返回 `ID`（不是 void）、`insert(T)` 返回 `ID`、`update(T)` 返回 `boolean`、`updateByCriteria(T, Criteria)` 返回 `int`；**无聚合级联落库**（`OrderRepository.save(order)` 不会持久化 `order.items`，集合须标 `@Transient`）；**写路径不消费 `@Version`**（`update`/`save` 更新分支只生成 `WHERE pk = ?`，不加版本条件、不自增）⇒ 声明 `version` 列不产生并发保护。
- **`bone-core` 事件机制**：`DomainEventPublisher.publishFrom(aggregate)`（default 实现＝发布后清空）；豁免用 `com.bone.core.annotation.NoDomainEvent`，**类级**豁免。
- **`BoneDddArchRules`**：规则名与其判定粒度（如 `applicationSaveMustPairWithPublishOrExempt` 是类级——类里有一处 `publishFrom` 就放过全部 `save`；`domainRepositoriesShouldOnlyDeclareWhitelistedMethods` 只看返回类型 ∈ {聚合, `Optional<聚合>`, `boolean`, `void}`）。
- **三条已登记 SDK 阻塞**（`doc/architecture/Bone-Metadata-SDK-能力需求.md`）：① `@Version` 乐观锁（P0，E-5.3/CORE-07）；② 聚合级联落库（P1，CORE-11）；③ 强类型 ID 值对象（P2，E-7.1）。
- **`bone-blueprint` = L3 参考实现，当前 `D1 + Shared`**（`@Table` 直接落在 `Order`/`Payment` 上），并含 `OrderItemRepository`（子实体级仓储）——均已登记为技术债（债主是 SDK），**不是违规，也不是可照抄的范式**；L2/L3 默认仍是 `D0 + Separated`。
- **`ci.yml` 的 `backend-quality` job 确实调用**：`mvn spotless:check`、`mvn verify`（含 ArchUnit）、`jacoco:check`（门槛 `jacoco.minimum.coverage`，实测 10%）、`check-ddd-doc-code-sync.py --strict`、`check-ddd-doc-drift.py`。gitleaks 与 DDL 检查**不在 CI**（仅本地 `scripts/check.sh`/`scan-secrets.sh`）。

## DDD 规范文档的三道检查与状态真源（2026-09-17 建立）
- **三道本地/CI 检查**：`scripts/check-ddd-doc-drift.py`（v4 编号 / 平台 API / 相对链接 / 内部锚点）、`scripts/ci/check-ddd-doc-code-sync.py --strict`（文档符号 ↔ 代码真实性）、`scripts/check-ddd-gate-state.py`（**门禁状态 == 实现真源**，含 WARN 与 `--metrics`）。改文档后**三个都要跑**。
- **HC 状态唯一真源是 `G-1.7 HC 硬约束与实测状态`（锚点 `#hc-hard-constraints`）**；`G-1.1` 只留"五条不得宣称已 CI 阻断"的单向指针。**HC-003/006/008 = Planned**（无机器载体）；**HC-001/004 = Manual**（`scripts/check.sh` / `scripts/ci-check.sh` 本地拦截，无 workflow 调用）；HC-005 = Active 但**父 POM** 门槛是 **10%**（不是 70%），且 4 个模块下调（`bone-metadata-engine-{domain,ports,starter}=0`、`-runtime=0.04`）。
- **门禁状态的判据是双向的**（G-1.7 表下）：① 升 Active 必须真有 CI/规则库载体；② **本地脚本已落地的拦截不得写成 Planned**（HC-001 曾如此——把"已有 pre-commit 拦截"读成"完全没有"）；③ **被点名的载体必须真的做这件事**（HC-008 曾把它当作"必备字段"载体，而那个脚本只比对表名清单、不读列）。② 与"只有本地脚本却称 CI 阻断"互为反向约束，两条都在 `Manual` 中间态才稳定；③ 只能人工复核。
- **`scripts/check-ddd-gate-state.py` 已接入 CI**（`ci.yml` `backend-quality` 的 `DDD Gate State Lint (blocking)`，2026-09-17 架构师确认；G-1.1 第 15 条声明它）。**未**接入 `scripts/check.sh`——熔断器按脚本计失败次数，不宜扩大 pre-commit 失败面。
- **`AGENTS.md` 已于 2026-09-17 收敛为薄引用**（35 行 / 537 字），并完成拆分：正文在 **`doc/agents/`** 七份（README 索引 + 01 概览与模块结构 / 02 构建运行与部署 / 03 架构分层规范 / 04 测试与代码质量 / 05 数据库与安全 / 06 AI 协作与编码准则）。原单文件 581 行版备份于 `doc/archive/AGENTS-单文件版-2026-09-17.md`。
  - **拆分文件沿用原章节编号**（`## 5.`、`### 5.2`），历史 `§5.2` / `§11.12` 引用按编号仍可定位 → 不要再做 §-引用全仓替换。
  - **判据是「加载时机」而非主题**：改 domain 读 03、改基础设施读 05、任何任务读 06。
  - **§12.1 只留指向 `Bone-DDD-最终实践方案.md#hc-hard-constraints` 的指针**（原 8 行 HC 表已删）；§12.7 三处错标（ArchUnit 载体 / 本地脚本当 CI 载体 / `check-ddl-doc-sync.py` 当必备字段载体）已修。`check-ddd-gate-state.py` 的 AGENTS 反查现为 **WARN 0 条**（收敛前 2 条）。
  - **入口文件一律不得复制约束或门禁状态**：`.github/copilot-instructions.md` 已同步改为薄引用（原文复制的 4 条里 "coverage >= 70%" 是错值）。`CLAUDE.md` / `.cursorrules` 保持 `@AGENTS.md` / `Read AGENTS.md first.`。
- **既有断链（未修，仅登记）**：`doc/CODE_WIKI.md` 全篇 25 条相对链接按「仓库根」写但文件在 `doc/`，全部断链；`doc/architecture/bone-前端架构.md:168` 的 `bone-frontend/START_GUIDE.md` 不存在（只有 `SCRIPT_USAGE.md`）。
- **不要新建独立摘要/导读文件**：主规范**已有**「一页纸速览 + 按角色阅读 + 反模式速查 + 提交前自检」；ADR-0026 是单文档真源，另起副本即新增漂移源。（评审常提的 `Bone-DDD-融合终极版-v9.0.md` **不存在**。）**`.github/PULL_REQUEST_TEMPLATE.md` 已于 2026-09-17 新建**（本地自检命令 + G-4 交付验收四组 + 生成器空目录勾选 + 门禁状态纪律：改 HC 状态必须同 PR 收敛 AGENTS.md 副本）。
- **领域事件判据已统一为两层**（E-5.4 ↔ E-5.2 ↔ glossary 一致）：① 领域层 = 是不是业务语言里的**事实**（**与订阅者无关**，禁止拿"当前没有下游"当豁免理由）；② 发布层 = 是否 `publishFrom()` / 是否 Durable。不要再写成"跨上下文需要感知"。
- **CORE 表有「类别」列**：CORE-01～08 = 通用 DDD，CORE-09～12 = Bone 工程约束。**CORE 编号禁止重排**（全仓 10+ 处引用）。
- **实施状态指标口径**（G-1.8，数字不入库，用 `check-ddd-gate-state.py --metrics` 现算）：`git ls-files` 索引内 **`src/main/java`** 下的 `.java` 按后缀计数，**必须排除测试夹具**（`bone-architecture-test` fixture 有 `SubmitOrderUseCase`，会让 `*UseCase` 计数虚增）。实测：CommandHandler **58**、QueryHandler **64**、ApplicationService **3**（全在 blueprint）、UseCase **0**、`domain/repository` 接口 **48**。

## 提交与门禁的运维事实（2026-09-17 实测补充）
- **`git commit` 一律后台跑**：pre-commit → `scripts/check.sh` 的 `mvn spotless:check`（**不带 `-o`**）本机实测 **~14 分钟**；前台 120s 会被 SIGTERM 杀掉（索引不丢、熔断不计数，重跑即可）。同一命令加 `-o` 只要 1s——差异纯属联网解析。
- **别把 `mvn -o spotless:check` 的 1 秒成功当证据**：它跑在根聚合 POM 上，几乎不校验子模块。判断"格式是否真的过"要看 pre-commit 的完整输出（重定向到文件，别用 `| tail`，否则拿不到进度与结尾）。
- **`collect-all-compliance.sh --check` 约 15 分钟**：必须后台跑，前台必被杀。2026-09-17 全 8 模块 up to date。
- **`.workbuddy/` 在 `.gitignore` 里，但 `memory/MEMORY.md` 已被历史提交跟踪**（`b9285821`）：`git status` 会显示它为 `M`，而 `git add -A` 会对 `.workbuddy` 报 ignored 告警。日常提交按目录显式 `git add` 即可，不要 `-A`。
- **AGENTS.md 于 2026-09-17 已拆分**（commit `53563456`）：入口 35 行薄引用 + `doc/agents/` 六册（沿用原 §编号）+ `doc/archive/AGENTS-单文件版-2026-09-17.md` 备份。**改 AI 协作规则要改 `doc/agents/`，不要往 `AGENTS.md` 里抄正文**；HC 状态真源只有 G-1.7 一处（`scripts/check-ddd-gate-state.py` 会告警副本）。
