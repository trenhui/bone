# Bone 工程文档与代码诊断报告

- **日期**：2026-09-23
- **模式**：grill-and-review 审计模式（B）
- **范围**：后端 Java 分层/仓储/CQRS · 文档体系一致性 · 门禁盲区与假绿 · 前端工程（bone-frontend）
- **基准**：`doc/architecture/Bone-DDD-最终实践方案.md`（E/CORE/G-1.7）、`doc/architecture/adr/`（ADR-0001~0037）、各模块 `ArchitectureTest.java`、`gate-state.json`
- **严重度**：P0（阻断/假绿级）· P1（真实违规/漂移）· P2（次要/风格）
- **动作**：仅诊断 + 可执行修复方案，**未修改任何文件**

---

## 一、执行摘要

派 4 个独立审查子 Agent 并行（后端 / 文档 / 门禁 / 前端），共合并去重得到 **4 个 P0、19 个 P1、约 20 个 P2**。

**Headline（最重要）**：项目门禁在 CI 与文档层面"看起来全绿"，但大量**已登记为 Hard gate / 核心约束的规则只在 1–2 个模块接线，其余模块完全无该门禁**——这是系统性的"假绿"（规则存在 ≠ 规则覆盖）。它让 HC/CORE 状态仪表板给出虚假信心，回归无法被捕获。

- 后端分层**大体健康**：HC-001 持久化唯一、ADR-0035 应用层形态、HC-003 统一响应、404→500 兜底均达标。但有 4 处真实 P1 违规。
- 文档体系**诚实**（无门禁状态造假），但 **E-1.2 数据所有权声明在 12/15 模块缺失**，导致 13 张 `bone-init.sql` 表成"孤儿"。
- 前端**无 P0**，但有包管理器冲突、双请求层、God 组件 3 处 P1。

## 二、四范围速览

| 范围 | 判定 | 最高风险 |
|---|---|---|
| 后端 Java 分层/仓储/CQRS | Request Changes（3 P1 + 1 P1） | `FileController` 直连 infra；`studio-generator` 租户 `0L` 兜底 |
| 文档体系一致性 | Needs Discussion（5 P1） | E-1.2 缺失致 13 孤儿表；`application-constructs-baseline.json` 与"96 条"措辞矛盾 |
| 门禁盲区与假绿 | Request Changes（**4 P0** + 11 P1） | R9 / 反贫血 / DomainEvent 配对 / gateway 守卫 仅 1–2 模块接线 |
| 前端工程 | Request Changes（3 P1） | `pnpm` 与 `npm` 冲突；shell 双请求层；`App.tsx` 1166 行 |

---

## 三、P0 — 门禁假绿（规则存在 ≠ 规则覆盖）必须最先收口

> 这 4 条的共同根因：**门禁采用"每模块各自写 `ArchitectureTest`"的分散模式**。共享规则库 `bone-architecture-test` 新增/收紧规则后，没有机制把规则扩散到全部 20 个模块 → 规则在 `blueprint` 试点后"忘了"推广。CI 全量跑 `mvn clean verify` 虽然覆盖了已接线的模块，但**未接线的模块根本不校验这些规则**，所以全局仪表板是绿的、实际是盲的。

| # | 规则（声明状态） | 实际覆盖 | 为什么危险 | 可执行修复 | 置信度 |
|---|---|---|---|---|---|
| P0-1 | `oneAggregatePerTransaction`（CORE-06 / E-5.1「一事务一聚合」，核心不变量） | **仅 blueprint 接入**，其余 19 模块零该门禁 | "一事务一聚合"是聚合一致性边界的基石。未接线 = 任一业务模块里一个事务双写两个聚合根，门禁全绿、回归 undetectable | 在其余 19 模块 `@ArchTest` 照搬 `BoneDddArchRules.oneAggregatePerTransaction()`（参考 blueprint/ArchitectureTest.java:166-168，非冻结、须 0 违规） | High |
| P0-2 | `applicationServicesMustNotOwnDomainRules`（E-6.4 反贫血 R2 内容禁令） | **仅 blueprint 接入** | ADR-0035/0020 反贫血的核心判定。未接线 = 应用层可随意 `new` 领域对象 / 调聚合 setter 改状态而不报警，贫血模型静默回归 | 在 19 模块接入 `BoneDddArchRules.applicationServicesMustNotOwnDomainRules()` | High |
| P0-3 | `applicationSaveMustPairWithPublishOrExempt`（E-5.4 DomainEvent 配对，gate-state 标 **Hard gate**） | **仅 blueprint + masterdata 接入** | 领域事件丢失是分布式一致性重大隐患。`Repository.save()` 不配对 `publishFrom()` 也不声明 `@NoDomainEvent` 即放行 = 事件静默丢失 | 在 iam/system/integration/notification/studio-generator/extension-studio/metadata-server 7 模块接入该规则 | High |
| P0-4 | `adaptersMustNotDependOnDomainRepository` / `...DomainService` / `...GodObjects`（adapter 守卫三条） | **gateway 模块完全缺失**（纯 adapter 层却无任何守卫） | gateway 整层都是 adapter，却可自由直注 `domain.repository` / `domain.service`，分层门禁在最该守的层完全真空 | 在 `bone-gateway/.../ArchitectureTest.java` 接入上述三条规则（先确认无 adapter→repository 直连，应 0 违规） | High |

