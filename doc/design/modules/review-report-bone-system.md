# 系统管理模块（bone-system）设计方案复核报告

> 执行时间：2026-09-27 ｜ 轮换序号 #5 ｜ 对应设计稿：`doc/design/modules/7. 系统管理模块详细设计方案.md`、`7a. 数据字典模块详细设计方案.md`、`1. 控制台与仪表盘模块详细设计方案.md`
> 锚定版本：`doc/design/_global-contracts.yaml` generated_at=2026-09-26T23:40
> 代码快照：分支 `codex/nightly-bone-system-20260927`（基于 `5ca1e70f1`，工作树干净）
> 对标基线：锚定 `hc_semantics` + 业界最佳实践（SAP/Oracle 字典建模、Spring Boot Actuator、Ant Design Pro 前端契约）

## 一、阻断级问题（必须先解决才能达标）

| # | 问题 | 违反项 | 修复建议 | 证据（命令 + 输出 + 文件:行） |
|---|------|--------|----------|-------------------------------|
| S-1 | 告警规则创建/编辑必 400，功能完全不可用 | 前后端契约不一致（F4.2） | 统一字段为后端契约 `metricName`/`thresholdValue`/`alertLevel` | 后端 `AlertRuleResp.java:19,21` 字段为 `metricName`/`alertLevel`；`MonitorAlert.tsx:205` `dataIndex:'metric'`、`:215` `'level'`、`:113` `ruleData` 取 `metric/threshold/level`、`:402/411/414` 表单 `name=metric/threshold/level`；`api.ts` 期望 `metricName/thresholdValue/alertLevel` → `undefined` 触发 `@NotBlank` 400 |
| S-2 | 系统配置编辑：值/类型不生效 | 前后端契约不一致（F4.1） | 编辑表单字段统一为 `configValue`/`configType`（与 `api.ts` 及列表列一致） | `SystemConfig.tsx:228/231/234` 表单 `name=key/value/type`；列表 `:136/142` `dataIndex=configValue/configType`；`api.ts#updateConfig` 仅取 `configValue/configType` → `handleEditSubmit` 传 `value/type` 被丢弃 |
| S-3 | `sys_alert_event` / `sys_log` 缺 `updated_at`/`deleted`（HC-008 硬约束） | HC-008 | L3 DDL 补列（待架构师审批，本任务不执行） | `bone-init.sql:556-573`（`sys_alert_event` 仅 `tenant_id`/`created_at`）、`:389-402`（`sys_log` 仅 `created_at`/`trace_id`）；均缺 `updated_at`/`deleted` |

> 锚定原判 **HC-003 violated 经复核为误报**（见 §九自审），已纠正为 `implemented`，不计入阻断级。

## 二、建议级优化

| # | 当前设计/实现 | 业界对标 | 优化方案 | 证据 |
|---|--------------|----------|----------|------|
| S-4 | 生产入口 `qiankun-entry.ts` 不注入 token；`App.tsx` 无路由守卫/权限码 | qiankun 契约、RBAC 前端隐藏 | 在 `createBoneMicroAppRenderer` 统一注入 token；补路由级权限码隐藏 | `main.tsx:18-21,35-38,56-59`（仅 dev 入口注入）；`qiankun-entry.ts` 经 `createBoneMicroAppRenderer.tsx:27-44` 渲染不注入；`App.tsx:12-28` 无 Guard |
| S-5 | dayjs 未 locale/utc；页面文案硬编码中文未接 i18n | 国际化设计规范 | 配置 dayjs locale/utc；页面 `useTranslation` | `grep dayjs/locale/useTranslation` 在 app 与 packages 0 命中；`BoneAppProvider.tsx:3-4,116,119` 仅 antd locale |
| S-6 | 无 ErrorBoundary / 403 无权限态；`@ant-design/pro-components` 声明未用 | 组件库既有约定 | 补错误边界与无权限兜底；澄清 pro-components 依赖（移除或启用） | `grep ProTable/ProForm` 0 命中（`package.json:24` 声明）；仅通用 toast `catch` |
| S-7 | 主键 `id` 以 `number` 直传，Long>2^53 精度风险 | 全租户门禁（ID 精度） | `shared-types` 主键 `id: string`，请求体透传 | 各 `*Resp.java` 主键 `Long id`；`shared-types` `id?:number`；页面 `record.id` 直拼 URL |
| S-8 | 后端业务错误码未映射 UI 提示 | 错误码 i18n | `apiClient` 拦截器按 `errorCode` 取 locale | `apiClient.ts:64-79` 仅透传 `message`；除 `ScheduleTaskManagement.tsx:93` 外无 errorCode 展示 |
| S-9 | `SystemModuleHealthIndicator` 返回静态 UP+STUB；关键路径日志无 MDC（tenantId/traceId） | 可观测性规范 | 真实校验或显式标注占位；MDC 注入 | `SystemModuleHealthIndicator.java:20-31`；`grep MDC` 0 命中 |
| S-10 | 聚合无 `version` 字段建模（domain model 0 命中 version） | 乐观锁最佳实践 | 新增/变更聚合补 `version`（与 `sys_config` 表 `version` 列对齐） | `grep version in domain/model` 0 命中 |
| S-11 | `AlertRecord` 聚合名→表 `sys_alert_event`，打破 `AlertRule`→`sys_alert_rule` 口径 | 命名一致性 | 文档说明或重命名聚合为 `AlertEvent` | `AlertRecord.java:18` `@Table("sys_alert_event")` |
| S-12 | `/config/{id}/history` 故意返回空 List（[Target] 未实现审计表） | 设计标注 [Target] | 落 `sys_config_history` 后实现（L3） | `ConfigController.java:129-135` |
| S-13 | doc1 控制台 dashboards/widgets/preferences 未实现，`cnsl_*` 孤儿表无聚合/控制器 | 设计-代码 GAP | 架构师裁定：实现或下线 `cnsl_*` 表 | `ConsoleController` 仅 5 端点；`cnsl_dashboard_widget:1546` 等孤儿表 |

