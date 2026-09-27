# 扩展管理模块（bone-extension-studio）设计方案复核报告

> 执行时间：2026-09-27 ｜ 轮换序号 #6 ｜ 对应设计稿：`doc/design/modules/5. 扩展管理模块详细设计方案.md`、`5a. 扩展管理模块核心场景及用例设计方案.md`、`doc/design/BONE-X-Studio-详细设计方案.md`
> 锚定版本：`doc/design/_global-contracts.yaml` generated_at=2026-09-26T23:40
> 代码快照：分支 `codex/nightly-bone-system-20260927`（基于 `a28803b62`，工作树干净）
> 对标基线：锚定 `hc_semantics` + `doc/architecture/多租户数据隔离方案设计.md`（§3.6 / §5 R4、通则 T1/T2）+ `doc/architecture/Bone-消息与事件规范.md`

## 一、阻断级问题（必须先解决才能达标）

| # | 问题 | 违反项 | 修复建议 | 证据（文件:行） |
|---|------|--------|----------|------------------|
| X-1 | `exts_plugin_version` 租户注入缺失：**所有行恒写入 `tenant_id = 0`**，而实体已声明 `tenantId` 触发 SDK 严格过滤 `tenant_id = :current` ⇒ 真实租户查询零命中，插件版本对其不可见 | 多租户 §5 R4② / §3.6；通则 T1（禁止同表混存 + 租户作用域） | L2：`toPluginVersionEntity` 补 `stampTenant(row)`（或显式 `TenantProvider.currentTenantIdOrNull()`）；**同时**必须裁决该表归属「平台种子层(0)」还是「租户实例层」——二者不可同表混存，若判为平台种子层则需拆表或去租户作用域（**L3**） | `StudioPersistenceConverter.java:138-158`（`toPluginVersionEntity` 全流程**无** `stampTenant`/`setTenantId`）；`:245-256`（`stampTenant` 仅 `ExtStudioExtensionImpl` / `ExtStudioExtensionPoint` 两个分支）；`:44`/`:77`（仅此 2 处调用）；`ExtStudioPluginVersion.java:24-25`（字段默认 `tenantId = 0L`）；`多租户数据隔离方案设计.md` §5 R4② |

> 说明：X-1 与「平台种子行隐身」是同一坑的两面。本轮实测 `bone-init.sql` 中 **`exts_plugin_version` 无任何平台种子 INSERT**（`grep "INSERT INTO exts_plugin_version"` 0 命中），故当前并非"种子行隐身"，而是**写入侧就把真实租户丢失成了 0**——缺陷当前即生效，非潜伏。

> 锚定原判 **HC-003 violated 经复核为误报**（见 §五自审），已纠正为 `implemented`，不计入阻断级。

## 二、建议级优化

| # | 当前设计/实现 | 业界对标 | 优化方案 | 证据 |
|---|--------------|----------|----------|------|
| X-2 | `StudioErrorCodes` 仅 **2 个** `EXT_*` 领域码，其余 5 个回落 `COMMON_*`；doc5 要求失败响应用 `EXT_*` | 错误码 i18n、领域错误码自解释（对照 `SystemErrorCodes` 37 个） | 补齐插件/扩展点生命周期领域码（`EXT_PLUGIN_VERSION_NOT_FOUND` / `EXT_DEPLOY_STATE_CONFLICT` / `EXT_CHECKSUM_MISMATCH` 等）并登记 errorCode→HTTP 状态 | `StudioErrorCodes.java:6-12`；doc5 #L459；锚定 `design_vs_code_gaps` |
| X-3 | `deployPlugin` 返回 `ResponseEntity<?>`（通配符） | OpenAPI 契约保真、客户端生成 | 收敛为 `ResponseEntity<ApiResponse<StudioOperation>>`（异步 202）或显式联合类型，消除 `?` | `ExtensionManagementController.java:333`（`public ResponseEntity<?> deployPlugin`）；`ExtensionStudioApplicationService.java:196` |
| X-4 | `ExtIamTenantDirectory` 在扩展模块落一份 IAM 租户目录副本，无同步机制、无数据所有权声明 | CORE-01（跨上下文不得静默复制） | 架构师裁定：改为调用 IAM 公开 API，或保留副本但补齐同步与所有权声明 | 锚定 `cross_module_contracts`「bone-extension-studio ↔ bone-iam」；`ExtIamTenantDirectory.java:22` |
| X-5 | 集成事件零实现：doc5 规划的 `ExtensionDeployedIntegrationEvent` 等 | 消息与事件规范 | 按路线图排期（**非缺陷**，见 §五）；实现前保持 `@NoDomainEvent` 显式声明 | doc5:287（标 **[Target]**）、:754（Target `integration-events`）；代码 `*IntegrationEvent` 0 命中；`ExtPointCommandApplicationService` / `ExtensionCommandApplicationService` / `StudioAuditSupport` / `PluginExecutionLogCommandApplicationService` 声明 `@NoDomainEvent`；SDK 侧仅 `ExtensionEvent` / `DefaultExtensionEventPublisher`（注册级生命周期，非集成事件） |

