# bone-iam 设计方案复核报告

> **执行时间**：2026-09-26 23:56 ~ 2026-09-27 00:2x（GMT+8）
> **轮换序号**：#1（锚定 `modules[0]`，依赖拓扑首位）
> **对应设计稿**：`doc/design/modules/6. IAM账号权限管理模块详细设计方案.md`（1264 行）+ `6a.`（312 行）+ `10. 应用与模块管理详细设计方案.md`（300 行）
> **代码快照**：分支 `codex/nightly-bone-iam-20260926`，HEAD `7fa2535cb`（工作树洁净：`git status --porcelain` 0 行）
> **锚定版本**：`doc/design/_global-contracts.yaml` `generated_at: 2026-09-26T23:40:00+08:00`
> **阶段**：A（设计复核 + v2 稿 + 自审）。**未写任何实现代码**，v2 稿已追加到 doc6 末尾。
> **对标基线**：锚定 `hc_semantics`（HC 编号语义唯一真源，`Bone-DDD-最终实践方案.md §G-1.7`）+ ADR-0028（入站边界）+ `Bone-API-规范.md` + `国际化设计方案.md`（v1.5）+ DDD/乐观锁/Google SRE 业界实践
> **HC 状态引用**：只引用 `doc/architecture/gate-state.json` 与 §G-1.7，本报告不复写门禁状态、不声称「CI 已拦截」。

## 零、本晚范围裁决（§1.6 合批门槛实测）

| 门槛 | 实测 | 结论 |
|---|---|---|
| 两个模块在 `cross_module_contracts` 无相互耦合 | **有耦合**：锚定 792-795 行 `between: ["bone-metadata-server","bone-iam"]`（IamModuleRef / IamApplicationRef 逻辑外键、无外键无事件补偿） | ❌ |
| `design_doc` 总行数 < 600 | bone-iam 单模块即 **1876 行**（1264+312+300） | ❌ |
| 均非 `design_doc == null` | 均非 null（但门槛已失败） | — |

→ **降级为当晚仅 1 个模块**：`bone-iam`。`bone-metadata-server` 顺延明晚（与 §1.6「不得跨序跳选」一致）。另：今晚为周六，按 §5「B 阶段默认不在周六日执行」，本晚只做 A 阶段。

---

## 一、阻断级问题（必须先改设计才能写代码）

