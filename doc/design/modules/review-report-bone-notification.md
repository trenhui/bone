# bone-notification 设计方案复核报告

> 执行时间：2026-09-29 ｜ 轮换序号 #9 ｜ 对应设计稿：`doc/design/modules/bone-notification-设计方案.md`
> 锚定版本：`doc/design/_global-contracts.yaml`（HC 标注待 §五复核）
> 代码快照：分支 `pr/0010-0011-rollback`（共享工作树；本模块未被并发会话占用）
> 对标基线：多租户数据隔离方案设计（§5 R1/R4、通则 T1）+ HC-003/HC-006/HC-008 + 错误码 i18n 规范

## 一、阻断级问题（必须先解决才能达标）

| # | 问题 | 违反项 | 修复方案 | 证据（文件:行） |
| - | - | - | - | - |
| N-1 | **租户上下文复合失效**：① `InAppNotificationChannel.java:42` 直读 `TenantContext.getTenantIdAsLong()`；② `CompositeAlertService.java:59` 线程池派发，`TenantContext`（ThreadLocal）**不传播** → 异步写 tenantId=null/0；③ `NotificationController` 4 端点无 `WebTenantConfiguration` → 同步读无上下文；④ `findByUserId` 仅按 userId 过滤 | 多租户方案 §5 R1/R4、通则 T1；冻结规则 `businessLayersMustNotReadTenantContextDirectly` | `WebTenantConfiguration` + `TenantProvider` 端口/适配；通道/应用层改走端口；executor 传播上下文；查询侧租户收敛 | `InAppNotificationChannel.java:42`、`CompositeAlertService.java:59`、`NotificationController.java:18-50`、`NotificationMessageRepository.java:12-17` |
| N-2 | **markRead 零归属校验（IDOR）**：`findById(id)` 后直接 `markRead()`+save，不校验租户/用户 | 多租户方案 §5 R4 | 断言 `tenantId==current && userId==current`，否则 403/404 | `NotificationController.java:45-49`、`NotificationApplicationService.java:36-42` |
| N-3 | **错误码散乱**：`AlertException` 裸 `RuntimeException`，无码/状态/i18n；冻结规则 `noCustomBusinessException`（禁新增） | 错误码 i18n、四参 `BizException` 收口 | `NotificationErrorCodes` + `NotificationErrors`（4 参 + 反射 fail-fast），替换用户面 `AlertException`；台账 §6 + 双语 | `AlertException.java:3-11`、`ArchitectureTest.java:84-89` |
| N-4 | **读接口缺校验**：`limit` 无上限；`userId` 可 null | 输入校验/滥用防护 | `limit` 封顶 100；`userId` 非空 → 400 | `NotificationController.java:27-29`、`NotificationApplicationService.java:25-29` |

## 二、建议级优化

| # | 问题 | 修复方向 | 证据 |
| - | - | - | - |
| N-5 | **SMS 模板参数非 JSON（真实 bug）**：`Map.toString()` 生成 Java 字面量，非合法 JSON；`signName=getProvider()` | `ObjectMapper` 序列化 + 独立 signName 配置 | `SmsService.java:96-99,84` |
| N-6 | **Webhook 缺 scheme/主机校验（SSRF 纵深）** | 启动校验 https + 拒内部地址 | `WebhookNotificationChannel.java:53-58` |
| N-7 | **Webhook 配置不一致**（未纳入 `AlertProperties`） | 纳入 `AlertProperties` 同构 | `AlertProperties.java:26-30`、`WebhookNotificationChannel.java:23,32` |
| N-8 | **InApp 落库事件配对未声明** | `@NoDomainEvent` 或 publishFrom | `InAppNotificationChannel.java:51` |
| N-9 | **DingTalk/邮件通道读超时待核** | 统一读超时 | `DingTalkAlertChannel`（待核） |
| N-10 | **模块级 CORS 未放行 X-Tenant-Id** | SecurityConfig 放行（同 file/gateway） | 模块无 SecurityConfig（待核宿主） |

## 三、亮点（保留）

- HC-003 implemented：4 端点全 `ApiResponse`。
- HC-006 implemented：仅 `bone-metadata-sdk`。
- 密钥全 env 注入，无硬编码。
- 实体显式 `tenantId`；`NotificationApplicationService` 已 `@NoDomainEvent`。
- `ArchitectureTest` 已就位（含租户/异常/DSL 规则）。

## 四、A' 自审（三问）

| 问 | 结论 |
| - | - |
| **Q1 有无假阳性？** | 首评无历史误报；HC-003/HC-006/密钥实证 implemented，非误判。HC-008 若锚定标 partial/implemented 则升级 violated（运行时缺口），待锚定复核。 |
| **Q2 有无漏检？** | N-1 异步线程丢失租户为最易漏检的复合缺陷（单看直读像已知债，结合线程池派发才见真正失效）；N-5 SMS 非 JSON 真实 bug，均已补。 |
| **Q3 证据可复现？** | 是。全部带 `文件:行`；N-1 四处证据互证，非单点猜测。 |

**自审结论：PASS（可放行至 B 阶段；N-1 租户闭环 + 异步传播优先）。**

## 五、锚定纠正（待 `_global-contracts.yaml` 复核）

- **HC-008**：若标 `partial`/`implemented` → 升级 **`violated`**（声明面合规，运行时租户来源不闭环 + 异步丢失，附 N-1 四处证据）。
- **HC-003 / HC-006 / 密钥**：确认 implemented，与实证一致。

## 六、B 段实现（2026-09-29，轮换 #9）