## 三、参考级对标（亮点）

- **响应信封高度统一**：单个 `ExtensionManagementController` 的 **38+ 个方法签名几乎全部为 `ResponseEntity<ApiResponse<T>>`**，并由 `StudioHttpSupport.ok()` / `StudioApiResponses.badRequest()/notFound()` 统一封装（`ResponseEntity<ApiResponse<T>>`），无任何裸返领域对象。合法例外仅 3 处且均合规：`ResponseEntity<Void>`×2（DELETE 204）、`ResponseEntity<Resource>`×1（插件包文件下载）。
- **租户声明面完整**：5 个实体（ExtensionImpl / ExtensionPoint / PluginVersion / PluginExecutionLog / AuditLog）**均显式 `@Column("tenant_id")`**，非遗漏型缺口。
- **DDL 审计列规范**：`exts_extension_point` / `exts_extension_impl` / `exts_plugin_version` / `exts_plugin_execution_log` 四张业务表均含 `tenant_id` + `created_at`/`updated_at`/`deleted` + `version`（乐观锁）。
- **方法级鉴权到位**：`@PreAuthorize("@studioSecurity.hasScope(...)")` 覆盖全部写操作，scope 常量化于 `ExtensionScopes`。

## 四、v2 优化稿（锚定纠正 + B 阶段清单 + L3 待审批）

### 4.1 锚定纠正（`_global-contracts.yaml`）
- **HC-003**：`violated` → **`implemented`**。锚定证据"ExtensionManagementController 使用 ResponseEntity"属**只 grep `ResponseEntity` 未看 body** 的误判——body 全为 `ApiResponse` 信封。这是与 bone-metadata-server、bone-system **同源的第三例误报**。
- **HC-008**：`partial` → **`violated`**。声明面（实体 `tenant_id` + 4 表 DDL 全列）合规，但**运行时租户注入覆盖不全**（`stampTenant` 仅 2/5 类实体）构成真实缺口（X-1），非 by-design。
  - 需澄清：`exts_audit_log` 缺 `updated_at`/`deleted` 是**只追加 by-design**（`ddl-required-columns-baseline.json:27-34` 已登记 `"classification": "by-design"`，`bone-init.sql:1194-1207` 佐证），**该表本身不算违规**，与 bone-integration `int_execution_log` 同口径。
- **design_vs_code_gaps 第四条**（"工作树有 4 个未提交改动"）→ **已消解**：`ExtensionScopes` 等已提交于 `7fa2535cb`，当前工作树洁净。

### 4.2 B 阶段实现清单（L2，非阻断）
- 修复 X-1 的写入侧：`toPluginVersionEntity` 补租户注入（**先于**归属裁决，避免继续产生 0 值行）。
- 补 X-2 领域错误码并同步 `Bone-错误码登记.md` 与 i18n。
- 收敛 X-3 的 `ResponseEntity<?>`。

### 4.3 L3 待审批（本任务不执行）
- 裁决 `exts_plugin_version` 表归属：平台种子层(0) ↔ 租户实例层**二选一**；若判平台种子层 → 按通则 T1 拆表（参照 `mdm_reference_value` / `mdm_reference_value_tenant`）或整表去租户作用域。
- X-4 `ExtIamTenantDirectory` 副本去留（CORE-01 裁定）。