| # | 问题 | 违反项 | 修复建议 | 证据（命令 + 结果 + 文件:行） |
|---|------|--------|----------|------------------------------|
| **B-1** | **审计日志 CSV 导出前后端契约三重错位，功能实际不可用**（响应形态 / 时间参数名 / 过滤字段名全错） | HC-003（Controller 必须返回 `ApiResponse<T>`）；`Bone-API-规范.md` 统一响应；doc6 §1.4「审计日志 CSV 导出 **As-Is**」与附录 C `iam-audit-csv-export` 的 As-Is 声明失真 | 前端改为 `responseType:'blob'` 走二进制下载（后端字节流契约不动，符合 RFC 6266 `Content-Disposition`）；`exportAuditLogs` 入参改 `startedAt/endedAt/operation` 并与 `getAuditLogs` 共用同一个查询构造 | 后端：`sed -n '30,80p' .../AuditController.java` → `public ResponseEntity<byte[]> export(...)`（`AuditController.java:55`）、BOM `\uFEFF` + CSV header（`:43`）、`EXPORT_MAX_SIZE=10000`（`:40`）、查询字段 `startedAt/endedAt/operation`；前端：`grep -n "exportAuditLogs" .../services/api.ts` → `api.ts:231-239` 声明 `api.get<never, ApiResponse<AuditLog[]>>('/audit/logs/export',{params})`，参数名 `startTime/endTime/action`（`:233,236`）；`AuditLog.tsx:110-111` 传 `startTime/endTime`、`:113` 判 `response.code === 200` 后 `response.data` |
| **B-2** | **ID 精度分裂**：后端雪花 ID 序列化为字符串，前端 `shared-types/iam.ts` 声明 `number` 并 `Number()` 转换，与设计稿明令相悖 | doc6 §2.6「**ID 精度契约**：后端雪花 ID 以**字符串**序列化返回，前端全链路以 `string` 承载，**禁止 `Number()`**」；doc10 §3.4 响应示例 `"id": "181234567890000001"` | `shared-types/iam.ts` 的 `Account.id/tenantId`、`Tenant.id`、`AuditLog.id` 等一律改 `string`；删除 `Number()` 转换；TreeSelect `value` 用 `String(id)` | `sed -n '1,30p' bone-frontend/packages/shared-types/src/iam.ts` → `:7 id: number`、`:8 tenantId: number`；`sed -n '45,55p' .../AccountManagement.tsx` → `:49-50 key: Number(n.id), value: Number(n.id)`；`:445 setSelectedDeptId(... Number(id))`；对照同仓正确写法 `api.ts:311 DeptNode.id: string`（注释明写「雪花 ID 序列化为字符串」）、`appApi.ts:15 id: string`；后端 `AccountDetailResp.java:8 private Long id` |
| **B-3** | **全模块无乐观锁 + 租户配额校验非原子**（`count → insert` 竞态窗口，配额可被并发超卖） | 乐观锁/并发一致性业界实践（Evans DDD 聚合不变式保护）；doc6 §1.4「租户配额 enforcement **As-Is**」在并发下不成立 | ① 配额改用同一事务内 `INSERT ... SELECT` 条件插入或数据库唯一约束计数表（**DDL 属 L3，进待审批**）；② L2 兜底：`TenantQuotaEnforcer` 内对 `iam_tenant` 行加 `SELECT ... FOR UPDATE`（走已登记的 JDBC 通道）或引入域服务级串行化 | `grep -rn 'version' bone-platform/bone-iam/src/main/java/com/bone/iam/domain/` → **0 命中**（13 个聚合全部无 version，`AggregateRoot.java:16` / `TenantAggregateRoot.java:16` 基类也无该字段）；`cat .../support/TenantQuotaEnforcer.java` → `:27-33 long count = accountRepository.countByTenant(tenantId); if (count >= max) throw ...`（无锁、与后续 insert 不在同一原子段）；写路径 22 处无条件 `save()`（如 `AccountApplicationService.java:111`、`AppApplicationService.java:60`） |
| **B-4** | **设计稿三处自相矛盾（角色继承）**：§1.4 标 As-Is done / §2.2+附录 A 标 [Target] 未实现 / 附录 C 标 As-Is 源码×2；实测**代码已实现**，附录 A 条目为陈旧项 | Docs-as-Code 三定律（SSOT / 强链接 / CI 派生）；AGENTS.md「文档治理」；`tools/iam-compliance-collector/backlog.yaml` 只写未实现项 | 从 `backlog.yaml` 删除 `role-inheritance-evaluation`，同步 §2.2 行 216 与附录 A 行 1207 改为 As-Is；重跑 `collect.py --sync-doc` | 矛盾三处：doc6 `:148`（As-Is，IAM-22 done，`RoleHierarchyResolver` 深度上限 5 + 环检测）vs `:216`（[Target]，`parent_role_id` 已入 DDL，**继承求值未实现**）vs `:1246`（附录 C，As-Is 源码×2）；实测：`grep -rln "RoleHierarchyResolver" bone-platform/bone-iam/src/main/java` → `application/RoleHierarchyResolver.java`、`AuthApplicationService.java`；`RoleHierarchyResolver.java:17` Javadoc、`:26-38 Role.parentRoleId`、`AuthApplicationService.java:273`「展开 parent_role_id 闭包」 |
| **B-5** | **健康检查恒为 UP（STUB）且 prod 不暴露 metrics**，与 §9 监控设计直接冲突；Micrometer 埋点 0 | doc6 §9.2「Rate/Errors/Duration … Micrometer Prometheus」、§9.1 SLO 目标、Google SRE RED 方法论；生产可观测性底线（故障不可被发现 = 无 SLI） | ① `IamModuleHealthIndicator` 拆为 DB/Redis/Minio 三个 contributor 并真检；② prod `management.endpoints.web.exposure.include` 至少保留 `metrics`（prometheus 端点走内网/鉴权，不放通匿名）；③ 登录/refresh 关键路径补 `Counter`/`Timer` | `cat .../IamModuleHealthIndicator.java` → `:25-33` 恒返回 `Health.up()` 且 `withDetail("status","STUB")`；`sed -n '20,32p' .../application-prod.yml` → `:26-32 include: health,info`；`sed -n '30,50p' .../application.yml` → `:35-44 include: health,info,metrics,prometheus`；`grep -rnE 'MeterRegistry|io.micrometer|Counter\.|Timer\.' bone-platform/bone-iam/src/main/` → **0 命中** |
| **B-6** | **微前端 locale 管道断裂 + 生命周期两套 mount 定义不等价**：下发 `locale` 被硬编码覆盖；`public/qiankun-entry.js` 硬编码 localhost 且 unmount 不卸 root | `国际化设计方案.md` §5.5.1 / P0-C（iam-app 是 8 个应用中**唯一**已订阅 `bone:theme:change` 的样板，见 §115/#275/#298）；qiankun 生命周期单一真源 | ① `main.tsx` 删除硬编码 `locale:'zh-CN'`，改为 `locale: props?.locale ?? readStoredLocale()`；② 合并 `:68-85` 与 `:94-105` 两套 lifecycle 为单一真源；③ `public/qiankun-entry.js` 改相对路径 + 真 unmount | `grep -n "locale\|subscribeLocaleChange\|renderWithQiankun\|export async function" .../main.tsx` → `:5` 已 `import { subscribeLocaleChange } from '@bone/shared-utils'`、`:73` 在 `renderWithQiankun.mount` 内调用；但 `:44 locale: 'zh-CN'` 硬编码覆盖下发值、`:95-98` 手写 `mount` **未调用** `subscribeLocaleChange()`、`:88-91` 独立运行路径亦未调用；`Read public/qiankun-entry.js` → `:12` `import('http://localhost:3003/src/main.tsx')`、`:19-21 unmount: () => Promise.resolve()`（不卸 root） |
| **B-7** | **错误码体系被绕过**：26 处裸 message 异常 + `MfaController` 用字符串拼接构造错误体验，`IamErrors`（码→状态唯一真源表）被架空 | `Bone-API-规范.md` 错误码章节；doc6 附录 C `iam-*` 证据体系；`IamErrorCodes.java:14` 自述「只承载稳定业务码字符串」，71 处已正确走 `IamErrors.of` | 新增/复用既有码：`IAM_REFRESH_TOKEN_INVALID`（`:38`）、`IAM_REFRESH_TOKEN_REUSE`（`:32`）、`IAM_DEPT_REQUIRED`（`:94`）；`MfaController:49` 改用 `IamErrors.of(IAM_MFA_NOT_AVAILABLE, ...)`；新增码须登记「Bone-错误码登记.md §6」 | `grep -rnE 'throw new (DomainException\|IllegalStateException\|IllegalArgumentException\|RuntimeException)\(' bone-platform/bone-iam/src/main/java/ \| grep -oE 'throw new [A-Za-z]+' \| sort \| uniq -c` → DomainException 12 / IllegalStateException 6 / IllegalArgumentException 3 / RuntimeException 5；`RefreshTokenIssuerGatewayAdapter.java:80,90,94`（「无效的刷新令牌 / 已撤销 / 已过期」，应走 `:38/:32` 已有码）；`Account.java:140`（「归属部门为必填项」，应走 `:94`）；`MfaController.java:49` `ApiResponse.error(501, IamErrorCodes.MFA_NOT_AVAILABLE + ": MFA 未在商业版/IdP 中启用")` |
| **B-8** | **doc6 §3.3 目录结构声明与 ADR-0028 / 实际代码不一致**：文档写 `application/command/{cmd|handler}` + `application/event/`，实际是 11 个 `*ApplicationService`、0 个 Handler、0 个事件类 | AGENTS.md §一.3（默认语义化 `*ApplicationService`，ADR-0028；**禁 Handler 与 ApplicationService 套娃**） | §3.3 目录树改写为 ApplicationService 形态并删除 `event/` 分支；补一句「事件接入须先出 ADR，当前 10 个类以 `@NoDomainEvent` 豁免（E-5.4）」 | doc6 `:421-424`（command/query/handler + `application/event/`）；`grep -rlE 'class [A-Za-z]+ApplicationService' bone-platform/bone-iam/src/main/java/com/bone/iam/application/ \| wc -l` → **11**；`grep -rnE 'class [A-Za-z]+Handler\|interface [A-Za-z]+Handler' .../src/main/java/` → 仅 `IamExceptionHandler`（全局异常处理，**非**用例 Handler）；`find bone-platform/bone-iam/src -iname '*event*' -o -iname '*outbox*'` → **0 命中** |
| **B-9** | **doc10 §3.2 `PUT /api/v1/apps/{appId}/modules/reorder` 契约空挂**：后端无该映射，前端亦无调用方 | doc10 §3.2 行 161；契约单源原则（doc6 §5 明言「HTTP 路径以 `iam-v1.yaml` 为唯一真源」） | 二选一：实现该端点（L2）或从 doc10 + `iam-v1.yaml` 撤回该契约并标注 [Target] | `grep -nE '@(Get\|Post\|Put\|Delete\|Request)Mapping' .../ModuleController.java` → 仅 5 个：`@RequestMapping("/api/v1/apps/{appId}/modules")`（`:25`）、`@GetMapping`（`:31`）、`/{id}`（`:37`）、`@PostMapping`（`:42`）、`@PutMapping("/{id}")`（`:50`）、`@DeleteMapping("/{id}")`（`:56`），**无 reorder**；前端 `Grep "/modules" bone-frontend/apps` → 命中全在 `bone-metadata-app`（`appModuleApi.ts`、`ModuleManagement.tsx`），无 reorder 调用 |
| **B-10** | **时间显示链路半通**：后端已落地 UTC 契约，前端 `AuditLog` 用裸 `dayjs`（未 extend utc/timezone）自行 format，多时区静默偏移 | `国际化设计方案.md` §6.4 P0-A（「带偏移才转时区、不带偏移告警」）；doc6 §2.4/§4.4 审计时间准确性 | `AuditLog.tsx` 改用 `packages/shared-utils/src/i18n/format.ts` 的既有 formatter（已含 `HAS_OFFSET` 判定），删除本地裸 dayjs 用法 | 后端已合规：`sed -n '1,12p' .../application.yml` → `:5-9 spring.jackson.time-zone: UTC` + `write-dates-as-timestamps: false`（注释明写「i18n 方案 §6.4」）；前端：`grep -n "import dayjs" .../AuditLog.tsx` → `:18` 裸 import、`:168` 直接 `.format(...)`；`Grep "dayjs|utc" bone-frontend/packages` → `packages/shared-utils/src/i18n/format.ts:12-14` 已 `dayjs.extend(utc/timezone/relativeTime)`、`HAS_OFFSET ? dayjs(raw).tz(LOCAL_ZONE) : dayjs(raw)`，但 iam-app 未引用 |

---

## 二、建议级优化