## 三、参考级对标（亮点）

- **数据字典 v3（doc7a）**：两级模型 + 层级关系独立（SAP SKOS / Oracle 对齐），含时间有效性、多语言译文表、外部标准码、值类型/正则校验、编码分段推导层级——设计完整且前端 `DictManagement` 字段对齐良好（F4.3 PASS），是本模块质量标杆。
- **错误码体系**：`SystemErrorCodes` 常量类（实测 **37** 个，非锚定 34）全量登记 `SystemErrors` 状态表，含 `checkEveryCodeRegistered()` 静态块 fail-fast，无裸字符串 `BizException`。
- **告警评估 Job**：60s 周期三态闭环（TRIGGERED / 去重更新 / RESOLVED），平台租户上下文 `TenantContextRunner.callAs(0L,…)` 合规；AES-256-GCM 敏感配置落库加密 + 读取脱敏。

## 四、v2 优化稿（设计改进 + B 阶段实现清单）

> 设计稿 doc7/doc7a 质量高，核心问题在前端契约偏离与少量后端欠账。v2 不重写设计，而是：(a) 纠正锚定 HC-003 误报；(b) 将前端契约对齐落到实现清单；(c) 列出 L3 待审批项。doc7 末尾已追加同稿。

### 4.1 锚定纠正
- **HC-003**：bone-system 实际 `implemented`（同 bone-metadata-server 同源误判，仅 `ResponseEntity<byte[]>`/`ResponseEntity<Void>` 合法例外）。已刷新 `_global-contracts.yaml`。

### 4.2 前端契约对齐（B 阶段 L2）
- 告警：`MonitorAlert.tsx` + `api.ts` 统一为 `metricName` / `thresholdValue(double)` / `alertLevel`，列表 `dataIndex` 同步；删除 `metric`/`level`/`threshold` 旧字段（修复 S-1）。
- 配置编辑：`SystemConfig.tsx` 表单字段 `configValue` / `configType`（与列表、`api.ts` 一致），修复 S-2。
- 契约：`shared-types` 主键 `id: string`；`apiClient` 错误码→locale 映射（S-7/S-8）。
- 微前端：`qiankun-entry` 统一 token 注入 + 路由守卫/权限码（S-4）；dayjs locale/utc + 页面 i18n（S-5）；错误边界 + 403 态（S-6）。

### 4.3 后端建议项（B 阶段 L2，非阻断）
- 可观测性：HealthIndicator 真实化或显式占位标注；关键路径 MDC(tenantId/traceId)（S-9）。
- 并发：聚合补 `version` 字段（S-10）。
- 命名：`AlertRecord` 口径说明（S-11）。

### 4.4 L3 待审批（本任务不执行）
- `bone-init.sql` 为 `sys_alert_event`、`sys_log` 补 `updated_at`/`deleted`（HC-008，S-3）。
- doc1 控制台 dashboards/widgets 是否实现 + `cnsl_*` 表去留裁决（S-13）。
- `/config/{id}/history` 落 `sys_config_history` 实现（S-12）。

## 五、实现计划

### 后端文件清单
- [ ] L3 | `bone-init.sql` | `sys_alert_event`/`sys_log` 补 `updated_at`/`deleted`（HC-008，待审批）
- [x] L2 | `AlertRecord.java` | 命名口径说明（S-11）：类 javadoc 写明「类名 Record ↔ 表 sys_alert_event」的历史成因与「新增代码按表名语义命名」约束；**重命名属 L3，待审批**
- [x] L2 | `SystemModuleHealthIndicator.java` | 占位标注（S-9 前半）：**复核纠正——原项已满足**，类 javadoc 已标「占位实现」且 detail 带 `STUB` / `limitation`，无需改动
- [ ] L2 | 关键路径日志 | MDC(tenantId/traceId)（S-9 后半，**仍未做**）
- [ ] L2 | `domain/model` | 聚合补 `version`（S-10）