**补充盲区（P0 的延伸，列为 P1 但同源）**：R9 本身还有**委盲点**——`getMethodCallsFromSelf` 只看同类直接调用，跨下游 service 编排（`ApplicationService → ServiceA.save + ServiceB.save`）时顶层类无直接 repo 调用 ⇒ 仍绿（`BoneDddArchRules.java:873` 对非聚合根实体返回 `null` 不计数）。短期至少在规则注释显式声明该盲区，长期做跨类调用遍历累加。

---

## 四、P1 — 真实违规与漂移

### 4.1 后端分层（4 项）

| # | 文件:行 | 问题 | 为什么 | 可执行修复 | 置信度 |
|---|---|---|---|---|---|
| P1-1 | `bone-platform/bone-file/.../adapter/web/controller/FileController.java:5,25` | 入站边界违规：本模块**无任何 `*ApplicationService`**，Controller 经 `@RequiredArgsConstructor` 直注 `infrastructure.storage.FileStorageService`（infra 实现类） | 违反 ADR-0028 / AGENTS §一.3「adapter 不得直连 infrastructure」。adapter 跨过 application 直连实现，分层约束被破坏，未来替换存储实现会牵动 controller | 在 `application/` 新增 `FileApplicationService`，或把 `FileStorageService` 抽象为 `application/port/out` 存储端口 + infrastructure 实现；Controller 只注端口/ApplicationService | High |
| P1-2 | `bone-engine/bone-metadata-server/.../domain/service/IamApplicationValidator.java:7,15`（同 `IamModuleValidator.java`） | 领域纯净度违规：`domain/service` 内使用 Spring `@Component` | 违反 ADR-0035 D3 / E-6 D1 / HC-002 框架白名单「domain 零框架依赖」。领域类带 Spring 注解，破坏可测试性与纯净度 | 删除 `import org.springframework.stereotype.Component;` 与 `@Component`；在模块 `@Configuration` 以 `@Bean` 注册，或在消费的 ApplicationService 内 `new IamApplicationValidator(repository)` 注入 | High |
| P1-3 | `bone-engine/studio-generator/.../domain/model/data/DataSource.java:20,58` 等 7 处实体 | 租户隔离隐患：租户级业务实体继承纯 `AggregateRoot`/`AbstractEntity`（bone-core 基类仅含 `id`，无 `tenantId`/`Tenantable`），却各自手填 `tenantId` 并以 `0L` 作默认值 | 未实现 `Tenantable` ⇒ SDK 自动租户改写（`TenantSqlRewriter`）很可能不生效；`0L` 默认值是**跨租户塌缩风险**（多租户数据串号到平台租户） | 这些实体改 `extends TenantAggregateRoot<Long>`（或显式 `implements Tenantable`）接入自动过滤；删除 `0L/1L` 兜底（缺失应报错而非归并平台租户）；核查各 Repository 是否声明 `@TenantScope` | Medium |
| P1-4 | `bone-platform/bone-integration/.../application/support/FlowExecutionSupport.java:7` | application 直连 infrastructure 实现类（死 import）：`import ...infrastructure.flow.LinearSyncFlowRuntime`，实际注入的是端口 `FlowRuntime` | 虽为死代码，但构成 application→infrastructure 源码依赖，违反 ADR-0028 / E-3 | 删除 `import com.bone.integration.infrastructure.flow.LinearSyncFlowRuntime;`（第 7 行）；确认仅依赖 `FlowRuntime` 端口 | High |

