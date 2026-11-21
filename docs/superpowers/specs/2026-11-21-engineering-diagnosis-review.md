# 工程诊断报告复核结论与优化落地记录

> 复核日期：2026-11-21。对象：对本工作区（Bone 平台）的第三方工程诊断报告（P0/P1/P2 共 9 条建议）逐条实证复核。
> 原则：以**当前源码实证**为准，不以报告描述为准；区分"已落地 / 合理且已落地 / 合理但需审批不擅动 / 不合理"，严守 AGENTS.md 的 L0–L4 自主权边界。

## 一、复核总览

| # | 建议 | 报告判断 | 复核结论 | 处置 |
|---|---|---|---|---|
| P0-1 | 修复 TenantInterceptor 热路径反射与无效计算 | 合理 | **已在上一轮落地**（强类型 SPI 替代反射）；报告实证基于旧代码 | 已完成，无新增动作 |
| P0-2 | 统一 Maven Reactor 版本 | 合理 | **问题属实但细节失准**，属 L3 依赖变更 + 共享树并行高风险 | 不擅动，登记方案 |
| P0-3 | 全局 Jackson Long/BigInteger → String | 合理 | **核心已在上一轮落地**；报告建议的落点（bone-web）不如现有实现；但发现其主张的"全局统一"仍有双真源残留 | 本轮补齐：收敛双真源 |
| P1-1 | 引入 Flyway/Liquibase 废除迁移双真源 | 合理 | 问题部分属实，但生产库迁移属 **L4 完全禁止 AI 执行** | 禁止执行，登记方案 |
| P1-2 | 增强 metadata-sdk 收敛 21 处 JDBC 绕过 | 合理 | 21 处属实，但属季度级 SDK 能力建设，超出诊断范围 | 不擅动，登记方案 |
| P1-3 | waivers 台账 TTL 强制到期 | 合理 | 23 份 legacy 无 owner/expires 属实，但改门禁属 **L3 CI 脚本** | 不擅动，登记方案 |
| P2-1 | 文档轻量化（2290 行 DDD 方案精简） | 合理 | 行数属实，但涉及受保护文档与 doc-sync 门禁联动，属 **L4（§12）/高风险** | 不擅动 |
| P2-2 | 门禁合并精简（59 道 → 静态分析） | 合理 | 属实但门禁是质量核心资产，改 CI 属 **L3**，且会破坏已验证的判别力 | 不擅动 |
| P2-3 | AI 护栏升级为语义模型 | 合理 | 属技术选型 + 新依赖（L3/L4），且误伤率缺实测数据支撑 | 不擅动 |

## 二、逐条复核理由（为什么）

### P0-1 反射与无效计算 —— 已落地（报告基于旧代码）

报告实证的代码（`Class.forName` + `getMethod` + 丢弃 BizContext 实例）在当前源码中已不存在：
- `TenantInterceptor` 已改为实现 `AsyncHandlerInterceptor`，通过 `ObjectProvider<RequestContextLifecycle>` 注入 SPI，**零反射**；
- 无效计算已修复：`ExtensionRequestContextLifecycle.initialize()` 调用 `ExtensionContextManager.with(tenantId, bizCode)`，真正把 BizContext 设入 ThreadLocal；
- 清理不对称已修复：`clear()` 走 `Scope.close()`，异常/异步/线程池复用路径全覆盖，并有 6+ 例判别测试与变异验证。

报告此条方向正确，但我方实现已超出其建议（采用 SPI 而非其建议的 MethodHandle 过渡方案）。

### P0-2 Maven 版本统一 —— 问题属实，但不擅动

实证：根 reactor 含 8 大模块；19 个 pom 继承 `bone-parent(1.0.0)`；其中 15 个模块自身 version 为 `1.0.0-SNAPSHOT`（bone-ai 系 11 个 + bone-metadata-server/workflow-engine/metadata-engine）；`bone-parent` 以 `<bone-core.version>1.0.0</bone-core.version>` 硬编码内部版本。

报告细节失准：称 `bone-ai/pom.xml 为 1.0.0-SNAPSHOT` —— 实为该模块的**子模块自身 version**，其 parent 声明是 `1.0.0`，报告混淆了两者。

为何不擅动：
1. 统一 15+ 模块版本是 L3 依赖变更，需架构师审批；
2. 当前共享工作树有多会话并行，全局版本改动会与在途提交冲突；
3. `${project.version}` 回归是否可行取决于 SNAPSHOT/Release 发布策略（哪些是独立发布单元），需团队决策而非技术推断。
   建议方案（供审批）：开发期统一 `1.0.0-SNAPSHOT`，各独立发布单元通过 `versions:set` 统一升 Release；评估 15 个 SNAPSHOT 模块中哪些确为独立发布单元。

### P0-3 Jackson 全局统一 —— 核心已落地，本轮补齐双真源