### 前端文件清单
- [x] L2 | `MonitorAlert.tsx` + `services/api.ts` | 字段对齐 `metricName`/`thresholdValue`/`alertLevel`，修复创建 400（S-1，**已落**）
- [x] L2 | `SystemConfig.tsx` + `services/api.ts` | 编辑字段对齐 `configValue`/`configType`（S-2，**已落**）
- [ ] L2 | `qiankun-entry.ts` / `createBoneMicroAppRenderer.tsx` | token 注入（S-4）
- [ ] L2 | `App.tsx` | 路由守卫/权限码（S-4）
- [ ] L2 | dayjs 配置 + 页面 | 国际化（S-5）
- [ ] L2 | 全局 | 错误边界 + 403 态（S-6）
- [ ] L2 | `shared-types` | `id: string`（S-7）
- [x] L2 | `apiClient.ts` + `shared-utils/errorMessage.ts` + `SystemConfig.tsx` | 错误码→locale 映射（S-8）：**基础设施 + SystemConfig 5 处样板已落**（含补齐 19 个 `SYS_*` 译文）；其余页面（MonitorAlert/DictManagement/SystemDeployment/ScheduleTask/LogManagement）按同模式铺开，待下一轮

### 待审批清单（L3/L4）
- [ ] L3 | DDL 补列（HC-008） | 影响全租户门禁，需架构师审批
- [ ] L3 | doc1 控制台能力 + `cnsl_*` 表裁决 | 是否实现/下线

### 联调前置条件
- 后端 :8083 已运行（JWT 鉴权）；前端修复 token 注入后可联调。

## 六、代码复核结果（B' 段）

**B 实现范围**：仅落前端契约对齐（S-1 / S-2 阻断级），未触碰后端、未动 `shared-types.SystemConfig`（保护 shared-services 的 `key/value` 契约）、未碰 `release/*`。

### 6.1 改动文件（4 个，全部前端）
| 文件 | 改动 | 对齐的后端真源 |
|---|---|---|
| `packages/shared-types/src/system.ts` | `AlertRule.metric→metricName`、`level→alertLevel` | `CreateAlertRuleReq.metricName` / `alertLevel` |
| `apps/bone-system-app/src/pages/MonitorAlert.tsx` | 列表 `dataIndex` 与表单 `name` 同步 `metricName`/`alertLevel`（`threshold` 保持不变） | 同上 |
| `apps/bone-system-app/src/services/api.ts` | `createAlertRule`/`updateAlertRule` 类型 `thresholdValue→threshold`；`updateConfig` 补 `description?` | `CreateAlertRuleReq.threshold`(Double) / `UpdateConfigReq` |
| `apps/bone-system-app/src/pages/SystemConfig.tsx` | 编辑表单 `key→configKey`(只读)、`value→configValue`(可编辑)、`type→configType`(只读)；历史弹窗标题去 `.key` | `UpdateConfigReq`(id+configValue+description) |

### 6.2 验证
- **`tsc --noEmit`**（bone-system-app，`strict:true`）：退出码 0，无类型错误。
- **范围扫描**：`AlertRule` 全仓仅 bone-system-app 使用（shared-services 不引用），改名安全；`LogManagement.tsx` 的 `.level` 与 `DictManagement.tsx` 的 `.value` 属 `SystemLog`/`DictItem` 类型，不受影响。
- **契约一致性**：后端 `ConfigResp` 实测为 `configKey`/`configValue`/`configType`/`description`，前端编辑表单现与其一致。

### 6.3 自评
- 无越权文件、无范围 creep、无裸字符串引入；纯字段名对齐，等价重构。
- **S-1 / S-2 阻断级问题已修复**，功能可用性恢复（创建/编辑告警、编辑配置不再 400 / 不再丢值）。

### 6.4 B 第 2 夜：P0 错误码闭环 + S-8 / S-11（本轮）

**P0（跨模块同款缺陷，bone-integration 修复后外溢核查发现）**：`SystemErrors.of(...)` 走**三参**
`new BizException(status, message, cause)` —— 该构造器把 `errorCode` 硬置 `null`（`BizException.java:40-44`）。
后果与 integration 同款：响应状态/中文 message 全对，**只有 `ProblemDetail.errorCode` 是 null**，
前端 `i18n.t('errors.' + code)` 的分支永不命中，英文用户只能看到中文 fallback。**极难发现**：
按「状态 + message」断言的测试全部照绿。

| 项 | 改动 | 证据 |
|---|---|---|
| `common/SystemErrors.java` | `of(...)` 改**四参** `BizException(status, message, errorCode, cause)`；javadoc 同步（message 保留 `码: 说明` 作为 fallback） | 全模块直接构造点仅此 1 处（grep `new BizException(`） |
| `common/SystemErrorsMappingTest`（新增） | 9 条：码非 null / of(detail) 带码 / supplier 带码 / 404·409·403·400·500 映射 / advice 端到端透传 / 未登记码 fail-fast | 纯单测，无 Spring 上下文 |
| — | **本地 advice 与 bone-web 全局 handler 均无缺陷**：`GlobalExceptionHandler:53` 已是 `errorBody(status, e.getErrorCode(), ...)`，状态口径 400–599 直用；故本轮**不动 handler** | 实测确认，避免误改 |