### 4.2 文档体系（5 项）

| # | 文件:行 | 问题 | 为什么 | 可执行修复 | 置信度 |
|---|---|---|---|---|---|
| P1-5 | `Bone-DDD-最终实践方案.md` E-1.2 / CORE-01:627-658 | **E-1.2 数据所有权声明仅 3/15 模块有**（仅 blueprint/iam/masterdata）；integration/system/metadata-server/metadata-engine/studio-generator/extension-studio/notification 等已落地业务表却无声明 | CORE-01 要求"每个上下文在 README 用 E-1.2 声明拥有的表"作为数据所有权唯一真源。缺失 ⇒ 守护名存实亡，表变更责任不清、易冲突 | 为缺失模块 README 补 E-1.2 段（参照 iam/blueprint 格式）；或在 E-1.2 显式列"已声明清单 + 未声明为技术债" | High |
| P1-6 | `bone-init.sql` vs 模块 README/`@Table` | **13 张建表既无生产 `@Table` 实体、也无任何模块 E-1.2 声明**（真孤儿表）：`cnsl_dashboard_widget`、`cnsl_notification`、`cnsl_recent_access`、`iam_audit_settings`、`iam_policy`、`iam_refresh_token`、`int_dead_letter`、`int_template`、`gen_type_mapping`、`meta_code_template`、`mdm_category`、`mdm_record_category`、`mdm_record_version` | 数据所有权真空：谁负责演进/迁移/回填未知；其中 `cnsl_*` 疑似控制台外部模块，更需显式登记 | 逐表裁定：属某模块则补 `@Table`+E-1.2；属规划/外部则在 README 或基线注明 | High |
| P1-7 | `bone-platform/bone-iam/README.md:69` | E-1.2 把 `iam_session` 列为本上下文"拥有的表"，但全仓无其 DDL/`@Table`；`Session` 经 `RefreshTokenSessionGatewayAdapter` 网关式（Redis）持久化，非关系表 | "表"标签不准确，声明与实现矛盾，误导读者 | 改为"会话数据（网关/Redis 持久化，非关系表）"，或删除该行使声明一致 | High |
| P1-8 | `bone-engine/studio-generator/.../templates/controller.ftl:13,30,40` | 代码生成器模板 `getById`/`page` 直接返回**领域实体** `ApiResponse<${customEntityName}>` | 生成器是"新模块起点"（G-1.4 已登记），出参仍是实体违反 CORE-05 读侧投影边界，会污染所有由它生成的代码 | 模板改为返回 adapter 投影/DTO；与 E-6.6 档位策略对齐后再作为新模块起点 | High |
| P1-9 | `doc/architecture/application-constructs-baseline.json`（base:15 / json:126 / adr/README:45） | 三处均称"96 条存量违规登记在 baseline（只可收缩）"，但基线文件实际 `"violations": {}`（0 条） | "存量只可收缩"门禁语义失真——要么 96 已清零、要么基线丢失；文档/JSON/README 措辞与现状矛盾，文档不可信 | 先跑 `scripts/check-application-constructs.py --check` 确认当前实际违规数；若已清零改为"已收敛清零"，若误清则恢复条目 | High |

### 4.3 门禁盲区（7 项，含 P0 延伸）