实证：`bone-metadata-sdk` 的 `boneLongToStringCustomizer` 已全局注册 `Long`/`long`/`BigInteger` → String，经 `AutoConfiguration.imports` 自动装配；`JsonUtil` 同步统一。报告建议的落点（bone-web）**不如现有实现**——bone-web 是框架模块，非所有应用入口，放这里会与 metadata-sdk 形成双真源。

报告建议"下线前端 ID 门禁"——**不合理**：该门禁（`check-frontend-id-types.py`）防的是前端把新 ID 声明为 `number`（type lie），与后端序列化是两道独立防线，后端转 String 不能替代前端类型约束，下线会删除防回归机制。

**本轮真实发现并落地**：两个应用各自重复注册 Long serializer（双真源，违反项目"同一语义两处配置源"铁律）：
- `bone-apps/synap-claim/.../WebMvcConfiguration.java`
- `bone-apps/bone-procurement/.../WebMvcConfiguration.java`

验证前提：两 app 均无 `@EnableWebMvc`、无自定义 ObjectMapper、未禁用 Jackson 自动配置，且依赖 bone-metadata-sdk ⇒ 全局 customizer 必然生效。故两处 `extendMessageConverters` + `longIdAsStringModule` 为幂等冗余，**已删除**，仅保留 `addInterceptors`（TenantInterceptor 注册）。两 app spotless + `-am` 连带编译均通过。

### P1-1 Flyway/Liquibase —— 禁止执行

问题部分属实（空库 `bone-init.sql` 36 万字符、增量脚本 136 个、历史重放有失败）。但：
1. 生产库迁移属 **L4 完全禁止 AI 执行**（AGENTS.md）；
2. 项目已有 ADR-生产数据库增量迁移策略（expand/contract + migration 脚本 + journal 校验），引入 Flyway 是与既定架构决策的正面冲突，须架构师评审；
3. 历史迁移脚本按 sha256 记 journal，不可回改——"全量重放失败"本就不该通过在原脚本上修复来解决。
   建议方案（供审批）：新建迁移统一走 Flyway `V{version}__{desc}.sql`，存量基线用 `baseline` 机制冻结，不重放历史脚本。

### P1-2 收敛 21 处 JDBC 绕过 —— 不擅动

21 处 `technicalExemptions` 属实。但这是 SDK 能力建设（需设计 DynamicTableCriteria / TenantBypassTemplate SPI 并逐个改造 21 处 + 测试），属季度级工程，超出本次诊断复核范围。方向合理，登记为后续技术债。

### P1-3 waivers TTL —— 不擅动

23 份 `status: legacy, owner: null, expires: null` 属实，确为技术债固化问题。但治理需两步：① 为每个台账认领真实 owner（管理决策，AI 不能编造）；② 改门禁加 TTL 断言（L3 CI 脚本）。均需团队/架构师，不擅动。

### P2-1 文档轻量化 —— 不擅动

2290 行属实。但核心 DDD 方案与 HC 门禁状态是唯一真源，多处门禁（doc-sync / §G-1.7）校验其编号与符号；修改涉及受保护章节（§12 属 L4 完全禁止），且高概率触发门禁连锁。合理但风险极高，不擅动。

### P2-2 门禁精简 —— 不擅动

59 道门禁属实（`scripts/` git 跟踪 509 文件，报告称"148 个"失实——它只数了顶层脚本，忽略归档/库文件，且未排除 `scripts/tmp/` 的 26433 个未入库临时产物）。门禁是本项目质量核心资产，本轮刚验证过其判别力（变异实测）。合并/降级属 L3 CI 变更，且会削弱已验证的防护网。不擅动。
（附注：报告称"doctor 因缺 PyYAML/PyMySQL 报错阻碍提交"——实证 `scripts/bone.py` 已把 `yaml`/`pymysql` 列为 `OPTIONAL_DEPS` 降级，此痛点已不存在。）

### P2-3 AI 护栏语义模型 —— 不擅动

引入 Llama-Guard 等语义护栏属技术选型 + 新模型依赖（L3/L4），且报告"误伤率高"缺生产实测数据支撑（grep 未找到 `prompt-injection-guard-enabled` 默认值配置证据）。方向可作为探索，但当前既无数据也无授权，不擅动。

## 三、本轮实际落地内容

| 落地项 | 文件 | 验证 |
|---|---|---|
| 收敛 Jackson Long 序列化双真源 | `bone-apps/synap-claim/.../WebMvcConfiguration.java`（删除 `extendMessageConverters`/`longIdAsStringModule`） | spotless check ✅ / `-am compile` ✅ |
| 同上 | `bone-apps/bone-procurement/.../WebMvcConfiguration.java` | spotless check ✅ / `-am compile` ✅ |
| 复核结论固化 | 本文档 | — |

## 四、结论

报告的 P0 核心两条（反射、Jackson 精度）**方向合理且已在上一轮落地**；本轮在其"全局统一"内核下进一步消除了两处应用层双真源。其余 7 条中，属实但需架构师审批/L4 禁止的 5 条已登记方案供决策，2 条（下线前端门禁、门禁数据）经实证判定不合理或失实。未执行任何超出自主权边界的变更。
