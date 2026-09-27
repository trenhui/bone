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