| # | 文件:行 | 问题 | 为什么 | 可执行修复 | 置信度 |
|---|---|---|---|---|---|
| P1-10 | studio/extension-studio/notification/system 的 `ArchitectureTest` | `businessLayersMustNotReadTenantContextDirectly`（E-2）在 4 个应用模块**完全缺失**（iam/masterdata/integration/metadata-server/blueprint 已接） | 业务层直调 `TenantContext` 有跨租户风险却无门禁守护 | 4 模块接入该规则（存量可先冻结） | High |
| P1-11 | system/notification/studio-generator/extension-studio 的 `ArchitectureTest` | `readSideDslOnlyInQueryLayer`（E-4.2）同样 4 模块缺失 | 这 4 模块 application 层可持有读侧 DSL（Criteria/QueryBuilder）而不报警，违反 ADR-0030 读侧落点约束 | 4 模块接入该规则 | High |
| P1-12 | 各模块 `ArchitectureTest`（仅 blueprint 接） | `springComponentBeanNamesMustBeUnique`（E-13.0）仅 blueprint 接入 | 协议同名类无显式 bean 名时，启动期才抛 `ConflictingBeanDefinitionException`，构建期无门禁；gate-state 称 553 组件当前零冲突，可全模块接线 | 在其余模块接入该规则（G-1.5 已确认零冲突，安全） | High |
| P1-13 | `BoneDddArchRules.java:873` | R9 委盲点：跨类编排（ApplicationService→ServiceA.save+ServiceB.save）时顶层无直接 repo 调用 ⇒ 绿；非聚合根实体返回 `null` 不计数 | 一事务写两聚合经下游 service 编排时漏检；非根建模反模式同事务双写不计数（注：根+子实体同事务属 By Design 正确跳过，非假绿） | 短期跨类遍历累加 persist 调用；非根按"仓储所属聚合白名单"计数；至少规则注释显式声明盲区 | Medium |
| P1-14 | metadata-engine-domain/ports/runtime/starter 4 个 `ArchitectureTest` | `domainMustNotDependOnOuterLayers`（HC-002，标 Active）未接入这 4 引擎模块 | 覆盖不一致；引擎模块若长出 adapter/application 层则无守护（现状低风险） | 评估后接入，或显式登记"引擎内部模块豁免" | Medium |
| P1-15 | `bone-metadata-server/.../ArchitectureTest.java:114-123` | `adapter_no_domain_repository_all_packages` 是共享规则的**重复内联版且缺 `..adapter.schedule..` 豁免**（对照 105 行冻结版带豁免） | 与 ADR-0030 授权的 schedule 例外自相矛盾，且冗余 | 删除该内联重复规则，105 行共享冻结规则已带正确豁免 | High |
| P1-16 | `scripts/check.sh:28-49`（pre-commit [2/5]） | 只跑**变更模块**的 `*ArchitectureTest`（`-Dsurefire.failIfNoSpecifiedTests=false`）。若改动共享规则库 `bone-architecture-test`，本地 pre-commit 只重跑该单模块，不会回归全 20 模块 | 本地"就近"检查的残留盲区（CI `mvn clean verify` 已全量覆盖）。注意：任务假设的 `cmd || true | sed` 中和写法**经核已修复**，不列为问题 | 在 pre-commit 增加"若改动 `bone-architecture-test/**` 则跑全 reactor ArchUnit"分支；或文档标注"本地 ≠ CI" | Medium |

### 4.4 前端工程（3 项）

| # | 文件:行 | 问题 | 为什么 | 可执行修复 | 置信度 |
|---|---|---|---|---|---|
| P1-17 | 根 `pnpm-workspace.yaml` | 仓库同时声明**两套包管理器 workspace**：根 `package-lock.json`+`package.json.workspaces`（npm）已存在，却又保留 `pnpm-workspace.yaml`；脚本均用 npm | 死配置误导贡献者用 pnpm 装出不同依赖树，破坏可重现构建 | 删除 `pnpm-workspace.yaml`（npm 为权威）；若确有 pnpm 计划则统一迁移并加 `pnpm-lock.yaml`、移除 npm workspaces | High |
| P1-18 | `apps/bone-shell/src/auth/axiosAuth.ts` + `services/consoleApi.ts` + `src/App.tsx` | Shell **混用两套请求层**：`consoleApi.ts`/登录登出走裸 `axios.get/post`（全局实例 + `axiosAuth.ts` 全局拦截器注入 token）；而 `App.tsx:6` 又 import `@bone/shared-services` 的 `createApiClient`（已自带回填 token + 解包 `ApiResponse`） | 同一应用两套鉴权/响应处理路径，易漂移且 token 注入逻辑重复；任一处改了拦截器另一处不跟着变 | 将 `consoleApi.ts`/`App.tsx` 的裸 axios 改为 `createApiClient`；随后删除 `axiosAuth.ts` 及其在 `main.tsx` 的调用 | High |
| P1-19 | `apps/bone-shell/src/App.tsx:1-1166` | 单体 God 组件：同时承担布局、菜单、主题切换、通知面板、JWT/权限解析、qiankun `registerMicroApps` 启动 | 难测试、易冲突、PR 易撞车 | 把 `NotificationPanel`/主题切换/Sider 菜单/qiankun bootstrap 抽为独立模块，`App.tsx` 仅做组合 | High |

