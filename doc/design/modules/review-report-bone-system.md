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
- [ ] L2 | `AlertRecord.java` + 文档 | 命名口径说明/对齐（S-11）
- [ ] L2 | `SystemModuleHealthIndicator.java` | 真实校验或占位标注（S-9）
- [ ] L2 | 关键路径日志 | MDC(tenantId/traceId)（S-9）
- [ ] L2 | `domain/model` | 聚合补 `version`（S-10）

### 前端文件清单
- [ ] L2 | `MonitorAlert.tsx` + `services/api.ts` | 字段对齐 `metricName`/`thresholdValue`/`alertLevel`，修复创建 400（S-1）
- [ ] L2 | `SystemConfig.tsx` + `services/api.ts` | 编辑字段对齐 `configValue`/`configType`（S-2）
- [ ] L2 | `qiankun-entry.ts` / `createBoneMicroAppRenderer.tsx` | token 注入（S-4）
- [ ] L2 | `App.tsx` | 路由守卫/权限码（S-4）
- [ ] L2 | dayjs 配置 + 页面 | 国际化（S-5）
- [ ] L2 | 全局 | 错误边界 + 403 态（S-6）
- [ ] L2 | `shared-types` | `id: string`（S-7）
- [ ] L2 | `apiClient.ts` | 错误码→locale 映射（S-8）

### 待审批清单（L3/L4）
- [ ] L3 | DDL 补列（HC-008） | 影响全租户门禁，需架构师审批
- [ ] L3 | doc1 控制台能力 + `cnsl_*` 表裁决 | 是否实现/下线

### 联调前置条件
- 后端 :8083 已运行（JWT 鉴权）；前端修复 token 注入后可联调。

## 六、代码复核结果（B' 段填写）
> 本晚仅完成 A 设计 + A' 自审，B 实现待下一轮。占位待填。

## 七、联调验证结果（C 段填写）
> 待 B 阶段后执行。

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