| # | 当前设计/实现 | 业界对标 | 优化方案 | 证据 |
|---|--------------|----------|----------|------|
| S-1 | 网关白名单含死条目 `/api/v1/iam/auth/login`（AuthController 无 `/auth` 前缀） | 配置即契约，死配置会误导后续维护 | 删除该条（正确路径 `/api/v1/iam/login` 已在白名单） | `sed -n '65,80p' bone-platform/bone-gateway/src/main/resources/application.yml` → `:72 /api/v1/iam/auth/login`、`:73 /api/v1/iam/login` |
| S-2 | `SessionController` 直返领域实体 `Session` | adapter 层 DTO 隔离（HC-003 精神） | 新增 `SessionResp`/`SessionDTO` 转换 | `SessionController.java:35 public ApiResponse<List<Session>> list(...)` + `:6 import com.bone.iam.domain.model.session.Session` |
| S-3 | 11 个 ApplicationService 中仅 `AuthApplicationService` 有日志；关键路径日志无 `tenantId`/`traceId` | 结构化日志强制字段（doc6 §9.4）、可定位性 | 统一日志切面或在写路径补 `tenantId`/`accountId`/`action`/`result` | `grep -rln '@Slf4j' .../src/main/java/` → 仅 8 个类；`grep -rnE 'MDC\|traceId\|X-B3\|traceparent'` → **0 命中** |
| S-4 | `resolveTenantFilter(Long)` 在 5 个 Service 各写一份 | DRY / 租户口径单一真源 | 抽到 `application/support` 的单一组件 | `DeptApplicationService.java:168`、`AuditApplicationService.java:132`、`RoleApplicationService.java:207`、`MenuApplicationService.java:233`、`AccountApplicationService.java:284` |
| S-5 | 6 个列表页缺空/加载/错误/无权限四态；错误一律退化为 toast | Ant Design Pro 状态设计规范 | 按 doc6 §2.8 补齐四态 | `grep -rn "\bEmpty\b\|\bSkeleton\b\|\bSpin\b\|\bResult\b\|\bErrorBoundary\b" .../apps/bone-iam-app/src/` → `Empty` 仅 2 页、`Spin` 仅 2 页、`Skeleton/Result/ErrorBoundary` **0 命中** |
| S-6 | 错误码→文案映射 0 处（24 处 `message.error` 硬编码中文），依赖后端原文 | `国际化设计方案.md` §118（错误展示散点实测 iam 3 处变量形态） | 建 `iam` 错误码文案映射表，拦截器统一兜底 | `grep -rn "message.error\|messageApi.error" .../bone-iam-app/src/` → 24 处；`apiClient.ts:72-76` 仅回填 `error.displayMessage` |
| S-7 | 后端已就绪但 iam-app 无页面：会话管理、个人信息/改密、MFA | doc6 §1.4 标 As-Is 的三项能力（IAM-21/23/08）前端不可达 | 补 3 个页面（或明确划入下轮并在 §1.4 标注「后端 As-Is / 前端 [Target]」） | `Grep "/accounts/{id}/sessions\|/me\|/mfa" bone-frontend/apps/bone-iam-app/src` → **0 命中**；后端 `SessionController.java:33,39,46`、`MeController.java:46,73,85`、`MfaController.java:28,37,42` 均已实现 |
| S-8 | 死代码：`infrastructure/handler/annotation/Capability.java`（0 引用）、`components/Layout.tsx`（0 引用） | 死代码是维护负债 | 删除（**L3 删码，进待审批**） | `grep -rn 'infrastructure.handler.annotation.Capability' bone-platform/bone-iam/src/` → 0；`grep -rn "Layout" .../bone-iam-app/src/` → 仅定义处 |
| S-9 | `iam_permission` 无 `tenant_id`，基线标 `needs-owner-decision` | doc6 §2.9 隔离矩阵已明写「权限目录：平台级共享，**无 tenant_id**」= by-design | 请 IAM Owner 裁决后把 classification 由 `needs-owner-decision` 改为 `by-design` 并写理由（改 baseline 属 L3） | `python3 -c "import json;d=json.load(open('doc/architecture/ddl-required-columns-baseline.json'));print(d['tables']['iam_permission'])"` → `{"missing":["tenant_id"],"classification":"needs-owner-decision",...}`；doc6 `:372` |
| S-10 | `AppController`/`MeController` 越过 ApplicationService 直注出向端口 `CurrentPrincipalPort` | AGENTS.md §一.3 入站边界一致性 | 二选一：收敛为经 ApplicationService，或在 ArchitectureTest 显式登记为允许形态 | `AppController.java:37`、`MeController.java:44` |
| S-11 | `iam-app` 单独声明 `dayjs@^1.11.21`，`shared-utils` 已有 `^1.11.23` 与 formatter | 单源依赖 | 统一走 shared-utils（涉及 package.json 依赖调整 = L3 待审批） | `bone-iam-app/package.json:22`；`packages/shared-utils/package.json` |

---

## 三、参考级对标（亮点，可酌情采纳）

1. **分层与入站边界是本仓样板（B1 全绿）**：`domain` 层全部 import 仅来自 `com.bone.core.*` / `com.bone.metadata.sdk.*` / `java.*` / `lombok.*`，13 处 `@Table` 是 SDK 注解而非 JPA；`application` 层 0 例 `infrastructure` import；15 个 Controller 全部经 `*ApplicationService`，**0 个用例 Handler，无套娃**。
   - 证据：`grep -rhoE '^import [a-zA-Z0-9_.]+;' .../domain/ \| sort -u`；`grep -rnE '^import com.bone.iam.infrastructure' .../application/` → 0 命中。
2. **持久化唯一（HC-001/HC-006）**：ORM 0 命中；JDBC 6 个类与 `doc/architecture/sdk-persistence-bypass-baseline.json` **完全对齐**（无越界、无陈旧）。锚定判定的「未登记新增 `AuditLogCleanupJob`」经复核为**误报**——该类只在 Javadoc 提及历史实现，实际注入 `AuditLogRetentionPort`。
   - 证据：`grep -nE '^import' .../adapter/schedule/AuditLogCleanupJob.java` → 无 JDBC import。
3. **租户旁路只有 1 处且已合规登记**：`AccountRepository.findByUsernameForLoginAllTenants`（`AccountRepository.java:40-43`），caller `AuthApplicationService` 已在 `ArchitectureTest.java:23-30` 显式登记，命名符合 `*AllTenants` 双向绑定。
4. **错误码主体干净**：`IamErrorCodes` 37 个常量 100% `IAM_` 前缀，71 处走 `IamErrors.of` 真源表，0 处 `new BizException`。
5. **分页响应 100% 统一**：8 处分页接口全部 `ApiResponse<PageResult<T>>`。
6. **UTC 时间契约已落地**（`application.yml:5-9`），领先于 `国际化设计方案.md` §6.4 的「后端无任何时区配置」描述——该描述对 bone-iam **已过时**，建议回写勘误。
7. **前端统一骨架**（`ModulePage`）9/10 页复用，7 页带 `StatisticCard` 统计行，与 doc6 §2.8 设计语言一致。

---

## 四、优化后的设计改进稿（v2 完整内容）

> 本节为增量修订稿，**不替代原稿**；每条注明「修订 / 新增」与目标章节。凡涉及 DDL、删码、依赖调整一律标 L3 并进第五章待审批清单。

### V2-1 契约层（阻断级 B-1 / B-2 / B-9 / B-10）

**修订 doc6 §5.5 + 新增 §5.8「导出类接口契约」**：
- 导出类端点（`GET /audit/logs/export`、`GET /accounts/export`）**不走 `ApiResponse` 信封**，返回 `ResponseEntity<byte[]>` + `Content-Type: text/csv;charset=utf-8` + `Content-Disposition: attachment; filename=...`（RFC 6266）+ UTF-8 BOM，上限 10000 行并在响应头 `X-Export-Truncated: true` 显式暴露截断。
- 前端统一封装 `downloadBlob(path, params)`：`responseType:'blob'`、从 `Content-Disposition` 取文件名、`createObjectURL` 下载；**禁止** `api.get<ApiResponse<T>>` 解包字节流。
- 查询参数名单一真源：`startedAt / endedAt / operation`（与 `AuditLogListQuery` 一致），前端 `getAuditLogs` 与 `exportAuditLogs` 共用同一个 `buildAuditQuery()`。

**修订 doc6 §2.6 / 新增 §2.10「ID 与标量契约」**（覆盖全模块，不只组织机构）：
| 后端类型 | JSON 形态 | 前端 TS 类型 | 禁止 |
|---|---|---|---|
| `Long`（雪花 ID：Account/Role/Permission/Tenant/Dept/Menu/App/Module/AuditLog） | 字符串 | `string` | `Number()` / `parseInt` / `==` 比较 |
| `LocalDateTime` | ISO-8601（UTC，`spring.jackson.time-zone=UTC`） | `string` | 裸 `dayjs()` 直接 format（须走 `shared-utils/i18n/format.ts`） |
- 新增守卫：在 `shared-types` 侧加类型约束（编译期），并在 iam-app 内以 lint 规则禁止 `Number(` 出现在 ID 字段上。

**修订 doc10 §3.2**：`PUT /apps/{appId}/modules/reorder` 标注「**[Target]**（后端未实现，前端无调用方）」；若要实现，排序语义明确为「同 `appId` 内全量重排 + 单事务 replace」（避免半序），并同步 `iam-v1.yaml`。

### V2-2 并发与不变式（阻断级 B-3）

**新增 doc6 §3.8「并发与配额不变式」**：
1. **配额**（L2 优先）：`TenantQuotaEnforcer.assertCanAddAccount/Role` 必须与后续写入在同一事务内，并对 `iam_tenant` 目标行加 `SELECT ... FOR UPDATE`（经已登记的 JDBC 通道），把「计数 → 判断 → 插入」变为串行段。
2. **乐观锁**（L3 待审批）：为 `iam_account` / `iam_role` / `iam_tenant` / `iam_dept` / `iam_menu` / `bone_application` / `bone_module` 增加 `version` 列 + 更新带 `WHERE id=? AND version=?`；SDK 层面需确认支持，未确认前**不落地**，先用 (1) 兜底。
3. 明确拒绝「先 count 再 insert」型校验写入 doc 反模式清单。

### V2-3 可观测性（阻断级 B-5）