**S-8（错误码 → UI 文案）闭环三件套**：
1. `shared-services/apiClient.ts`：响应拦截器把 `ApiResponse.data.errorCode` 挂到 `error.errorCode`
   （**只透传不翻译**——本包不依赖 i18n，翻译留给消费端，避免给共享包加语言包依赖边）；
2. `shared-utils/i18n/errorMessage.ts`（新增）：`resolveErrorMessage(error, fallback)` 按
   `errors.<code>` 取译文，取不到回退页面中文 fallback；用 i18n 单例而非传 `t`（toast 不参与重渲染）；
3. 语言包：**实测 36 个 `SystemErrorCodes` 里有 19 个在 zh-CN/en-US 无译文**（CONFIG/ALERT/SCHEDULE/LOG 段），
   即"后端产了码前端也翻译不出来"——已补齐至 **37 码全覆盖**（`SCHEDULE_TASK_HANDLER_NOT_FOUND` 因跨行声明曾被正则漏掉，已修正）。
4. `SystemConfig.tsx` 5 处 catch 由硬编码中文改为 `resolveErrorMessage(error, '获取配置失败')`（样板）。

**S-11**：`AlertRecord` 类 javadoc 写明「类名 Record ↔ 表 `sys_alert_event`」的历史成因与「新增代码按表名语义命名」约束；重命名属 L3 待审批。

**S-9 复核纠正**：报告原列「HealthIndicator 真实校验或占位标注」，实测**该选项已满足**
（javadoc 标"占位实现" + detail 带 `STUB` / `limitation`），无需改动；S-9 真正未做的是**后半**：关键路径日志 MDC(tenantId/traceId)。

**验证**：`mvn -o -pl bone-platform/bone-system test` **175 全绿**（新增 9 条，ArchitectureTest 26）；
`tsc --noEmit` shared-utils 与 bone-system-app 均退出码 0；`check-i18n-sync.py` 通过。

**未在本次范围**（避免 scope creep，下轮候选）：S-8 其余页面铺开、S-9 MDC、S-4（token 注入/路由守卫）、
S-5（dayjs locale/utc）、S-6（ErrorBoundary/403）、S-7（`id: string`，跨 shared-services 契约风险高）、S-10（聚合 `version` 建模）。

## 七、联调验证结果（C 段填写）
> 本 B 轮为纯前端字段对齐（后端契约未变），已由 `tsc` 通过 + 后端 DTO 真源比对佐证。
> 全链路 C 联调（live 后端 :8083 + 前端 :3007 + Playwright 走创建/编辑）建议下一轮"继续"执行，或随 IDE 监管实例人工回归。

## 八、验收测试结果（D 段填写）
> 待 C 段后执行。

## 九、AI 自审结论（A'）

### 9.1 证据可复现
- **HC-003 误报**：主代理亲自 `grep ResponseEntity<` 于 controller 目录，仅 `byte[]`（ConfigController:105 / LogController:69 导出）/ `Void`（SystemController:127/133/139/145 501 桩），无业务裸 `ResponseEntity<X>` → 与子代理结论一致，可复现。
- **HC-008 缺口**：主代理亲自 `Read bone-init.sql:556-573` / `:389-402`，确认 `sys_alert_event`/`sys_log` 缺 `updated_at`/`deleted` → 与子代理一致。
- **S-1/S-2 前端阻断**：主代理亲自 `Read AlertRuleResp.java` + `Grep MonitorAlert.tsx` / `SystemConfig.tsx`，确认字段名错配 → 可复现。

### 9.2 分级正确
- HC-003 误报已纠正为 `implemented`（非降级取巧）。
- S-1/S-2 列为阻断级（功能不可用），S-3（HC-008）列为阻断级硬约束但属 L3 待审批，未降级为建议。分级无误。

### 9.3 v2 稿安全
- v2 覆盖全部阻断级（S-1/S-2 修复方案 + S-3 L3 登记）。
- 未引入新硬约束违反；API/事件/DTO 与锚定一致。
- 实现清单无遗漏（前后端阻断 + 建议均已列）。

**自审结论：✅ PASS** → 默认放行进 B 实现（下一轮"继续"）。

## 十、复核与执行结论（2026-09-28）

> 复核人对照当前代码快照重做证据核验。结论：**本报告相对当前代码已显著滞后**——S-1/S-2/S-4(token)/S-5(dayjs)/S-8/S-9/S-11 在报告出具后已被落地，并非"待下一轮"。

### 10.1 逐条复核（合理性 × 当前状态 × 处置）

