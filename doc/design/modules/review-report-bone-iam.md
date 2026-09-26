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
- [ ] L2 | `bone-frontend/apps/bone-iam-app/src/pages/{Account,Role,Permission,Tenant,AuditLog,Application}Management.tsx` | 补四态（空/加载/错误/无权限）（S-5）
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

## 六、代码复核结果（B' 段填写）

| # | 复核项 | 方法 | 结果 | 处置 |
|---|--------|------|------|------|
| — | — | — | **未执行（本报告止于 A/A' 段）** | B' 段按 §6.2 七项逐条填 |

## 七、联调验证结果（C 段填写）

| # | 场景/API | 验证方法 | 实际 HTTP 码 + 响应摘要 | 备注 |
|---|----------|----------|--------------------------|------|
| — | — | — | **未执行（本报告止于 A/A' 段）** | C 段按锚定 `exposed_apis` 逐条填 |

## 八、验收测试结果（D 段填写）

| # | 场景 | 构造数据 | 模拟操作步骤 | 断言 | 实测 | 数据已清 |
|---|------|----------|--------------|------|------|----------|
| — | — | — | — | — | **未执行** | — |

## 九、AI 自审结论（见 §4 / §6.2）

### 自审三问（独立重跑，不复用阶段 A 中间结果）

| 问 | 独立复核动作 | 结论 |
|---|---|---|
| **1. 证据可复现？** | 本轮重新执行了 9 组命令复核关键 finding：`sed`/`grep` 复核 `SessionController.java:35`（领域实体出网）、`AuditController.java:55`（`ResponseEntity<byte[]>`）、`api.ts:231-239` + `AuditLog.tsx:110-113`（前端解包错位）、`iam.ts:7-8` + `AccountManagement.tsx:49-50,445`（`Number()`）、`TenantQuotaEnforcer.java:27-33`（非原子）、`RoleHierarchyResolver.java:17` + `AuthApplicationService.java:273`（继承已实现）、`ModuleController.java:25-56`（无 reorder）、`bone-gateway/application.yml:72-73`、`application.yml:5-9`（Jackson UTC 已配置） | **全部可复现**；据此**修正**了两处子代理初判：① 锚定「HC-006 未登记新增 `AuditLogCleanupJob`」为误报（无 JDBC import）→ 未计入阻断；② 国际化方案 §6.4「后端无时区配置」对 bone-iam 已过时 → 未计入阻断，改为回写勘误建议 |
| **2. 分级正确？** | 复核是否降级取巧：B-3（并发/配额）曾被考虑降为建议级——但 doc6 §1.4 明确把「租户配额 enforcement」标为 **As-Is**，并发下可超卖属能力声明失真 → **维持阻断**；B-6 曾被考虑降为建议级——但 i18n 方案把 iam-app 定为 8 个应用的接线样板，样板错误会被复制 → **维持阻断**；B-9（reorder 空挂）证据确凿但影响面小 → 维持阻断（契约单源原则，doc6 §5 明文） | **无误判降级**；10 条阻断全部对应「先改设计才能写代码」 |
| **3. v2 稿安全？** | 逐条检查 v2 是否引入新硬约束违反：V2-2 的 `version` 列 → 已标 L3 待审批（不执行）；V2-6 的补偿事件 → 列 [Target]，不引入 Outbox 半成品；契约变更只对齐锚定 `cross_module_contracts` 中 bone-iam 的 3 条（gateway / metadata-server / extension-studio），**未新增**跨模块 DTO 或事件；实现清单已覆盖全部 10 条阻断（B-1→前端 api.ts + AuditLog.tsx；B-2→iam.ts + AccountManagement；B-3→TenantQuotaEnforcer(L2 兜底)+version(L3)；B-4/B-8→文档；B-5→HealthIndicator+prod yml+埋点；B-6→main.tsx+qiankun-entry.js；B-7→3 个文件；B-9→doc10 标注；B-10→AuditLog.tsx） | **v2 无新硬约束违反，契约对齐，清单无遗漏** |

### 判定

**✅ PASS** —— 阻断级 10 条全部有对应方案；v2 稿未引入新的 HC 违反；契约与锚定 `cross_module_contracts` 对齐；证据逐条可复现。

- 状态迁移：`pending` → `design_ready`
- **下一步**：等待人工卡点 `doc/design/approvals/bone-iam.yaml`（或 `doc/design/modules/bone-iam/_review-approved.yaml`）后进入阶段 B
- 需人工确认的三个关键点：
  1. **B-3 并发方案选哪条**：L2 兜底（`FOR UPDATE`）先行，还是直接批 L3 的 `version` 乐观锁 DDL？
  2. **B-1 导出契约以谁为基准**：保留后端字节流（前端改 blob，推荐）还是改后端包 `ApiResponse<base64>`？
  3. **S-7 前端缺口范围**：本轮只补「个人信息/改密」，还是连会话管理 / MFA 一起补？