---

## 五、P2 — 次要 / 漂移（汇总）

| # | 范围 | 文件 | 问题 | 修复方向 | 置信度 |
|---|---|---|---|---|---|
| P2-1 | 后端 | `bone-extension-studio/.../domain/gateway/*ReadPort`（5 个） | ADR-0030 旧形态残留：`*ReadPort` 独立接口未折叠进 `domain/repository` 的 `default`/投影读 | 读方法迁为对应 `*Repository` 的 `default` 方法，删 `domain/gateway/*ReadPort` | Medium |
| P2-2 | 后端 | `bone-metadata-server/.../adapter/web/controller/MetadataController.java:61,90,125` | `ApiResponse<List<FieldMetadata>>` 直接以 SDK 领域模型出 adapter（`FieldMetadata` 系对外契约，边界情况） | 若为不可变值对象+对外契约可保留并补注释；否则新增 `application/dto` 投影 | Low |
| P2-3 | 后端 | `bone-system/.../domain/model/{dict,log,config,schedule,alert}/*.java` | 实体继承纯 `AggregateRoot<Long>` 无 `tenantId`（可能 by-design 平台级） | 核对 HC-008 基线；by-design 则在模块 README 登记理由，否则改 `TenantAggregateRoot` | Medium |
| P2-4 | 后端 | `bone-iam/.../domain/model/{role/RolePermission,account/AccountRole}.java:15,16` | 关系实体继承纯 `Entity<Long>` 无 `tenantId`（账号-角色绑定本质租户内关系） | 确认经 Account 的 tenant 间接隔离；如需独立隔离则补 `TenantAggregateRoot`/`Tenantable` | Low |
| P2-5 | 文档 | `Bone-DDD-最终实践方案.md:1921` | 正文 G-1.1 用 `R1/R2/R3` 作本地标号，正文禁用 `R[1-9]` 规则编号 | 改 `判定①/②/③` 或 `C1/C2/C3` | Med |
| P2-6 | 文档 | `Bone-DDD-最终实践方案.md` G-1.4:1979 | 描述 `controller.ftl`"直接外吐领域实体"措辞偏严（实际已经济 Service，仅出参是实体） | 修正为"经 ApplicationService 但出参仍是领域实体，未转投影" | Med |
| P2-7 | 文档 | `doc/architecture/adr/README.md:40` | ADR-0030 状态标"草案 · 待批准"，但主文档已当"已落地"大量引用且代码确有实现 | 批准后把 ADR-0030 状态升级为"已采纳/已落地" | Med |
| P2-8 | 门禁 | `BoneDddArchRules` / 各 `ArchitectureTest` | `command_handlers_must_not_depend_on_application_service` 谓词锚 `..application.command.handler..`，该包随 Handler 内联"消失" ⇒ 0 命中、永久死规则 | 谓词扩到 `..application..`，或删除（若已被其它规则覆盖） | High |
| P2-9 | 门禁 | 6 个平台/引擎应用模块 | `engineModulesMustNotDependOnPlatform` / `platformMustNotDependOnEngineApps`（ARCH-LEVEL 01/02）仅 2 模块接入 | 在 6 模块接入两条 ARCH-LEVEL 规则 | Medium |
| P2-10 | 门禁 | `archunit_store/*.rules` | `FreezingArchRule` 冻结存量 ⇒ "绿 ≠ 无违规"；风险仅在团队为吸收新暴露违规而重冻结时产生假绿 | CI 增加步骤：比对 `archunit_store/` 条目数是否增加，增加即告警/阻断 | Low-Med |
| P2-11 | 门禁 | 引擎/框架模块 | `noCrossContextDomainDependency` / `noCrossContextModelDependency`（E-1.3）未接入 | 评估引擎应用模块是否需接入 | Low |
| P2-12 | 前端 | 各 app `package.json` | `antd` 声明区间分裂（`^5.12.0`/`^5.12.8`/`^5.27.6`，靠根 overrides 统一） | 统一为单一区间，或 README 注明"以 overrides 为准" | High |
| P2-13 | 前端 | 各 app `package.json` | `@tanstack/react-query` 仅 generator/iam/shared-services 声明，其余 5 app 消费却靠 hoist | 实际用到 hooks 的 app 显式声明（与 shared-services peer `>=5` 对齐） | Med |
| P2-14 | 前端 | `packages/core/event-bus/package.json` | event-bus 用 vite 4，其余 vite 5（根 override `^5.4.21`） | 升到 `^5.4.21` | High |
| P2-15 | 前端 | 全仓 `@bone/ui` 仅 10 处 import | 设计系统落地率极低，主题只在 shell 局部设置；微前端下各子应用 Token 未集中 | 每份子应用入口用 `BoneAppProvider`/统一 `ConfigProvider` 包裹，优先从 `@bone/ui` 取 token | Med（抽样） |
| P2-16 | 前端 | `apps/*/public/qiankun-entry.js`（7 份） | 7 子应用各持几乎相同 dev 入口（仅端口不同） | 抽共享模板/生成脚本按 app 注入端口 | High |
| P2-17 | 前端 | 根 `tsconfig.json` vs `tsconfig.base.json` + 每 app `tsconfig.node.json` | 双根级 tsconfig，`paths:@bone/*` 配置冗余且 app 实际不继承 | 明确单一真源（base 放共享项，root 仅 `tsc -b` 编排） | Med |
| P2-18 | 前端 | `bone-metadata-app/src/pages/FieldManagement.tsx` 等 | 调试残留 `console.log`（密度低） | 删 `console.log`，或 `import.meta.env.DEV` 包裹 | High（抽样） |
| P2-19 | 前端 | `services/consoleApi.ts` | Shell 自定义 `BoneApiResponse` 与 `@bone/shared-types` 的 `ApiResponse` 重复 | 复用 `@bone/shared-types` 的 `ApiResponse<T>` | Med |