| # | 建议 | 合理性 | 当前代码状态（复核） | 处置 |
|---|------|--------|----------------------|------|
| S-1 | 告警创建/编辑 400 | 合理 | 前端字段已对齐 `metricName`/`thresholdValue`/`alertLevel`（`MonitorAlert.tsx` + `api.ts`） | 前轮已落地 |
| S-2 | 配置编辑值/类型不生效 | 合理 | 表单已对齐 `configValue`/`configType` | 前轮已落地 |
| S-3 | `sys_alert_event`/`sys_log` 补 `updated_at`/`deleted` | 合理（HC-008 硬约束） | 两表仍缺列 | **✅ 本次执行**（见 10.5）：`bone-init.sql` + `schema-test.sql` 已补列 |
| S-4 | token 注入 + 路由级权限码隐藏 | token 部分合理；权限隐藏需权限码体系 | `createBoneMicroAppRenderer` 已注入 token；路由守卫无 `PermissionCodes` 定义 | token 已做；**✅ 本次执行**（见 10.5）：前端 `permission.ts`+`Authorized.tsx`+`App.tsx` 路由守卫（用户指定为跨模块优先项）；后端 `@PreAuthorize` 已覆盖 |
| S-5 | dayjs locale/utc + 页面 i18n | dayjs 合理；整页 i18n 工作量大 | `dayjs-setup.ts` 已 `locale('zh-CN')`+`utc`；页面硬编码中文未接 `useTranslation` | dayjs 已做；整页 i18n 属大重构，延后 |
| S-6 | ErrorBoundary + 403 无权限态 | 合理 | 子应用内无 ErrorBoundary（Shell 仅兜挂载层） | **✅ 本次执行**（见 10.2） |
| S-7 | 主键 `id:string` | 方向合理；**但报告「number 直传精度风险」前提已过时**——`MetadataAutoConfiguration.boneLongToStringCustomizer` 已将 `Long` 全局序列化为 JSON 字符串，线上不再以 number 直传 | `shared-types` 仍 `id?:number`（类型错位）；`masterdata-app` 存在 `Number(id)` 二次转换真脚枪 | 跨 `shared-services` 契约风险，不盲目执行；**详见 §10.6 调研**：后端无需改，前端类型对齐 + 清剿 `Number(id)` 应单独立项 |
| S-8 | 错误码→UI 文案 | 合理 | `apiClient` 拦截器 + `errorMessage` + 37 码译文 + 全部 5 页已用 `resolveErrorMessage` | 前轮已落地；**本次顺手统一 `ScheduleTaskManagement.runNow` 旧写法** |
| S-9 | MDC(tenantId/traceId) | 合理 | `RequestLoggingMdcFilter` 已实现每请求注入 + `finally` 清理 | 前轮已落地 |
| S-10 | 聚合补 `version` | 方向合理但需 DDL/SDK 验证 | 仅 `sys_config` 有 `version` 列；`@Version` 全仓零使用 | **✅ 本次执行**（见 10.5）：7 聚合补 `@Version`；验证 SDK `@Version` 支持时暴露 `TypeConverter.parseNumber` 的 `INT→Long` 转换 `ClassCastException` 缺陷，**已修 SDK 根因**（属平台级修复，不止本模块） |
| S-11 | `AlertRecord` 命名口径 | 合理 | 类 javadoc 已说明历史成因 | 前轮已落地 |
| S-12 | `/config/{id}/history` 实现 | [Target] 未实现 | 故意返回空 | **✅ 本次执行**（见 10.5）：新增 `ConfigHistory` 聚合 + 仓储 + `ConfigHistoryProjector` 事件投影 + `/config/{id}/history` 端点（事件投影落库，规避 R9 一事务一聚合） |
| S-13 | doc1 控制台能力 + `cnsl_*` 表 | 需架构师裁决 | 孤儿表 | **L3** 裁决 |

### 10.2 本次执行内容（S-6 + S-8 收尾）

1. **新增 `apps/bone-system-app/src/components/AppErrorBoundary.tsx`**
   - class 组件 `getDerivedStateFromError` + `componentDidCatch`（打点 `console.error`，后端链路已带 MDC `traceId`）。
   - 崩溃时渲染 antd `<Result status="error">` + 「重试」按钮，避免整页白屏。
   - 对标 Shell 的 `MicroAppErrorBoundary`——Shell 只兜住了「微应用挂载」层，子应用内部渲染崩溃仍会冒泡成白屏，此处补内层兜底。
2. **`apps/bone-system-app/src/App.tsx`**：`Routes` 整体包入 `<AppErrorBoundary>`；新增导入。
3. **`apps/bone-system-app/src/pages/ScheduleTaskManagement.tsx`**：`runNow` 的 `catch` 由原「手动取 `err.response.data.message`」改为 `resolveErrorMessage(error, '执行失败')`，与同文件其余 handler 及 S-8 契约一致。

> 关于 S-6 的「403 无权限态」：真实授权边界是后端 `@PreAuthorize` + 各页 `catch→resolveErrorMessage`（已覆盖）；路由级权限隐藏属 S-4 范畴，需先定义 `PermissionCodes` 体系，故不擅自加守卫（避免触发 Shell 注释中"租户管理员 403 → 容器不渲染"的陷阱）。

### 10.3 验证