## 五、A' 自审（三问）

| 问 | 结论 |
|----|------|
| **Q1 有无假阳性（误报）？** | **有 1 例已剔除**：HC-003。锚定据"使用 ResponseEntity"判 violated，实测 body 全为 `ApiResponse`（38+ 签名 + 两个统一封装类佐证），仅 3 处合法例外（204 / 文件下载 / LRO）。已纠正为 `implemented`。 |
| **Q2 有无漏检（假阴性）？** | **有 1 例已补**：锚定 HC-008 仅记 `partial`（且只提 `exts_audit_log` by-design），**漏掉运行时租户注入缺口** X-1。本轮读码 + 读多租户方案 §5 R4 交叉确认后升级为 `violated` 并列入阻断级。 |
| **Q3 证据是否可复现？** | 是。全部结论带 `文件:行`；X-1 另有反证闭环（`bone-init.sql` 无种子 INSERT ⇒ 排除"种子行隐身"解释，确认为写入侧丢租户）。 |

**自审结论：PASS（可放行至 B 阶段，但 X-1 建议优先于其他 B 项修复）。**

---

## 六、B 阶段实现结果（2026-09-27 夜间，轮换 #6）

> 分支 `codex/nightly-bone-extension-studio-20260927`（基于 `26e43321b`）。改动 18 个文件，全部落在 bone-extension-studio 模块与共享 i18n / 错误码登记三处，无跨模块代码改动。

### 6.1 X-1 租户注入闭环（阻断级，L2）

| 文件 | 改动 |
|------|------|
| `infrastructure/persistence/converter/StudioPersistenceConverter.java` | `stampTenant` 由「两个 instanceof 分支」改为 `Tenantable<Long>` 单入口（**只在租户未设置时填充**：`null` 或 `0L`；显式租户不被上下文覆盖）；`toPluginVersionEntity` / `toExecutionLogEntity` / `toAuditLogEntity` 三处补调用；审计日志原来的 `entry.getTenantId() != null ? … : 0L` 改为「先赋值再 stamp」，0 值不再被当成"已注入" |
| `infrastructure/persistence/entity/ExtStudioPluginVersion.java` 等 5 个实体 | 统一 `implements Tenantable<Long>`（字段与 Lombok getter/setter 本就存在，仅补接口声明） |
| `StudioPersistenceConverterTest` | 新增 3 条：插件版本行带当前租户 / 执行日志+审计日志带当前租户 / 显式租户不被覆盖 |

**为什么收成接口而不是继续加 instanceof**：原实现漏掉 3/5 类实体正是因为它按具体类型分支——新增实体类型时"忘了加分支"既不报错也不告警。改为接口后漏一个就编译不过，同类缺陷不可能复现。

> **仍未闭环的部分（L3，需架构师裁决）**：`exts_plugin_version` 到底是「平台种子层(0)」还是「租户实例层」。本轮只修写入侧，使新写入的行落真实租户；历史 `tenant_id=0` 的行与表归属裁定不在本轮范围（见 §4.3）。

### 6.2 X-2 领域错误码（建议级，L2）

| 文件 | 改动 |
|------|------|
| `common/StudioErrorCodes.java` | 新增 6 个领域码：`EXT_PLUGIN_NOT_FOUND`(404) / `EXT_EXT_POINT_NOT_FOUND`(404) / `EXT_PLUGIN_VERSION_NOT_FOUND`(404) / `EXT_PLUGIN_VERSION_CONFLICT`(409) / `EXT_DEPLOY_STATE_INVALID`(409) / `EXT_PLUGIN_PACKAGE_INVALID`(400) |
| `common/StudioErrors.java`（新） | 「码 → HTTP 状态」唯一配对真源 + **四参** `BizException` 工厂 + 反射 fail-fast（任一码常量未登记状态即类加载失败）。与 `SystemErrors` / `BlueprintErrors` 同构 |
| `config/StudioWebExceptionHandler.java` | 新增 `@ExceptionHandler(BizException.class)`：400–599 直用、越界按 500；`errorCode` 透传（`null` 时按状态回落 `VALIDATION_FAILED`/`INTERNAL_ERROR`）。**必须显式声明**——本类末尾的 `Exception` 兜底会抢在全局映射前命中，不声明则 404/409 会被兜底成 500 且丢码 |
| 5 个业务类（11 处抛出点） | 插件不存在 / 关联扩展点不存在 / 版本不存在 / 版本已存在 / 未启用即发布 / 未部署即模拟调用 / 非 JAR 包 等，由裸 `IllegalArgumentException` 改为 `StudioErrors.of(码, 上下文)` |
| `Bone-错误码登记.md` §EXT_ | 6 行新码登记 |
| `shared-utils/src/i18n/locales/{zh-CN,en-US}.json` | 8 个 `EXT_*` 键（含此前完全缺失的 `EXT_RESOURCE_NOT_FOUND` / `EXT_STATE_INVALID`） |