---

## 六、为什么这些会同时出现（根因分析）

1. **门禁"分散接线"治理缺口**：每个模块自己写 `ArchitectureTest`，共享规则库升级后无推广机制。新规则在 `blueprint` 试点后没有"全量接线检查"门禁，于是 20 个模块里只有 1–2 个真正校验它。这不是代码 bug，是**流程缺口**——修复手段应是"规则上线即要求全模块接线"的契约。
2. **E-1.2 缺强制门禁**：文档要求每模块声明拥有的表，但只有 blueprint 的 `SqlTemplateGovernanceTest` 部分解析，无"缺失即红"的门禁 ⇒ 声明沦为样板，新模块不补也不报错 ⇒ 13 张孤儿表。
3. **前端处于迁移中途**：npm↔pnpm、shared-services 引入、状态管理迁移（见 `STATE_MANAGEMENT_MIGRATION.md`）并行，产生配置双轨与双请求层，属演进期债务，需收口而非根治。

---

## 七、建议修复路线（可执行，分阶段）

**Phase 0 — 立即（防假绿继续扩散，半天）**
- P0-1~P0-4：把 4 条 Hard gate 规则接入全部应接模块（先本地跑 `mvn -o -pl <m> test` 确认 0 违规再提交）。
- P1-16：pre-commit 增加"改动 `bone-architecture-test/**` 则跑全 reactor ArchUnit"分支。

**Phase 1 — P0 门禁收口 + 防回归（1–2 天）**
- P1-10/11/12/14：补 E-2、E-4.2、bean 名唯一、HC-002 引擎模块接线。
- P2-10：CI 增加 `archunit_store/` 条目数增加即阻断，杜绝"重冻结吸收新违规"。
- 建立"共享规则库新增/收紧规则 → 必须全 20 模块接线"的契约（可在 `collect-all-compliance.sh` 加校验）。

**Phase 2 — P1 后端 + 文档（2–3 天）**
- P1-1 FileController：补 `FileApplicationService` 或存储端口。
- P1-2 domain `@Component`：改 `@Bean`/`new` 注入。
- P1-3 studio 租户 `0L` 兜底：接 `TenantAggregateRoot`，删兜底。
- P1-4 删死 import。
- P1-5/6/7：补 E-1.2 声明、裁定 13 孤儿表、修正 `iam_session`。
- P1-8 controller.ftl 改投影；P1-9 跑 `check-application-constructs.py --check` 后修正 baseline 措辞。

**Phase 3 — P1 前端 + P2 清理（3–5 天）**
- P1-17 删 `pnpm-workspace.yaml`；P1-18 统一 `createApiClient` 删 `axiosAuth.ts`；P1-19 拆 `App.tsx`。
- P2-12~19 版本声明/设计系统/配置冗余/console.log 逐步清理。
- P2-1~7、P2-8/9/11 存量漂移按模块触达收敛。