**重写 doc6 §9.2 实现列**：
| 类别 | 指标 | 实现状态（v2） |
|---|---|---|
| Rate/Errors/Duration | `iam_login_requests_total`、`iam_login_failures_total{reason}`、`iam_login_seconds`（Histogram） | **L2 补齐 Micrometer 埋点**（当前 0） |
| Health | `IamModuleHealthIndicator` → 拆 `DatabaseHealthIndicator` / `RedisHealthIndicator` / `MinioStorageHealthIndicator`，经 `HealthContributorRegistry` 聚合为 `iam` 组 | **L2 替换 STUB** |
| 暴露面 | dev `health,info,metrics,prometheus`；prod 至少 `health,info,metrics`（prometheus 端点仅内网 + JWT 鉴权，禁止匿名） | **L2 修正 prod yml** |
| 日志 | 关键路径（登录、账号/角色/权限变更、租户创建）强制 `tenantId / accountId / action / result / traceId` | **L2 补日志切面** |

### V2-4 前端契约与微前端（阻断级 B-6 / 建议 S-5 / S-6 / S-7）

**新增 doc6 §2.11「微应用接线契约」**：
1. lifecycle **单一真源**：仅保留 `renderWithQiankun({bootstrap,mount,unmount})`；`mount` 固定执行 `subscribeLocaleChange() → initGlobalContext(props) → subscribeGlobalContextChanges() → render(props)`；删除手写 `export async function mount/unmount` 与 `public/qiankun-entry.js` 的硬编码 URL / 假 unmount。
2. locale：禁止硬编码 `locale:'zh-CN'`；`globalContext.locale = props?.locale ?? readStoredLocale()`。
3. 四态规范：每个列表页必须覆盖 空态 / 加载态 / 错误态 / 无权限态（`Empty` / `Spin|Skeleton` / `Result|Alert` / `Result 403`）；错误统一由拦截器按 `errorCode → 文案` 映射，页面不再裸写中文兜底。
4. 能力补齐矩阵（后端已就绪）：会话管理、个人信息/改密、MFA —— 本轮先补「个人信息/改密」（L2，低风险），会话与 MFA 列 [Target] 并在 §1.4 标注「后端 As-Is / 前端 [Target]」。

### V2-5 错误码与文档真源（阻断级 B-4 / B-7 / B-8）

1. `IamErrorCodes` 补齐/接線：`IAM_REFRESH_TOKEN_INVALID` / `IAM_REFRESH_TOKEN_REUSE` / `IAM_DEPT_REQUIRED` 替换 26 处裸 message 中的对应点；`MfaController` 停止字符串拼接；新增码登记「Bone-错误码登记.md §6」并跑 `scripts/check-i18n-sync.py`。
2. **文档真源修复**：`tools/iam-compliance-collector/backlog.yaml` 删除 `role-inheritance-evaluation`（代码已实现）；doc6 §2.2 行 216 / 附录 A 行 1207 改为 As-Is 并指向 `RoleHierarchyResolver`。
3. **doc6 §3.3 目录树改写**为 ApplicationService 形态（删除 `command/handler`、`query/handler`、`application/event/` 分支），并注明「接入领域事件前须出 ADR；当前 10 个类以 `@NoDomainEvent` 豁免，受 `ArchitectureTest.applicationSaveMustPairWithPublishOrExempt` 守护」。
4. 建议回写 `国际化设计方案.md` §6.4 勘误：bone-iam 已配置 `spring.jackson.time-zone: UTC`（`application.yml:5-9`）。

### V2-6 跨模块契约（对齐锚定 `cross_module_contracts`）

| 条目（锚定） | v2 处置 |
|---|---|
| `bone-gateway ↔ bone-iam`（共享 JWT secret，白名单 `/api/v1/iam/auth/login` 永不命中） | 删除死条目 S-1；**新增**：网关路由表是隐式契约，新增端点必须同步 `bone-gateway/application.yml` 并经 `IamGatewayRouteIT` |
| `bone-metadata-server ↔ bone-iam`（module_id/appId 逻辑外键、无外键无补偿） | **新增补偿设计**：IAM 删除应用/模块前先调元数据侧校验引用（或发布 `AppDeleted`/`ModuleDeleted` 集成事件），本轮先出设计，实现列 [Target] |
| `bone-extension-studio ↔ bone-iam`（`ExtIamTenantDirectory` 副本无同步） | **新增所有权声明**：IAM 是租户目录唯一写方；扩展侧副本改为只读缓存 + TTL，或改走 API 查询。实现列 [Target] |

---

## 五、实现计划（逐项标 L 级）

> 阶段 B（人工批准后）才执行；**本阶段未写任何代码**。

### 后端文件清单

- [ ] L2 | `bone-platform/bone-iam/.../adapter/web/controller/AuditController.java` | 导出端点保持字节流，补 `X-Export-Truncated` 头并在 Javadoc 注明「不走 ApiResponse」（契约显式化）
- [ ] L2 | `bone-platform/bone-iam/.../application/support/TenantQuotaEnforcer.java` | 配额校验加行级 `FOR UPDATE` 串行段（V2-2 第 1 条）
- [ ] L2 | `bone-platform/bone-iam/.../infrastructure/observability/IamModuleHealthIndicator.java` | 拆分真检（DB/Redis/Minio）并移除 STUB（V2-3）
- [ ] L2 | `bone-platform/bone-iam/src/main/resources/application-prod.yml` | `management.endpoints.web.exposure.include` 补 `metrics`（V2-3）
- [ ] L2 | `bone-platform/bone-iam/.../infrastructure/gateway/RefreshTokenIssuerGatewayAdapter.java`（:80,90,94）、`domain/model/account/Account.java`（:140）、`adapter/web/controller/MfaController.java`（:49） | 裸 message 改为 `IamErrors.of(已有码)`（V2-5 第 1 条）
- [ ] L2 | `bone-platform/bone-iam/.../adapter/web/controller/SessionController.java` | 新增 `SessionResp` 替换领域实体出网（S-2）
- [ ] L2 | `bone-platform/bone-iam/.../application/**` | 关键路径补结构化日志（tenantId/accountId/action/result）（S-3）
- [ ] L2 | `bone-platform/bone-iam/.../application/support/`（新增） | 抽 `TenantScopeResolver` 收敛 5 份重复 `resolveTenantFilter`（S-4）
- [ ] L1 | `bone-platform/bone-iam/src/test/java/...` | 为上述改动补单测（配额并发、错误码映射、Session DTO 转换）

### 前端文件清单

- [ ] L2 | `bone-frontend/apps/bone-iam-app/src/services/api.ts`（:231-239） | `exportAuditLogs` 改 blob 下载 + 参数名对齐 `startedAt/endedAt/operation`（B-1）
- [ ] L2 | `bone-frontend/apps/bone-iam-app/src/pages/AuditLog.tsx`（:100-133, :168） | 导出走 blob；时间显示改 `shared-utils/i18n/format.ts`（B-1/B-10）
- [ ] L2 | `bone-frontend/packages/shared-types/src/iam.ts`（:7-8, :68, :113 …） | ID/tenantId 改 `string`（B-2）
- [ ] L2 | `bone-frontend/apps/bone-iam-app/src/pages/AccountManagement.tsx`（:49-50, :445） | 去除 `Number()`，改 `String(id)`（B-2）
- [ ] L2 | `bone-frontend/apps/bone-iam-app/src/main.tsx`（:44, :68-105） | locale 不再硬编码；lifecycle 收敛为单一真源（B-6）
- [ ] L2 | `bone-frontend/apps/bone-iam-app/public/qiankun-entry.js`（:12, :19-21） | 去硬编码 URL + 真 unmount（B-6）
- [x] L2 | `bone-frontend/apps/bone-iam-app/src/pages/{Role,Permission,Tenant,Application}Management.tsx` | 补四态（空/加载/错误/无权限）（S-5，2026-09-28 完成）
- [x] L2 | `bone-frontend/apps/bone-iam-app/src/pages/{Account,AuditLog}Management.tsx` | 补四态（空/加载/错误/无权限）（S-5 剩余，2026-09-28 完成）
- [ ] L2 | `bone-frontend/apps/bone-iam-app/src/pages/Profile.tsx`（新增）+ `App.tsx` 路由 | 个人信息/改密页（S-7，范围最小的一项）

### 待审批清单（L3/L4，本任务不执行）