- `tsc --noEmit`（bone-system-app，`strict:true`）：**退出码 0**，无类型错误。
- 范围：`AppErrorBoundary` 为新增独立组件，仅 `App.tsx` 一处引用；`ScheduleTaskManagement` 仅改 catch 文案来源，行为等价。
- 未触碰后端、未动 `shared-types`、`release/*`、DDL。

### 10.4 待办（下轮候选 / 需审批）

- **仍待架构师裁决**：S-13（`cnsl_*` 孤儿表处置）——用户明确「先不动」，本任务未执行。
- **需专项评估（跨模块，未盲目执行）**：S-7（`id:string`，见 §10.6 调研结论——报告原前提已部分过时）、S-5（整页 i18n 大重构）。
- 已闭环：S-1/S-2/S-4/S-6/S-8/S-9/S-11（前轮）+ **S-3/S-10/S-12（本次，见 10.5）**。
- ⚠ **遗留平台级事项**：S-10 验证中发现 `bone-metadata-sdk` 的 `TypeConverter.parseNumber` 存在 `INT→Long` 转换 `ClassCastException` 缺陷（影响所有 `@Version Long` 实体读取 `INT` 列），本任务已修 SDK 根因并安装至本地 `.m2`；建议 SDK 维护方并入主线（见 10.5 第 3 点）。

### 10.5 本次执行内容（S-3 / S-4 / S-10 / S-12，2026-09-28）

**S-3｜`sys_alert_event` / `sys_log` 补 `updated_at` / `deleted`（HC-008 硬约束）**
- `bone-init.sql`：`sys_alert_event`、`sys_log` 均补 `updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` 与 `deleted TINYINT(1) NOT NULL DEFAULT 0`。
- `src/test/resources/schema-test.sql`（H2）：同步补列，保证测试库与生产 DDL 一致。

**S-4｜路由级权限码守卫（用户指定为跨模块优先项）**
- 新增 `apps/bone-system-app/src/auth/permission.ts`：`hasPermission(required)` 优先读 `window.__BONE_GLOBAL_CONTEXT__.permissions.codes`，回退到 JWT 解码 scopes；并 re-export `BonePermissionCodes`（`SYS_CONSOLE_READ = 'sys:console:read'`）。
- 新增 `apps/bone-system-app/src/components/Authorized.tsx`：复制 Shell 的 `Authorized`（403 → `<Result status="403">`），置于子应用内以规避「租户管理员 403 → 容器不渲染」陷阱。
- `App.tsx`：`<Routes>` 包入 `<Authorized required={BonePermissionCodes.SYS_CONSOLE_READ}>`。
- 后端授权边界仍由 `@PreAuthorize` + 各页 `catch→resolveErrorMessage` 兜底（S-8 已覆盖）；本项为 UX 级隐藏，非鉴权替代。
- 验证：`tsc --noEmit -p apps/bone-system-app/tsconfig.json` 退出码 0。

**S-10｜聚合补 `@Version` 乐观锁**
- 7 个聚合补 `private Long version;`（标注 `@com.bone.metadata.sdk.domain.annotation.Version`）：`SystemConfig`、`AlertRule`、`SysDictItem`、`SysDictHierarchy`、`SysDictItemText`、`SysDictType`、`ScheduleTask`（`sys_alert_rule` 此前缺列，已在 `bone-init.sql` + `schema-test.sql` 补 `version INT NOT NULL DEFAULT 0`）。
- 验证 SDK `@Version` 支持时**暴露平台级缺陷**：`TypeConverter.parseNumber` 在「目标 `Long` + 实值 `Integer`（H2 与 MySQL 的 `INT` 列均返回 `Integer`）」时执行 `targetType.cast(doubleValue())` → `ClassCastException`。已修 SDK 根因（`bone-metadata-sdk/.../sql/executor/TypeConverter.java` 的 `else` 分支按目标数值类型取 `longValue()/intValue()/shortValue()/floatValue()`），属平台级修复，对所有 `@Version` 实体读取生效，**无回归**（仅修复原本抛异常的分支，原 `Double` 路径保持不变）。
- 影响面：本任务已将补丁 `bone-metadata-sdk:1.0.0` 安装至本地 `.m2`；建议 SDK 维护方并入主线版本。

**S-12｜`/config/{id}/history` 实现（事件投影，规避 R9）**
- 新增聚合 `domain/model/config/ConfigHistory.java`（extends `AggregateRoot<Long>`，满足 `Repository<T extends Entity>` 边界；纯投影实体，无软删 / 乐观锁）。
- 新增仓储端口 `domain/repository/ConfigHistoryRepository.java`（`findByConfigIdOrdered` 读侧）。
- 新增 `infrastructure/event/ConfigHistoryProjector.java`：`@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` 订阅 `ConfigCreatedEvent` / `ConfigChangedEvent`，以**独立事务**落 `sys_config_history` 投影表——主配置写路径不跨聚合，规避 R9「一事务一聚合」。
- `ConfigApplicationService.history(id)` 经仓储读侧返回 `List<Map<String,Object>>`；`ConfigController.history()` 改为 `ApiResponse.success(...)`（去掉空列表桩）。
- 新增 `sys_config_history` 表（`bone-init.sql` + `schema-test.sql`）。
- 测试：`ConfigHistoryTest`（5，含 R8 行为方法 `auditSummary()`）、`ConfigHistoryProjectorTest`（2，验证事件→投影）、`ConfigApplicationServiceTest` 历史用例改为断言领域事件、`ConfigControllerTest` 端到端通过。