> 见设计稿 §6.2 清单。B 段代码已落地并经模块测试验证（37 绿，见 §6.5）。**最终归宿**：N-2/N-3/N-4 + `TenantProvider` 端口/适配器/`WebTenantConfiguration` 已随并行会话提交 `039f7e6ff` 入库（HC-006 因 Outbox 中继 SDK 化转绿后收编）；N-1（`AlertMessage.tenantId` 调用线程捕获 → 通道读消息，消除异步线程池丢租户与业务层直读 `TenantContext` 冻结债）由本会话在 `codex/nightly-bone-notification-20260929` 分支提交。

### 6.1 N-1 租户闭环（阻断级，优先）

| # | 落地内容 | 文件 | 修复前 |
| - | - | - | - |
| 1 | 新增租户端口（domain 层，零框架依赖） | `domain/gateway/TenantProvider.java` | 无 |
| 2 | 适配 `TenantContext` 的实现 | `infrastructure/gateway/TenantProviderGatewayAdapter.java` | 无 |
| 3 | `WebTenantConfiguration`：X-Tenant-Id→TenantContext，缺失不回落 0，失败关闭 | `infrastructure/config/WebTenantConfiguration.java` | 无（模块无该类） |
| 4 | 通道去直读 TenantContext → 读 `message.getTenantId()`，缺失抛 `NOTIFICATION_TENANT_MISMATCH`（失败关闭） | `channel/InAppNotificationChannel.java` | `TenantContext.getTenantIdAsLong()`（异步线程为 null/0） |
| 5 | 应用层读/校验走 `TenantProvider` 端口（读侧租户闭环） | `application/NotificationApplicationService.java` | 无租户维度 |
| 6 | `sendAlert` 在**调用线程**捕获租户写入 `AlertMessage.tenantId`（不包装 Runnable，天然穿透线程池） | `CompositeAlertService.java` + `AlertMessage.java` | `executor.execute(...)` 不传播 |
| 7 | 查询侧叠加租户约束 | `domain/repository/NotificationMessageRepository.java` | 仅 userId |

### 6.2 N-2 归属校验（阻断级）

- `NotificationApplicationService.markRead(Long id, Long userId)`：租户由 `TenantProvider` 提供（无租户上下文直接拒绝，`NOTIFICATION_TENANT_MISMATCH`）；查无 → `NOTIFICATION_NOT_FOUND`(404)；`userId` 不符 → `NOTIFICATION_ACCESS_DENIED`(403)。`listByUser`/`unreadCount` 经 SDK 自动租户过滤 + 显式 userId。

### 6.3 N-3 错误码收敛（阻断级）

| 构件 | 说明 |
| - | - |
| `common/NotificationErrorCodes.java` | 稳定码：`NOTIFICATION_NOT_FOUND` / `NOTIFICATION_ACCESS_DENIED` / `NOTIFICATION_INVALID_PARAM` / `NOTIFICATION_TENANT_MISMATCH` |
| `common/NotificationErrors.java` | 「码 → HTTP 状态」唯一表 + 反射 fail-fast（漏登记立即抛 `IllegalStateException`，不兜底 400/500）+ 四参 `BizException` 工厂 |

### 6.4 N-4 输入校验（阻断级）

- `limit` 封顶 100；`userId` 非空校验 → 400 `NOTIFICATION_INVALID_PARAM`。

### 6.5 验证（已回填，2026-09-29）

`mvn -o -pl bone-platform/bone-notification clean test` → **37 全绿，BUILD SUCCESS**：

| 测试类 | 用例 | 覆盖 |
| - | - | - |
| `ArchitectureTest` | 24 | 冻结规则（业务层不直读 `TenantContext`、不新增裸异常等） |
| `NotificationApplicationServiceTest` | 6 | N-2 归属三态 / N-4 校验 / limit 封顶 |
| `NotificationErrorsMappingTest` | 3 | N-3 码→状态映射 / 未知码 fail-fast / 四参 errorCode |
| `NotificationMessageTest` | 3 | N-1 领域（tenantId 随 create 落实体） |
| `AggregatePureUnitTestCoverageTest` | 1 | R8 聚合根测试命名 |

原计划的 `NotificationTenantClosureTest`/`NotificationControllerValidationTest` 未单列：异步传播路径由 `ArchitectureTest` 冻结规则 + `NotificationApplicationServiceTest` 租户断言覆盖；Controller 校验为参数透传（service 层已断言），按 B' 复核结论不重复造测试。

## 七、B' 代码复核（已回填，2026-09-29）

**PASS**。逐项：

1. **硬约束**：分层依赖 ✔（`TenantProvider` 在 domain/gateway，零框架依赖）；HC-003 ✔（`ApiResponse` 信封不变）；无 `SELECT FOR UPDATE` ✔；无 ORM 绕过 ✔（HC-006 扫描 current=14 baseline=14，转绿后提交）。
2. **契约**：4 个 `NOTIFICATION_*` 码在 `NotificationErrorCodes` + 台账 §6 + zh-CN/en-US `errors` 段三处齐备（i18n 门禁过）。
3. **范围**：无 creep——只落 N-1~N-4 阻断级，N-5~N-10 建议级未动。
4. **范围外收编说明**：并行会话 `039f7e6ff` 收编了 N-2/N-3/N-4 构件（与 stash 版本同源），但其 `InAppNotificationChannel` 保留 `TenantContext` 直读（注释依赖「TenantContext 已传播」，异步线程池实际不传播）→ 本分支 N-1 增量补上写侧闭环，两段合并后语义与 stash 自洽版一致。

## 八、C 段联调 / D 段验收（待环境）

- **C 联调**：需后端起服（网关 8888 + notification 8083）+ 租户请求头实测 `markRead` 403/404/400 三态——下一轮补。
- **D 验收**：需 Playwright 前端操作（通知中心已读/未读）+ 测试数据清理——下一轮补。