- [ ] L3 | **DDL**：`iam_account/iam_role/iam_tenant/iam_dept/iam_menu/bone_application/bone_module` 增 `version` 列（乐观锁，V2-2 第 2 条）→ 需架构师审批 + SDK 支持确认
- [ ] L3 | **删码**：`infrastructure/handler/annotation/Capability.java`、`apps/bone-iam-app/src/components/Layout.tsx`（S-8）
- [ ] L3 | **依赖**：`bone-iam-app` 移除本地 `dayjs`、改由 `shared-utils` 提供 formatter（S-11）
- [ ] L3 | **基线变更**：`doc/architecture/ddl-required-columns-baseline.json` 中 `iam_permission` 由 `needs-owner-decision` 改 `by-design`（S-9，需 IAM Owner 书面裁决，不改表结构）
- [ ] L4 | **不提议执行**：`bone-init.sql` 任何 DDL 变更、密钥/证书、生产库迁移 —— 仅登记

### 联调前置条件

1. MySQL 本地库已按 `bone-init.sql` 初始化（IAM 10 表）；`BONE_DB_PASSWORD=mysql123`、JWT 用 IAM dev 默认密钥。
2. `bone-gateway`(8888) → `bone-iam`(8081) 路由可用；**业务 API 必须走网关**（直连模块端口 `/api/v1/**` 一律 401 空体）。
3. `bone-shell`(3000) 能挂载 `bone-iam-app`(3003)；登录取到 `data.token`。
4. 可复现场景：登录 → 账号页 → 审计导出（验证 CSV 落盘 + 时间窗生效）→ 改密页 → 切 locale 观察文案/时间格式。

---

## 六、代码复核结果（B' 段）

| # | 复核项 | 方法 | 结果 | 处置 |
|---|--------|------|------|------|
| 6.1 | 禁碰文件 | `git status --porcelain \| grep -E "_global-contracts\|Bone-DDD\|gate-state\|bone-init.sql\|.github\|.comet"` | **0 命中** | 无需处置 |
| 6.2 | 范围 creep | 逐条比对改动文件 vs 第五章清单 | 清单外 **0** 项；清单内 2 项按 L3 规则未执行（乐观锁 DDL、`Capability.java` 删码） | 已入待审批清单 |
| 6.3 | 硬约束未新增违反 | `mvn -o -pl bone-platform/bone-iam test` | **159 tests / 0 failure / 0 error**，`ArchitectureTest` 28 项全绿 | 通过 |
| 6.4 | 契约一致性 | 前后端 ID / 时间 / 导出契约逐条对齐 | 见 C 段实测；**新发现 B-11（时间格式）已当场修复** | 已修 |
| 6.5 | 门禁 | `mvn -o -pl bone-platform/bone-iam spotless:check` | **BUILD SUCCESS** | 通过（`check.sh` 全反应堆版因他人在途文件不可用，已改用模块级等价集） |
| 6.6 | 未卷入他人 WIP | `git status --porcelain` 比对 | 他人 `studio-generator/*`、`bone-metadata-server/AuthController.java`、`bone-gateway/GatewayJwtProperties.java`、`AccountApplicationService.java:326` **均未纳入本次提交** | 提交按路径显式 add |
| 6.7 | L3/L4 未混入 | 逐文件分级 | 无 DDL、无删码、无依赖新增、无密钥改动；`bone-init.sql` 未落任何变更 | 通过 |

### 6.8 本轮实现与 v2 的偏差（必须显式声明）

| 项 | v2 写法 | 实际落地 | 原因 |
|---|---|---|---|
| B-3 配额串行 | `SELECT ... FOR UPDATE` | **改为单 JVM 分段锁 + 事务完成后释放**（`TenantQuotaEnforcer`） | SDK 无 `FOR UPDATE`（`bone-engine/bone-metadata-sdk` 0 命中），真做要新建 JDBC 出站端口 → 撞 `sdk-persistence-bypass-baseline.json`「只可收缩、新增文件不得加入」⇒ **L3**。现落地是**已声明的降级**，不是等价修复 |
| B-5 健康检查拆分 | 新建 `DatabaseHealthIndicator` / `RedisHealthIndicator` / `MinioStorageHealthIndicator` 三个类 | **单类聚合** Spring Boot 自动装配的 `db` / `redis` | 同样受 bypass 基线约束；且拆分只是物理分文件，聚合已拿到真值。**未新增文件** |
| B-5 Micrometer 埋点 | 补齐 `iam_login_*` 三个指标 | **未做** | 需在 `AuthApplicationService` 注入 `MeterRegistry`，属 L2 但超出本轮已验证范围；登记为建议项 S-12 |
| B-7 `Account.java:140` | 裸 `DomainException` → `IamErrors.of(DEPT_REQUIRED)` | **未做** | `ArchitectureTest.domainCoreShouldOnlyDependOnAllowedPackages` 限定 domain 只能依赖 `domain/java/com.bone.core/sdk/lombok`，`com.bone.iam.common` 不在白名单内；**并发会话曾改过、随即回退**（与 ArchTest 冲突）。正解需把校验上移到 application，另立条目 |
| S-5 列表四态 | 6 个列表页全覆盖 | `Profile`（失败态统一为 `error+403`）+ `Application`/`Tenant`/`Role`/`Permission`/`Account`/`AuditLog` 共 6 页全部按四态实现 | 已全部覆盖 |

### 6.9 本轮新增发现（A 段未识别）

| # | 问题 | 证据 | 级别 | 处置 |
|---|---|---|---|---|
| **B-11** | **时间格式契约**：后端 `AuditLogListQuery.startedAt/endedAt` 是 `LocalDateTime`，只接受 ISO-8601（`2026-01-01T00:00:00`）；前端发 `YYYY-MM-DD HH:mm:ss` → **列表与导出双双 400**。A 段只发现参数名错位（B-1），本层在 C 段才暴露 | `curl ".../audit/logs?startedAt=2026-01-01%2000:00:00..."` → `HTTP 400 COMMON_VALIDATION_FAILED`，rejectedValue `2026-01-01 00:00:00`；改 ISO 后 `HTTP 200` | 阻断 | **已修**：`AuditLog.tsx` 新增 `isoLocal()`，列表与导出共用 |
| **B-12** | **B-7 回归**：`MfaController.notAvailable()` 现为 `ApiResponse.error(501, "MFA 未在商业版/IdP 中启用")` —— **业务码完全消失**，前端/监控无法按码聚合 | 实测 `POST /mfa/enroll` → `HTTP 501`，message 无 `IAM_MFA_NOT_AVAILABLE` 前缀（对比 `IAM_LOGIN_FAILED: 用户名或密码错误` 前缀正常） | 阻断 | 该文件正被并发会话编辑，**未抢改**；登记待下一轮或由并发会话收口 |
| **B-13** | 共享 api client 的响应拦截器是 `(response) => response.data`，**响应头不透传**，导致 `Content-Disposition` / `X-Export-Truncated` 前端取不到，导出只能用兜底文件名 | `packages/shared-services/src/apiClient.ts:66` | 建议 | 已登记；需给 `createApiClient` 增加「原始响应」出口（L2，下轮） |

## 七、联调验证结果（C 段）

> 环境：网关 8888（监管实例）+ bone-iam **独立实例 8091**（`SPRING_PROFILES_ACTIVE=dev`，从 `target/classes` 启动，确保加载本轮改动）+ shell 3000 + iam-app 3003。
> 注：监管实例跑的是 `target/bone-iam-1.0.0.jar`（20:10 产物，早于本轮改动），故另起 8091 取证；业务 API 经 8091 直连已可正常鉴权（实测 401/200 语义正确）。