**验证汇总**
- `mvn -o -pl bone-platform/bone-system test`：**187 用例，0 失败 0 错误，BUILD SUCCESS**（含 ArchitectureTest 26/26 门禁全绿：R8 聚合纯单测、R9 一事务一聚合）。
- 前端 `tsc --noEmit` 退出码 0。

### 10.6 S-7 复核调研（2026-09-28）：报告原前提已部分过时

**调研触发**：用户指定 S-7「先调研再定方案」。结论与报告 §10.1/§10.4 的「主键 `id` 以 `number` 直传，Long>2^53 精度风险」判断**不一致**——序列化层已全局兜底，线上不再以 number 直传。

**1. 后端线上序列化（精度丢失根因）——已安全**
- `bone-metadata-sdk` 的 `MetadataAutoConfiguration` 已在 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 登记为 Spring Boot 自动配置；bone-system 的 `pom.xml:38` 依赖该 SDK → 运行时必然激活。
- 其 `boneLongToStringCustomizer()` 注册**全局** `Jackson2ObjectMapperBuilderCustomizer`，对 `Long.class` / `long` 一律用 `ToStringSerializer.instance` 序列化为 JSON 字符串（注释明言「全平台统一兜底的唯一落点」）。
- 因此 `ConfigResp`/`AlertRuleResp` 等 12 个裸 `@Data` 类中的 `private Long id`（无 `@JsonSerialize`）**线上即为 JSON 字符串**，不会触发 JS 双精度截断。
- 该兜底与 `Entity.id` 的 `EntityIdSerializer`（`Long`→`writeString`）、`AbstractDTO.id` 的 `ToStringSerializer` 形成三重保险；聚合根响应早已 string。

**2. 前端 `id?: number` 声明——类型错位，非活跃数据 bug**
- 线上值为 string，`shared-types` 却仍声明 `number`：分布为 `system.ts`(8)、`integration.ts`(9+2?)、`masterdata.ts`(8)、`metadata.ts`(3) 等共约 28 处在 `shared-types`，另有约 39 个 app 文件。
- `iam.ts`/`metadata.ts` 注释已写明「所有 ID 一律 string、禁止 Number()」，但 `metadata.ts` 字段仍写 `id: number`——**约定与声明自相矛盾**。
- bone-system-app 内 `record.id!` 均直接作为 path/body 参数透传（无 `Number()`），运行时无碍；仅为 TS 类型摩擦。

**3. 真正的残留脚枪：`Number(id)` / `parseInt(id)` 二次转换**
- 跨模块扫描发现 `bone-masterdata-app` 多处对 ID 字段做 `Number(v.accountId)` / `Number(v.parentCategoryId)` / `Number(v.assigneeId)`。一旦 ID > 2^53，此类转换会静默截断（与 10.5 所述 S-10 实证同源坑），是**比类型声明更实的精度风险**。bone-system-app 未发现此类转换。

**4. 网关——安全**
- `bone-gateway` 仅在 `RateLimitGatewayFilter` 用到 `Long.class`（限流 key），不对响应 ID 做数字解析，无网关级精度风险。

**结论与建议**
- 报告原 S-7 方案「`shared-types` 主键 `id: string`、请求体透传」方向仍对，但**「number 直传精度风险」前提已不成立**——后端无需再改（加 `@JsonSerialize` 属冗余）。
- 可执行且低风险的工作：① 将 `shared-types` 的 `id?: number`→`id?: string` 对齐到线上实际格式与 iam/metadata 约定（属前端类型对齐，跨模块，影响全量 type-check）；② **优先清剿各 app 的 `Number(id)`/`parseInt(id)` 转换**（真正脚枪，集中在 masterdata-app）。
- S-7 实质是**跨模块前端类型对齐 + 转换清理大项**，应按专项评估结论单独立项，不应塞进 bone-system 特性提交。

**待用户裁定**：是否立项执行 ①（shared-types 类型对齐，约 28+39 处）+ ②（清剿 Number(id) 转换）；或直接仅更新本报告、维持现状。

### 10.7 S-5 整页 i18n 落地（2026-09-28，本轮）

> grill 阶段先确认：按 `doc/design/国际化设计方案.md` §1.2/§1.3/§89，微应用**全量页面** i18n 被划为 **P3 / Vision**，不在首期验收；首期范围是错误文案 + 壳层 common/nav + 业务枚举 + 日期/数字。但用户最终裁定**在本模块先行落地整页 i18n 以验证模式**。