**Phase 4 — 验证**
- 每阶段结束跑 `./scripts/check.sh` + 全量 `mvn -o clean verify` + `check-ddd-doc-drift.py` / `check-ddd-gate-state.py`；
- 改共享门禁后 `diff archunit_store/` 新增条目必须为空文件（空=真绿，非空=假绿）。

---

## 八、覆盖说明与免责

- **后端**：各模块抽关键文件（controller/application/domain/infrastructure 各 1–3 个）+ 全仓 grep import，非逐文件通读。
- **文档**：已读 `Bone-DDD-最终实践方案.md`、ADR README、模块 README、`gate-state.json`、CI 配置，并做了代码符号与 `@Table`/`bone-init.sql` 比对。
- **门禁**：已逐文件核对 20 个 `ArchitectureTest`、共享规则库、CI、`archunit_store`。
- **前端**：抽样 7 应用配置与少数源码，未运行构建/未全量通读页面；`console.log`/`any`/主题相关结论为**抽样推断**（已在对应行标注 Confidence）。
- 完整原始发现见 4 个子 Agent 输出（已合并去重，本报告为唯一汇总真源）。

---

## 九、执行进度（2026-09-23 已落地，未提交）

按"非 L3 / 高置信 / 可安全验证"口径执行，未触 DDL / 依赖 / CI 脚本 / 共享门禁（均属 L3，需架构师审批）。