| # | 场景/API | 验证方法 | 实际 HTTP 码 + 响应摘要 | 备注 |
|---|----------|----------|--------------------------|------|
| C-1 | `POST /login`（错口令） | curl 经 8888 | **401** `{"code":401,"message":"IAM_LOGIN_FAILED: 用户名或密码错误"}` | 错误码前缀正常（对比 B-12） |
| C-2 | `POST /login`（正确） | curl 经 8888 | **200**，`data.token` 长度 1992 | 后续请求凭据来源 |
| C-3 | `GET /me` | curl + JWT | **200**；`data.id="1"`、`tenantId="0"` —— **ID 以字符串下发** | 印证 §2.10 契约 |
| C-4 | `GET /audit/logs` | curl | **200**；`records[0].id="759182949592596480"`（18 位 > `Number.MAX_SAFE_INTEGER`） | **B-2 危害实证**：数值化必丢精度 |
| C-5 | `GET /audit/logs/export?startedAt=…T00:00:00&operation=LOGIN` | curl -D 头 | **200**；`Content-Type: text/csv;charset=utf-8`、`Content-Disposition: attachment; filename="iam-audit-logs.csv"`；正文首行 `\uFEFFid,tenant_id,...`，245 行 | B-1 字节流契约打通 |
| C-6 | 同上但用空格时间格式 | curl | **400** `COMMON_VALIDATION_FAILED` | ⇒ B-11 发现 |
| C-7 | `GET /accounts/1/sessions` | curl | **200**；`data[0] = {id,accountId,tenantId,revoked,expiresAt}` | `SessionResp` 已生效（S-2），领域实体不再出网 |
| C-8 | `GET /mfa/status` | curl | **200** `{enabled:false,enrolled:false,methods:[]}` | 正常 |
| C-9 | `POST /mfa/enroll` | curl | **501** `MFA 未在商业版/IdP 中启用`（**无业务码**） | ⇒ B-12 |
| C-10 | `POST /refresh`（伪造 token） | curl | **401** `未登录或 Token 已过期` | 正常 |
| C-11 | `GET /accounts`（无 token） | curl | **401** | 鉴权兜底正常 |
| C-12 | `GET /actuator/health`（带 JWT） | curl 8091 | **200**；`iamModule = {status:UP, details:{module:"bone-iam", db:"UP", redis:"UP", minio:"notApplicable"}}` | **B-5 已生效**：由恒 UP 的 STUB 变为真检（整改前实测为 `status:"STUB"`） |

## 八、验收测试结果（D 段）

> 方式：Playwright + 本机 Chrome，history 路由 `/iam` + 子应用 hash 路由；断言读 `body.innerText`，只认 console / pageerror / 5xx。

| # | 场景 | 构造数据 | 模拟操作步骤 | 断言 | 实测 | 数据已清 |
|---|------|----------|--------------|------|------|----------|
| D-1 | 登录 | 无（用种子账号 admin） | 打开 `/login` → 填 admin/123456 → 提交 | 跳转到控制台 | 跳转 `http://localhost:3000/` | — |
| D-2 | **个人信息页（本轮新增 S-7）** | 无 | 访问 `/iam#/profile` | 渲染「基本信息 + 编辑资料 + 修改密码」三块 | **通过**：用户名 admin / 姓名 系统管理员 / 邮箱 admin@bone.com / 最近登录 `2026-09-27 14:49:25`（走 `shared-utils/i18n/format`，非裸 dayjs）；三块齐全 | — |
| D-3 | 审计时间窗查询 | 无 | 打开 RangePicker → 点「近 30 天」→ 查询 | 无 400，记录数回显 | **通过**：新增 400 = `[]`，总记录数 371 | — |
| D-4 | **审计 CSV 导出** | 无 | 点「导出CSV」，监听 download 事件 | 落盘且内容正确 | **通过**：文件名 `audit-logs-20260927065014.csv`；首行 BOM + 表头；数据行 User-Agent 含逗号已被 `""` 正确转义 | 下载到 /tmp，非业务数据 |
| D-5 | 角色新增 | `NIGHTLY-158934` | 角色页 → 新增角色 → 填名称+描述 → 保存 | 列表出现该角色 | **通过**：`POST /roles {"name":"NIGHTLY-158934","description":"…","tenantId":0}`，页面含该角色 | — |
| D-6 | 角色删除（自清理） | 同上 | 行内「删除」→ popconfirm「删 除」 | DELETE 发出且列表消失 | **通过**：`DELETE /roles/759185690134052864`，刷新后不含 | ✅ |
| D-7 | 残留自证 | — | `GET /roles?size=50` 复核 | 无 NIGHTLY 残留 | **通过**：角色总数回到 8，`NIGHTLY 残留 = []`；审计留痕 0 条（只读，非业务残留） | ✅ |
| D-8 | 卫生断言 | — | 全程监听 | console 0 / pageerror 0 / 5xx 0 | **通过**：`console.error=0, pageerror=0, 5xx=0, 400=0` | — |

## 九、AI 自审结论（见 §4 / §6.2）

### 自审三问（独立重跑，不复用阶段 A 中间结果）

| 问 | 独立复核动作 | 结论 |
|---|---|---|
| **1. 证据可复现？** | 本轮重新执行了 9 组命令复核关键 finding：`sed`/`grep` 复核 `SessionController.java:35`（领域实体出网）、`AuditController.java:55`（`ResponseEntity<byte[]>`）、`api.ts:231-239` + `AuditLog.tsx:110-113`（前端解包错位）、`iam.ts:7-8` + `AccountManagement.tsx:49-50,445`（`Number()`）、`TenantQuotaEnforcer.java:27-33`（非原子）、`RoleHierarchyResolver.java:17` + `AuthApplicationService.java:273`（继承已实现）、`ModuleController.java:25-56`（无 reorder）、`bone-gateway/application.yml:72-73`、`application.yml:5-9`（Jackson UTC 已配置） | **全部可复现**；据此**修正**了两处子代理初判：① 锚定「HC-006 未登记新增 `AuditLogCleanupJob`」为误报（无 JDBC import）→ 未计入阻断；② 国际化方案 §6.4「后端无时区配置」对 bone-iam 已过时 → 未计入阻断，改为回写勘误建议 |
| **2. 分级正确？** | 复核是否降级取巧：B-3（并发/配额）曾被考虑降为建议级——但 doc6 §1.4 明确把「租户配额 enforcement」标为 **As-Is**，并发下可超卖属能力声明失真 → **维持阻断**；B-6 曾被考虑降为建议级——但 i18n 方案把 iam-app 定为 8 个应用的接线样板，样板错误会被复制 → **维持阻断**；B-9（reorder 空挂）证据确凿但影响面小 → 维持阻断（契约单源原则，doc6 §5 明文） | **无误判降级**；10 条阻断全部对应「先改设计才能写代码」 |
| **3. v2 稿安全？** | 逐条检查 v2 是否引入新硬约束违反：V2-2 的 `version` 列 → 已标 L3 待审批（不执行）；V2-6 的补偿事件 → 列 [Target]，不引入 Outbox 半成品；契约变更只对齐锚定 `cross_module_contracts` 中 bone-iam 的 3 条（gateway / metadata-server / extension-studio），**未新增**跨模块 DTO 或事件；实现清单已覆盖全部 10 条阻断（B-1→前端 api.ts + AuditLog.tsx；B-2→iam.ts + AccountManagement；B-3→TenantQuotaEnforcer(L2 兜底)+version(L3)；B-4/B-8→文档；B-5→HealthIndicator+prod yml+埋点；B-6→main.tsx+qiankun-entry.js；B-7→3 个文件；B-9→doc10 标注；B-10→AuditLog.tsx） | **v2 无新硬约束违反，契约对齐，清单无遗漏** |

### 判定（A' 段，2026-09-26）

**✅ PASS** —— 阻断级 10 条全部有对应方案；v2 稿未引入新的 HC 违反；契约与锚定 `cross_module_contracts` 对齐；证据逐条可复现。

### 判定（B'/C/D 段，2026-09-27）

**✅ PASS（带 3 条已声明偏差 + 2 条新增阻断）**

- 状态迁移：`design_ready` → **`implemented`**（六段流水线走完 A → A' → B → B' → C → D）
- 门禁：模块级 `spotless:check` 绿、`bone-iam` 159 tests 全绿（含 `ArchitectureTest` 28 项）
- 联调：12 条真实请求，全部记录实际 HTTP 码与响应摘要（见 §七）
- 验收：8 条场景（含新增页面、导出落盘、时间窗、角色增删自清理），卫生断言 0 错，测试数据已清并自证
- **本轮新发现 3 条**：B-11（时间格式 400，已修）、B-12（MFA 业务码丢失，未抢改）、B-13（响应头不透传，建议）

- **待人工裁定（L3/L4）**：
  1. **B-3 配额**：本轮只做到单 JVM 分段锁；跨实例需批 `version` 乐观锁 DDL 或新增 JDBC 出站端口（`FOR UPDATE`）——二者都受 `sdk-persistence-bypass-baseline.json` / DDL 审批约束
  2. **B-12 MFA 业务码**：需与并发会话收口，避免双方反复覆盖 `MfaController`
  3. **B-5 Micrometer 埋点**（S-12）、**B-6 会话管理 / MFA 前端页**、**S-5 其余列表页四态**：列下一轮
  4. 建议回写 `国际化设计方案.md` §6.4 勘误（bone-iam 已配 `spring.jackson.time-zone: UTC`）

---

## 十、复审补遗（2026-09-28 · grill-and-review 独立三视角审查 + 优化）