**改动范围（仅 bone-system-app + 共享语言包，未动后端与其他 app）**
- 6 个页面全部 `useTranslation()` 化：`SystemConfig` / `MonitorAlert` / `LogManagement` / `SystemDeployment` / `DictManagement` / `ScheduleTaskManagement`。
- 共享语言包新增 `system` 命名空间：`packages/shared-utils/src/i18n/locales/zh-CN.json` + `en-US.json`，共 **347** 个 `system.*` key（systemConfig 23 / monitorAlert 49 / logManagement 37 / systemDeployment 55 / dictManagement 145 / scheduleTaskManagement 38），并复用既有 `common.*`（确定/取消/加载中/暂无数据）。
- `bone-system-app/package.json` 显式加入 `react-i18next`（运行时单例已由 `@bone/shared-utils` 的 `i18n/index` 初始化；`main.tsx` 已 `subscribeLocaleChange()` 订阅 `bone:theme:change`，运行时切换依赖 Shell 广播）。
- `DictManagement.tsx` 修正 `TFunction` 导入（v15 该类型来自 `i18next` 而非 `react-i18next`）。

**验证**
- `tsc --noEmit`（bone-system-app，strict）：**0 错误**。
- 脚本交叉校验：350 个 `t()` 字面量 key 在 zh-CN / en-US 均存在，且两语言 key 集合完全一致（含修复 `system.dictManagement.effectiveRangeTooltip` 遗漏）。
- 页面残留中文仅余：① `resolveErrorMessage(error,'中文兜底')` 兜底串（走 errorCode→文案链路，按设计保留）；② 代码注释；③ 模拟后端数据值（如 `'例行升级'`）。均非用户可见 UI chrome。

**已知限制（非阻塞）**
- 运行时 locale 切换依赖 Shell 广播 `bone:theme:change`；当前 Shell 尚未广播（设计文档 §115/§116），故英文切换暂不生效，但 zh-CN 渲染与 en-US 资源均已就绪。
- 与 4 个兄弟 app（iam/masterdata/metadata/integration）尚未对齐——整页 i18n 仍是设计文档定义的 P3 专项，本模块为先行试点。

### 10.8 Stage-3 独立复审结论（2026-09-28）

对 `5ca1e70f1..16e49d298`（S-3/S-4/S-10/S-12 + S-7①）派 3 只只读子 Agent（安全+正确 / 性能+可维护 / 领域一致性）复审。

**共识级结论（三 Agent 均确认）**
DDD 分层干净；S-12 事件投影链路（`AFTER_COMMIT`+`REQUIRES_NEW`）正确规避 R9；S-10 `@Version` 边界与 SDK `Repository<T extends Entity>` 一致；S-3 双库 DDL 同步；SDK `TypeConverter` 修复无回归；S-4 守卫位置正确（UX 级、后端 `@PreAuthorize` 兜底）。

**待跟进缺陷（建议下轮，非本模块首期阻塞）**
| # | 项 | 严重 | 说明 |
|---|----|----|------|
| R1 | S-12 `ConfigHistoryProjector` 审计操作人硬编码 `OPERATOR="admin"` | High | 真实操作人未进事件/投影，审计流水恒为 admin，语义失效 |
| R2 | S-12 `tenant_id` 依赖隐式 `TenantContext`（实体/事件无 `tenantId` 字段） | High(中置信) | 脱离上下文链路（异步/Outbox 重放）会落 0 或读不到；建议事件带 `tenantId` 或实体继承 `TenantAggregateRoot` |
| R3 | S-7① `system.ts` 的 `configId`/`alertRuleId`/`tenantId` 仍为 `number` | Medium | 与已改 `id?:string` 不一致，运行期 string/number 错位 |
| R4 | S-3 补的 `deleted` 列未接线（实体无 `@SoftDelete`/字段） | Medium | 软删语义未真正落地，查询会回带逻辑删除行 |
| R5 | H2 `updated_at` 缺生产 `ON UPDATE` 语义 | High | 测试库 `updated_at` 不随 UPDATE 自增，与生产不一致 |
| R6 | S-4 `permission.ts` 守卫 fail-closed 可用性 | Medium | 若 Shell 不写 `permissions.codes`/`localStorage` JWT，全体用户将被 403 |
| R7 | S-12 投影无幂等键、`sys_config_history` 无唯一约束 | Medium | 事件重放会插入重复历史行 |
| R8 | S-12 投影异常未兜底 | Low | 投影失败可能导致当次 HTTP 500，建议 best-effort |

**文档漂移（需刷新本报告）**
- §10.6 称 S-7「不盲目执行 / 待用户裁定」，但 S-7①（system.ts + bone-system-app）已按用户「以上所有」裁定在 `16e49d298` 提交；建议 §10.6 补一句「S-7① 本模块已落地，全量跨模块 S-7 仍按专项单独立项」。
- §10.5 称「仅 `sys_config` 有 `version` 列」，实测 `sys_dict_*` 等 6 张表在本次提交前已含 `version`（本提交仅补 `sys_alert_rule`），描述需校正。