**行为变化（有意为之）**：「插件不存在 / 扩展点不存在 / 版本不存在」此前走 `IllegalArgumentException` → **400 且无领域码**，现在是 **404 + `EXT_*_NOT_FOUND`**；「版本已存在」由 400 变 **409**。「仅支持 JAR 插件包」仍是 400，但带回领域码。`JarMagicValidatorTest` 的期望异常类型随契约变更同步更新。

### 6.3 X-3 `deployPlugin` 返回类型收敛（建议级，L2）

- `StudioCommandResponses#asObject(...)`：把 `ResponseEntity<ApiResponse<T>>` 抬升为 `ResponseEntity<ApiResponse<Object>>`（状态码、响应头、报文体原样保留）。
- `ExtensionStudioApplicationService#deployPlugin` 与 `ExtensionManagementController#deployPlugin` 签名由 `ResponseEntity<?>` 改为 `ResponseEntity<ApiResponse<Object>>`：同步返回插件实体、异步返回 `{operationId}`，两种形态不再靠通配符表达，OpenAPI 不再退化为空 schema。
- **线上报文零变化**（泛型仅编译期）：`ExtensionApiLroTest` 的 202 + `$.data.operationId` 断言原样通过。

### 6.4 B' 代码复核（7 项）

| # | 复核项 | 方法 | 结果 |
|---|--------|------|------|
| 1 | 禁碰文件未被改 | `git status --porcelain` 对照 §0 禁碰清单；`.comet/**`、`gate-state.json`、`Bone-DDD-最终实践方案.md`、`bone-init.sql`、`.github/workflows/**` 均未出现 | ✅ |
| 2 | 无 scope creep | 改动文件全部在 §4.2 清单内；额外文件仅为「错误码登记 + i18n」——是 X-2 明确要求的同步项 | ✅ |
| 3 | 硬约束未新增违反 | 重跑 B1–B8 取证：`domain` 零外层 import；无新增 ORM；Controller 仍全 `ApiResponse` 信封；`@PreAuthorize` 未松动 | ✅ |
| 4 | L3/L4 未被执行 | 无 DDL、无删码、无依赖/CI 改动；`exts_plugin_version` 归属仍挂 §4.3 待审批 | ✅ |
| 5 | 契约一致 | 状态码与 `$.data.errorCode` 由新增 `ExtensionApiErrorCodeTest` 实测锁定；锚定 `exposed_apis` 路径未变 | ✅ |
| 6 | 门禁绿 | `mvn -o -pl bone-extension-studio test` **93 全绿**（ArchitectureTest 25；2 条 IT 需外部 MySQL/Redis，skip）；`check-i18n-sync.py` 通过（常量 188） | ✅ |
| 7 | 他人 WIP 未被卷入 | 工作树另有并发会话的 8 个 `bone-masterdata-app` 前端改动，本次提交按路径显式 `git add`，未包含 | ✅ |

## 七、C 联调验证结果

本机 dev 起全链路（含 Redis / MySQL）在夜间无监管实例可用（此前 `bone-metadata-server` 轮已记录：裸启会因 `BONE_REDIS_PASSWORD` 缺省被 Redisson AUTH 拒绝）。本轮按 §7.4 降级为**契约级联调**：用 `@SpringBootTest` + MockMvc 走真实 Spring MVC 全链路（Filter → Controller → 幂等执行器 → advice → JSON），断言**真实 HTTP 码 + 响应摘要**。