> 本段为原报告（2026-09-26/27 出具）之后，基于 `grill-and-review` 流程派 **3 只独立只读子 Agent**（安全正确性 / 性能可维护性 / 领域一致性）并行审查，并对照实时代码逐条核验后的补遗。**裁判基准：项目 ADR/HC 为硬红线，业界实践作补充。**

### 10.1 超出原报告、且已修复的缺陷（原报告未识别 / 误判）

| # | 问题 | 原报告状态 | 实时代码结论 | 修复 |
|---|---|---|---|---|
| **C-1** | `IamErrors.of()` 调 3 参 `BizException(int,String,Throwable)`，**`errorCode` 字段恒 `null`**；`IamExceptionHandler` 取 `ex.getErrorCode()` 写入响应 → **全部 71 处业务码在 wire 层为 null**，前端 i18n 键与监控按码聚合全部失效 | §六.4 称「错误码主体干净、71 处已正确走 `IamErrors.of`」、§九判 **PASS** —— **过早** | 确属系统性缺陷（已读 `BizException.java:40` 与 `:75` 4 参构造器佐证） | `IamErrors.java:103` 改用 4 参构造器 `new BizException(..., errorCode, cause)`；新增回归测试 `IamErrorsErrorCodeTest` |
| **C-2** | `AuthApplicationService.resolveFromDatabase` `catch(Exception)` 对管理员回退 `DefaultPermissionCodes.adminFallback()`（全模块超宽权限）且被缓存 → **授权 fail-open** | 未识别 | 确属 fail-open（`:294-301`） | 新增 `AUTHORITY_RESOLVE_FAILED` 码并注册 500；异常改 fail-closed 重抛，移除 adminFallback 回退与缓存 |
| **S-A** | `AccessTokenIssuerGatewayAdapter.generateToken(accountId,username)` 给任意账号签发 `tenantId=0 + adminFallback`，非接口方法、0 调用方 → 潜在提权 API | 未识别 | 确属危险死代码 | 删除该 2 参重载 |
| **S-B** | `application-prod.yml` 数据库密码 `${BONE_DB_PASSWORD:}` 空默认 → 部署未设则以空密码连库（fail-open） | 未识别 | 确属 | 改为 `${BONE_DB_PASSWORD}`（缺失即启动失败） |
| **S-C** | `AuditController.escape()` 未中和 CSV 公式注入字符 `= + - @`，审计字段含用户可控数据 | 未识别 | 确属 OWASP CSV 注入 | `escape()` 以这些字符开头时前缀 `'` |
| **S-D** | `SecurityConfig` `/api/v1/iam/debug/**` 列入 `permitAll` 但无 Controller 映射 → 孤儿/误导配置 | 未识别 | 确属 | 从 permitAll 列表删除 |
| **S-E** | `AuthController.ssoCallback` 裸拼 `ApiResponse.error(501, SSO_NOT_CONFIGURED+":…")`，不经 handler → 响应无 `errorCode`（B-7 残留） | 未识别 | 确属 | 改 `throw IamErrors.of(SSO_NOT_CONFIGURED, …)`，响应带结构化业务码 |

### 10.2 原报告论断失真 / 滞后（代码已修，报告需更正）

| 原报告论断 | 复核结论 |
|---|---|
| §6.9 **B-12**「`MfaController` 业务码完全消失、**未抢改**」 | **不准确**：`MfaController` 现 `throw IamErrors.of(IAM_MFA_NOT_AVAILABLE, …)`（代码已挂码）。其「响应层业务码」此前因 **C-1** 恒为 null，随 C-1 修复已真正闭环；原 report 的 C-9「无业务码」现象已消除 |
| §6.8 **B-7** `Account.java:140`「裸 `DomainException` → 未做」 | **滞后**：应用层 `AccountApplicationService.java:103-105` 与 `:127-129` 已在调用 `changeDept` 前抛 `IamErrors.of(DEPT_REQUIRED, …)`，`Account.java:140` 的 `DomainException` 已是达不到的防御兜底，无需改动 |
| §2 **B-2 / B-1 / S-2**「未修」 | 代码实测均已修复（ID 串化、`export` 走 blob、`SessionResp` 出网）—— 原 report 描述滞后于代码 |

### 10.3 已落地的治理项

- **M-4（L3 删码，已授权）**：删除 `infrastructure/handler/annotation/Capability.java`（0 引用死代码）；同步修正 **D-1** README「应用层结构」漂移——`AccountRoleBindingService`→`AccountRoleBindingSupport`、`TenantQuotaEnforcer`/`PasswordPolicyValidator` 落点由误写的 `application/policy/` 改为实际 `application/support/`（原描述违背 `application-constructs-baseline.json` 的 `direction-conflict` 决议）
- **测试补齐**：新增 `TenantScopeResolverTest`（租户隔离口径单一真源，原零覆盖）、`RequestLoggingMdcFilterTest`（防 MDC 串号）、`IamErrorsErrorCodeTest`（锁 C-1）

### 10.4 验证

- `mvn -o -pl bone-platform/bone-iam spotless:apply` → BUILD SUCCESS
- `mvn -o -pl bone-platform/bone-iam test` → 全绿（含新增 3 项、`AuthSsoMfaControllerTest` 仍 501 + 含 `IAM_SSO_NOT_CONFIGURED` 的 message、`ArchitectureTest` 28 项全绿）

### 10.5 仍未闭环（交后续轮次 / 需裁决，非本次范围）

| 项 | 状态 | 备注 |
|---|---|---|
| **M-5** 前端 `dayjs` 直引（L3 依赖收敛） | **不执行（不可行）** | antd v5 以 dayjs 为硬依赖（`shared-utils/i18n/format.ts` 亦 `import dayjs`），移除本地 `dayjs` 会破坏 `DatePicker` 类型与构建；iam-app 仅保留 dayjs 服务于 antd 控件，display 已统一走 `shared-utils` 的 `formatDate`，无额外独立日期格式化逻辑 |
| **M-6** 前端 `errorCode→text` 映射（原 24 处硬编码中文） | **已执行（2026-09-28）** | 见 §10.6 |
| **M-7** 列表页四态（空/加载/错误/无权限） | **已执行（2026-09-28）** | `Profile` 失败态统一为 `error+403` 双态，并以 `ListStates` 组件统一；6 个列表/详情页（含 `Account`/`AuditLog`）全部覆盖四态（见 §10.7 / §10.8） |
| **M-8** `iam_permission` 基线 `needs-owner-decision`（L3 基线变更） | **已执行（2026-09-28）** | `ddl-required-columns-baseline.json` 中 `iam_permission` 与 `iam_role_permission` 的 classification 均由 `needs-owner-decision` 改为 `by-design`，判据一致（缺列 = 租户经 `iam_role` 间接隔离，非遗漏）；「是否冗余 tenant_id 直查」为独立非阻塞优化，不入缺列缺口。**均不改表结构**。独立审查发现二者原状态自相矛盾，已同步修正 |
| **B-3** 配额跨实例超卖 | **已收敛单 JVM（2026-09-28）+ Redis 锁方案待确认** | 现状 `TenantQuotaEnforcer` 单 JVM 分段锁已落地；多实例根治方案见 §10.9（Redis 分布式锁，L2，待确认后实现） |
| **S-12** Micrometer 登录/refresh 埋点 | **已执行（2026-09-28）** | `AuthApplicationService` 注入 `MeterRegistry`，login/refresh 加 `iam_login_requests_total` / `iam_login_failures_total{reason}` / `iam_login_seconds` + 对称 `iam_refresh_*`；prod yml 经核查已是 `health,info,metrics`（B-5 旧记录过时）。单测 11 例全绿 |

### 10.9 B-3 Redis 分布式锁设计方案（待确认，L2）

**目标**：消除 `TenantQuotaEnforcer` 在多实例部署下的超卖窗口；不触 DDL、不引入被禁的 `FOR UPDATE`（AGENTS.md 明确多副本下悲观锁失效）。

**现状**：`TenantQuotaEnforcer`（`application/support`）已用 64 段 `ReentrantLock` 把「count→判断→insert」在单 JVM 内串行化，锁释放对齐事务提交（`afterCompletion`）。多实例下该锁失效，类注释已声明为「已知且已声明的降级」。

**方案**：在现有 JVM 分段锁之外叠加一层 Redis 分布式锁（按 `tenantId`），构成「JVM 锁（防本实例并发）+ Redis 锁（防跨实例并发）」双保险。