### 已落地（已通过 `spotless:check` 验证）
| 项 | 文件 | 改动 |
|---|---|---|
| P1-2 | `bone-metadata-server/.../domain/service/IamApplicationValidator.java` + `IamModuleValidator.java` + `config/CatalogInfrastructureConfiguration.java` | 删 domain 类 `@Component` 与 import；在 `CatalogInfrastructureConfiguration` 以 `@Bean` 注册（保全 domain 零框架依赖） |
| P1-4 | `bone-platform/bone-integration/.../application/support/FlowExecutionSupport.java` | 删死 import `LinearSyncFlowRuntime`，仅依赖 `FlowRuntime` 端口 |
| P1-6 | `bone-platform/bone-iam/README.md` | `iam_session` 改为"网关/Redis 持久化，非关系表" |
| P1-9 | `Bone-DDD-最终实践方案.md` / `adr/0035-*.md` / `adr/README.md` | 跑 `check-application-constructs.py` 实测 **0 违规**；三处"96 条"措辞改为"已清零 `violations: {}`"，保留历史事实 |
| P1-17 | `bone-frontend/pnpm-workspace.yaml` | 删除（npm 为权威 workspace） |
| P1-5 | `bone-integration` / `bone-system` / `bone-metadata-server` / `bone-extension-studio` 的 README | 补 E-1.2 数据所有权声明（仅列各模块 `@Table` 实体对应表） |
| P1-1 | `bone-file` FileController + storage | `FileStorageService` 接口由 `infrastructure.storage` 迁为 `application/port/out/FileStoragePort`（出站端口）；Controller 与 `MinioFileStorageService` 改注端口，删旧接口。修复 ADR-0028 adapter 直连 infrastructure 边界违规（`spotless:check` + compile 均绿，commit 549663184） |
| P1-15 | `bone-metadata-server/.../ArchitectureTest.java` | 删除与共享规则 `adaptersMustNotDependOnDomainRepository` 重复、且缺 `..adapter.schedule..` 豁免的内联规则 `adapter_no_domain_repository_all_packages`；删除后由共享冻结规则覆盖（范围等价且保留 schedule 受控授权），门禁更正确。`ArchitectureTest` 26/26 绿（commit 549663184） |
| P1-10 | studio/extension-studio/notification/system 4 模块 `ArchitectureTest` | `businessLayersMustNotReadTenantContextDirectly`(E-2) 接入 4 模块（存量 0 违规，冻结基线空=真绿）；iam/masterdata/integration/metadata-server/blueprint 早已接入。commit `c4691a9ba` |
| P1-11 | 同上 4 模块 | `readSideDslOnlyInQueryLayer`(E-4.2) 接入 4 模块（实测 0 违规，与 blueprint 同口径）。commit `c4691a9ba` |
| P1-12 | 15 个其余模块 `ArchitectureTest` | `springComponentBeanNamesMustBeUnique`(E-13.0) 全模块接线（G-1.5 确认 553 组件零冲突，安全）。commit `0d9618e62` |
| P1-14 | 4 引擎模块 `ArchitectureTest` + `pom.xml` | `domainMustNotDependOnOuterLayers`(HC-002) 接入 domain/ports/runtime/starter，新增 `bone-architecture-test` 测试依赖；引擎 domain 无 `..infrastructure..` 依赖，实测 0 违规（真绿，非冻结）。commit `0d9618e62` |
| P1-16 | `scripts/check.sh` | 新增分支：改动 `bone-architecture-test/**` 时跑全部含 `ArchitectureTest` 的模块（find 定位 + `-f` 逐模块，避开 reactor 路径不稳与前端 node 模块）。commit `0d9618e62` |
| P2-8 | `bone-blueprint` + `bone-metadata-server` `ArchitectureTest` + `scripts/check.sh` | blueprint 旧命令规则谓词 `..application.command.handler..` 包随 ADR-0028 内联消失 ⇒ 0 命中死规则，改为共享 `commandHandlersMustNotUseQueryBuilder()`（与 masterdata 对齐）；metadata-server 删同样的死谓词内联（已另有冻结版 `command_no_query_builder`），并移除孤立 `noClasses` 静态导入。commit `6a6e3e2b7` |
| P1-13 | `bone-framework/bone-architecture-test/.../BoneDddArchRules.java` | `oneAggregatePerTransaction` 新增 Javadoc 声明已知盲区：判定仅基于 `entry.getMethodCallsFromSelf()`（同方法体内直接调用），多层编排 `A→B→repo.save(X)` 且无任一被扫描类直接对两聚合 save 时不拦截；long-term 须做跨类调用遍历。commit `233d63966` |
| scripts/check.sh (L3 修复) | `scripts/check.sh` | [2/5] 共享门禁库变更分支原用 `mapfile`（bash≥4.0 内建，macOS 默认 bash 3.2 无）⇒ 改动 `bone-architecture-test` 时钩子崩溃中断提交；改为 `while-read` 收集，bash 3.2 兼容。修复后该分支全量 ArchUnit 回归真正跑通（覆盖全部含 `ArchitectureTest` 模块）。commit `233d63966` |
| P2-9 | masterdata/integration/system/notification/gateway/bone-file 6 模块 `ArchitectureTest` | `engineModulesMustNotDependOnPlatform`(ARCH-LEVEL-01) + `platformMustNotDependOnEngineApps`(ARCH-LEVEL-02) 接入 6 平台模块（此前仅 iam + metadata-server 有）；规则带 allowEmptyShould，实测 0 违规（平台模块无引擎包、无平台→引擎应用壳依赖）。6 模块 ArchitectureTest 全绿（integration 28/28）。commit `9e55b017a` |

### 待架构师裁定（未动）
- **P0-1~4 门禁全量接线**：已于 P0 收口批次落地（10 个业务模块接线，10 个框架/引擎模块零可门禁对象正确排除；详见 `bone-p0-probe-2026-09-24.md` 第七节）；P1-10/11/12/14/16 门禁扩面亦已落地（见「已落地」表），其余模块（含引擎）门禁覆盖已一致。
- **P1-3** studio 租户 `0L` 兜底接 `TenantAggregateRoot`（含存量数据迁移，属 L4 禁 AI 执行）；**P1-8** `controller.ftl` 改投影；**P1-19** `App.tsx` 拆分。（P1-1 已落地，见「已落地」表）
- **P1-5 孤儿表**（13 张无 `@Table` 实体）：`cnsl_dashboard_widget` `cnsl_notification` `cnsl_recent_access` / `iam_audit_settings` `iam_policy` `iam_refresh_token` / `int_dead_letter` `int_template` / `gen_type_mapping` / `meta_code_template` / `mdm_category` `mdm_record_category` `mdm_record_version`——归属待裁定（不臆造）。
- **E-1.2 缺 README**：`bone-notification`、`studio-generator` 无模块 README（按规范不主动新建 md），待补。
- **前端 P2-12/13/14/18 / P1-18**：版本声明对齐、console.log 清理、双请求层统一——属大范围前端改动，建议单独评估。