| # | 场景 / API | 验证方法 | 实际 HTTP 码 + 响应摘要 | 备注 |
|---|-----------|----------|------------------------|------|
| C-1 | `GET /api/v1/extension/plugins/999999/deployment-state` | `ExtensionApiErrorCodeTest#unknownPlugin_...` | **404**，`{"success":false,"data":{"errorCode":"EXT_PLUGIN_NOT_FOUND",…}}` | 改造前为 400 且无码 |
| C-2 | `POST /api/v1/extension/plugins:upload`（重复版本号） | `ExtensionApiErrorCodeTest#duplicateVersionUpload_...` | **201 → 409**，`errorCode=EXT_PLUGIN_VERSION_CONFLICT` | 幂等执行器之后透传 |
| C-3 | `POST /api/v1/extension/plugins/1:deploy`（异步） | `ExtensionApiLroTest#deploy_async_pollUntilDone`（既有） | **202 + Location**，`$.data.operationId` 为字符串；轮询至 `done=true` | X-3 改造后报文不变 |
| C-4 | `GET /api/v1/extension/points/999999` | `ExtensionApiContractTest#notFound_returnsProblemDetail`（既有） | **404**，`errorCode=EXT_RESOURCE_NOT_FOUND` | 回归未破坏 |
| C-5 | 错误码 → 状态映射 | `StudioErrorsMappingTest`（5 条） | 13 个码全部落在 4xx/5xx；未登记码 `IllegalStateException` fail-fast | 表驱动 |

## 八、D 验收测试（构造数据 → 模拟操作 → 清理自证）

| # | 场景 | 构造数据 | 模拟操作 | 断言 | 实测 | 数据已清 |
|---|------|----------|----------|------|------|----------|
| D-1 | 插件不存在时的用户可见反馈 | in-memory 种子（扩展点 1 / 插件 1）+ 不存在的 id 999999 | 查询该插件部署状态 | 404 + 前端可 `i18n.t('errors.EXT_PLUGIN_NOT_FOUND')` | 通过；语言包 zh/en 均已补该键 | ✅ in-memory 数据集随 Spring 上下文销毁，无外部库写入 |
| D-2 | 重复上传同一版本号被拒 | 上传 `7.7.7` 至插件 1（201），再上传同版本 | 走 `/plugins:upload` 两次 | 第二次 409 + `EXT_PLUGIN_VERSION_CONFLICT` | 通过 | ✅ 同上 |
| D-3 | 租户注入（X-1 验收面） | `TenantContext.setTenantId(1001/1002)` | 领域模型 → 持久化行 | 插件版本 / 执行日志 / 审计日志三行 `tenantId` = 当前租户；显式租户 2002 不被覆盖 | 通过（3 条单测） | ✅ 纯内存对象，无落库 |
| D-4 | 卫生断言 | — | — | 测试期间 0 5xx、0 未捕获异常 | 通过（93 测试全绿） | — |

> D 段未做浏览器端 Playwright 走查：`bone-extension-app` 前端无「部署/上传」页面的冒烟脚本，且本轮改动全在后端契约层（前端消费的是 `errorCode` 键，已由 i18n 门禁保证存在）。列入下一轮。

## 九、AI 自审结论

| 问 | 结论 |
|----|------|
| **Q1 证据可复现？** | 是。X-1 有 3 条单测 + 转换器 `文件:行`；X-2 有 5 条映射测试 + 2 条 MockMvc 契约测试（真实状态码）；X-3 有既有 LRO 测试作回归。全部可一键重跑。 |
| **Q2 分级正确？** | 是。X-1 按阻断级处理并优先于其他项；X-2/X-3 维持建议级。未出现"为好看降级"或"误判阻断"。 |
| **Q3 v2 稿安全？** | 是。§4.2 三项 B 清单全部落地，无新增硬约束违反；两处 L3（表归属、`ExtIamTenantDirectory`）仍在待审批清单，未被执行。 |

**自审结论：PASS（B/B'/C/D 完成，状态 `design_ready` → `implementing`）。**