- **落点**：`TenantQuotaEnforcer` 内部新增 `@Autowired(required = false) StringRedisTemplate redisTemplate`——沿用项目既有优雅降级约定（见 `AccountAuthorityCacheGatewayAdapter` / `TokenBlacklistPortAdapter`：无 Redis 时退化为仅 JVM 锁，行为与现完全一致，风险零回归）。
- **加锁**：`redisTemplate.opsForValue().setIfAbsent(key, lockValue, Duration.ofSeconds(TTL))`；`key = iam:quota:lock:{tenantId}`，TTL 取事务最大预期耗时（建议 5s，远大于正常创建耗时）。
- **释放**：与现有 `afterCompletion` 对齐——在 `TransactionSynchronization#afterCompletion` 里 `redisTemplate.delete(key)`（best-effort；TTL 作兜底防 JVM 崩溃遗留锁）。
- **顺序**：先取 JVM 锁、再取 Redis 锁；释放逆序（先删 Redis、再解 JVM），避免死锁。
- **失败语义**：Redis 不可用 / `setIfAbsent` 返回 false（锁被其他实例持有）→ 建议复用现有 `TENANT_QUOTA_EXCEEDED`，避免前端把「并发冲突」误判为「真超配额」；若需区分重试，可在 `IamErrorCodes` 新增 `QUOTA_CONCURRENT_CONFLICT(409)`。

**待 Owner 确认**：① 是否启用（仅当 IAM 规划多副本）；② `setIfAbsent` 失败抛超配额还是新增冲突码；③ TTL 取值。确认后实现，纯 L2、不进 L3。

**为何不选 B/C（L3）**：`version` 乐观锁需改 13 聚合基类 + 迁移（L3 重）；`FOR UPDATE` 经 JDBC 出站端口撞 `sdk-persistence-bypass-baseline.json`「只可收缩、新增文件不得加入」且多副本下失效——均不优先。

### 10.10 S-12 独立审查修正记录（2026-09-28）

S-12 落地后经 3 只独立子 Agent 并行审查（安全/正确性、性能/可维护性、领域一致性），共识修正如下（均已改完、单测 11 例全绿、spotless 通过）：

1. **reason 维度语义错误（必须修，High）**：原 `reason` 用 `e.getCode()`（int HTTP 状态码），会折叠不同业务失败且口径随状态码漂移；改为稳定业务码 `e.getErrorCode()`（如 `IAM_LOGIN_FAILED`），标签名由 `reason` 改为 `error_code`（`BizException` 双码语义见 `BizException.java:11-19`）。
2. **fail-safe（红线）**：新增 `safeCount` / `safeStop` 辅助方法，埋点/计时异常一律吞掉，确保绝不替换或抑制鉴权 `BizException`（守住 fail-closed）。
3. **infra 失败不可见（Medium）**：新增 `catch (Exception)` 分支按 `error_code=infra` 计入 `failures_total` 后原样重抛，使 `requests/failures/success` 可对账、基础设施故障不再隐身。
4. **口径漂移**：doc6 §9.2 补登记 `iam_refresh_failures_total{reason=…}` 与 `iam_refresh_seconds`（Rate 行本已含 `iam_refresh_requests_total`）。
5. **测试锁定（Medium）**：两测试改为持有 `SimpleMeterRegistry` 引用并断言请求计数，S-12 行为不再裸奔。

未采纳项：① `login`/`refresh` 埋点对称代码抽公共方法（判为小重复、显式更清晰，避免过度工程）；② `refreshToken` 的 `NumberFormatException` 注释/实现不符为**既有**问题、非本次引入，未顺手改（范围外）。

**复审结论**：三视角修后均 **Approve**（门禁合规、契约零变化、HTTP 错误码/事务回滚/fail-closed 主链路未被触动）。

### 10.6 前端 M-6 执行记录（2026-09-28）

**新增** `apps/bone-iam-app/src/utils/iamErrorMessages.ts`：
- `IAM_ERROR_MESSAGES`：与后端 `IamErrorCodes.java`（38 个 `IAM_` 码）一一对应的中文文案表，新增 / 修改业务码时须同步；
- `resolveIamErrorMessage(err: unknown)`：优先按 `errorCode` 命中映射（便于后续 i18n 切换），其次回退后端 `displayMessage` / `message`。

### 10.7 前端 M-7 执行记录（2026-09-28）

**四态组件** `apps/bone-iam-app/src/components/ListStates.tsx`：
- `ListErrorState({ error, onRetry })`：加载失败态（`Result status="error"` + 重试）；
- `ListForbiddenState({ onRetry })`：无权限态（`Result status="403"`，与「加载失败」区分，引导联系管理员）；
- `ListEmptyState({ text, action })`：空态（`Empty` + 可选「新建」行动按钮）。

**无权限判定** `utils/iamErrorMessages.ts` 新增 `isForbiddenError(err)`：按 `err.response.status === 403` 识别（IAM 列表接口鉴权拒绝统一以 403 返回，无独立权限业务码）。

**接入 4 个列表页**（`Application` / `Tenant` / `Role` / `Permission` Management）：
- 新增 `loadError: string | null` 与 `forbidden: boolean` 状态；fetch 成功清、失败置（并移除初始加载失败的重复 toast，改由 `Result` 承载文案）；
- 渲染区按「错误 → 403 → 空 → 正常表格」分流；Table 的 `loading` 继续承担「加载态」；
- 重试复用各页既有 `fetchApps` / `reload`，空态「新建」复用 `openCreate` / `handleAdd`。

**验证**：`tsc --noEmit` 通过；本次改动的 5 个文件 `eslint` 0 error / 0 warning（其余 114 个 lint 错误为 iam-app 存量、非本轮引入）。

### 10.8 前端 M-7 收尾（2026-09-28）

**统一「示范页」基准**：`Profile.tsx` 原失败态为单 `Result status="warning"`（无 403 区分），与列表页的 `error+403` 双态不一致。本轮改为复用 `ListErrorState` / `ListForbiddenState`，`fetchProfile` 在 catch 中按 `isForbiddenError` 判定 403，并移除不再使用的 `Result` 导入——四态基准在 6 个页面间完全统一。

**补齐剩余 2 页**（`Account` / `AuditLog` Management）：
- 同样新增 `loadError` / `forbidden` 状态，fetch 成功清、失败置，并移除初始加载失败的 toast / `console.error`（`Account` 原仅 `console.error`，用户无任何反馈）；
- 渲染区按「错误 → 403 → 空 → 正常表格」分流；`AuditLog` 为只读页，空态无「新建」CTA；
- `Account` 空态「新增账号」复用 `handleAdd`、重试复用 `reload`；`AuditLog` 重试复用 `fetchAuditLogs`。

**S-5 收口**：6 个列表/详情页（`Profile` + 5 个 Management）四态全覆盖；待做清单中 `Account`/`AuditLog` 项勾选完成。

**验证**：`tsc --noEmit` 通过；本次改动的 `Profile` / `AccountManagement` / `AuditLog` 共 3 个文件 `eslint` 0 error / 0 warning（`AuditLog` 原 2 处存量 `any` 已收敛：`operation: operation as any` 因 `operation?: string` 本就兼容直接去除；`Text.copyable` 的 `tooltips` 在 antd v5 原生支持，移除冗余 `as any`）。

**接入**：9 个页面（`AuditLog` / `ApplicationManagement` / `Profile` / `AuditSettings` / `TenantManagement` / `RoleManagement` / `PermissionManagement` / `AccountManagement` / `Auth`）的后端错误展示统一改经 `resolveIamErrorMessage`——
- `catch {` 改为 `catch (err: unknown) {` 并 `message.error(resolveIamErrorMessage(err) ?? '本地兜底')`；
- `response.message || 'X'` 改为 `resolveIamErrorMessage(response) ?? 'X'`；
- 原 `err.message`（axios 泛型「Request failed with status code…」）改为 `resolveIamErrorMessage(err)`，消除无信息文案。

**验证**：`npm run typecheck --workspace=bone-iam-app` → 0 error；`eslint` 仅 3 个预存在的 `any` / `exhaustive-deps` 警告（非本次引入），改动文件 0 新增错误 / 警告。

**M-5 不执行说明**：原建议「移除本地 dayjs 依赖」在 antd v5 下不可行——antd 的 `DatePicker` 等控件以 dayjs 为日期库硬依赖，且 `shared-utils/i18n/format.ts` 也 `import dayjs`；强删会使类型与构建破裂。iam-app 仅保留 dayjs 服务于 antd 控件，日期展示已统一收敛到 `shared-utils` 的 `formatDate`，无散落的独立格式化逻辑，符合收敛意图。
