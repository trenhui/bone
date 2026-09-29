# bone-notification 设计方案（v1，从零产出）

> 执行时间：2026-09-29 ｜ 轮换序号 #9 ｜ 锚定版本：`doc/design/_global-contracts.yaml`（待 §G 复核确认）
> 代码快照：分支 `pr/0010-0011-rollback`（共享工作树，本模块未被并发会话占用）
> 对标基线：多租户数据隔离方案设计（§5 R1/R4、通则 T1「禁止隐式 0」）+ HC-003（统一响应）/HC-008（租户隔离）/HC-006（持久化唯一）/错误码 i18n 规范
> 端口：8083（与 bone-system 同上游，无独立端口）；无既有设计稿 → 本稿从代码现状复核产出 v1

## 一、模块现状（代码实证）

bone-notification 由两条互不相干的子系统组成：

1. **告警子系统**（异步后台）：`AlertService` / `CompositeAlertService` 按 `AlertLevel` 策略把 `AlertMessage` 派发到 `mail/sms/dingtalk/webhook/in-app` 通道；通道经线程池异步执行，失败仅记日志（`CompositeAlertService.java:73-75`）。
2. **站内信子系统**（用户面）：`NotificationController`（4 端点读/已读）+ `NotificationApplicationService` + `NotificationMessage`（聚合，`TenantAggregateRoot`）+ `NotificationMessageRepository`（SDK）。

| 维度 | 现状 | 结论 |
| --- | --- | --- |
| 响应信封（HC-003） | 4 个 Controller 端点全返 `ApiResponse` | **implemented**（亮点） |
| 持久化唯一（HC-006） | 仅 `bone-metadata-sdk`，无 MyBatis/JPA | **implemented**（亮点） |
| 租户隔离（HC-008） | 实体有 `tenantId`，但运行时上下文不闭环 | **violated**（阻断，见 N-1/N-2） |
| 错误码 i18n | `AlertException` 裸 `RuntimeException`，无码/状态 | **violated**（阻断，见 N-3） |
| 密钥 | `${SMS_API_KEY:}` 等 env 注入，无硬编码 | **implemented**（亮点） |

## 二、阻断级问题（必须先修才能达标）

| # | 问题 | 违反项 | 修复方案 | 证据（文件:行） |
| - | - | - | - | - |
| **N-1** | **租户上下文复合失效**：① `InAppNotificationChannel.java:42` 直读 `TenantContext.getTenantIdAsLong()`（基础设施层直读 TenantContext，是冻结规则 `businessLayersMustNotReadTenantContextDirectly` 的已知债，且为根因）；② `CompositeAlertService.java:59` `executor.execute(...)` 把通道派到线程池，`TenantContext` 为 ThreadLocal **不传播** → 异步写路径 tenantId=null/0；③ `NotificationController` 全 4 端点**无 `WebTenantConfiguration`** → 同步读路径从不建立上下文；④ `findByUserId` 只按 userId 过滤、无租户维度 | 多租户方案 §5 R1/R4、通则 T1「禁止隐式 0，须经 `TenantProvider.currentTenantIdOrNull()` 明确意图」；冻结规则 `businessLayersMustNotReadTenantContextDirectly`（存量债、禁新增） | ① 新增 `WebTenantConfiguration`（X-Tenant-Id→TenantContext，缺失不回落 0，fail-closed）；② 新增 `TenantProvider` 端口（`domain/gateway`）+ 适配（`infrastructure/gateway`），业务/通道层改走端口、去直读；③ `CompositeAlertService` 的 executor 必须传播 TenantContext（提交时捕获上下文、包装 Runnable 还原）；④ 查询侧经 SDK 自动过滤 + 显式租户断言 | `InAppNotificationChannel.java:42`；`CompositeAlertService.java:59`；`NotificationController.java:18-50`（零 WebTenantConfiguration）；`NotificationMessageRepository.java:12-17`（仅 userId） |
| **N-2** | **markRead 零归属校验（IDOR）**：`markRead(@PathVariable Long id)` → `findById(id)` 后直接 `markRead()`+save，**不校验消息是否属于当前租户/用户** → 任意已认证用户可标记任意站内信已读（横向越权） | 多租户方案 §5 R4（租户内数据须归属校验） | `markRead` 断言 `message.getTenantId()==currentTenant && message.getUserId()==currentUser`，否则抛 `NOTIFICATION_ACCESS_DENIED`(403) 或 `NOTIFICATION_NOT_FOUND`(404)；读路径 `listByUser` 叠加租户约束 | `NotificationController.java:45-49`；`NotificationApplicationService.java:36-42` |
| **N-3** | **错误码散乱**：`AlertException` 裸 `RuntimeException`，无 errorCode / 无 HTTP 状态 / 无 i18n；且 `ArchitectureTest` 已冻结 `noCustomBusinessException`（存量债、禁新增），保留/新建裸异常违反"禁止新增"；前端 `i18n.t('errors.'+errorCode)` 全链路拿不到码 | 错误码 i18n 规范、四参 `BizException` 收口（file/system/integration/studio 同口径） | 引入 `NotificationErrorCodes`（稳定码）+ `NotificationErrors`（4 参 `BizException` 工厂 + 码→状态唯一表 + 反射 fail-fast），替换用户面路径的 `AlertException`；登记台账 §6 + 双语语言包 | `AlertException.java:3-11`；`ArchitectureTest.java:84-89`（冻结） |
| **N-4** | **读接口缺输入校验 / 资源耗尽**：`list(userId, limit)` 的 `limit` 仅 `Math.max(0, limit)`，**无上限** → 可一次拉全量站内信；`userId` 为 `@RequestParam Long` 可为 null → 查询可能跨用户 | 输入校验、滥用防护 | `limit` 封顶（如 100）；`userId` 非空校验 → 400；`summary` 同步约束 | `NotificationController.java:27-29`；`NotificationApplicationService.java:25-29` |

> **N-1 影响面**：写入侧异步线程丢失租户 → 站内信落 tenantId=null/0（与 extension-studio X-1 同构的"恒写 0"缺陷）；同步读侧无上下文 → 查询命中 tenant=0 或被 SDK 严格过滤成空。二者均为缺陷，仅严重度不同，修复方式相同（闭环 + 去隐式 0）。

## 三、建议级优化

| # | 当前设计/实现 | 业界对标 | 优化方案 | 证据 |
| - | - | - | - | - |
| N-5 | **SMS 模板参数非 JSON（真实 bug）**：`SmsService.buildTemplateParam` 用 `Map.toString()` 生成 `{code=HIGH, message=...}`（Java 字面量，非合法 JSON）；`signName=getProvider()`（"Aliyun"）非注册签名 | 阿里云要求 `{"code":"HIGH",...}` 合法 JSON | 用 `ObjectMapper` 序列化；signName 取独立配置项 | `SmsService.java:96-99`、`84` |
| N-6 | **Webhook 缺 scheme/主机校验（SSRF 纵深）**：`URI.create(webhookUrl)` 直连配置 URL，无 https 强制、无内网/链路本地拦截 | SSRF 防御纵深 | 启动时校验 https + 拒绝 169.254.169.254 / 127.0.0.0/8 / 10/8 / 172.16/12 / 192.168/16 / ::1 / fc00::/7 | `WebhookNotificationChannel.java:53-58` |
| N-7 | **Webhook 配置不一致**：`AlertProperties` 只有 mail/dingtalk/sms，无 webhook 子项；通道却用 `@Value("${alert.channels.webhook.url:}")` + `@ConditionalOnProperty("alert.channels.webhook.enabled")`；且 `application.yml` 未定义该块 | 通道配置一致性 | webhook 纳入 `AlertProperties` 与其他通道同构 | `AlertProperties.java:26-30`、`WebhookNotificationChannel.java:23,32` |
| N-8 | **InApp 落库缺事件配对声明**：`InAppNotificationChannel.save` 在 infrastructure（channel 包），不经 `applicationSaveMustPairWithPublishOrExempt`；语义与已 `@NoDomainEvent` 的 `NotificationApplicationService` 应一致 | DDD 事件配对 | 显式 `@NoDomainEvent` 或 publishFrom（若将来要未读计数事件） | `InAppNotificationChannel.java:51` |
| N-9 | **DingTalk/邮件通道读超时未核**：webhook 有 `request.timeout(TIMEOUT)` 兜底读超时；DingTalk 未读，疑无读超时可能挂起 | 外呼必须读超时 | 统一外呼读超时（≤通道策略 timeout） | `WebhookNotificationChannel.java:55`；`DingTalkAlertChannel`（待核） |
| N-10 | **模块级 CORS 未显式放行 X-Tenant-Id**：宿主 8083 若未统一处理则租户头被 CORS 拦 | 跨模块租户头一致 | SecurityConfig CORS 放行 `X-Tenant-Id`（同 file/gateway 口径） | 模块无 `SecurityConfig`（待核宿主是否已统一） |

## 四、亮点（保留，不计入缺陷）

- 响应契约统一（HC-003 implemented）：4 端点全 `ApiResponse`，无裸 `ResponseEntity`。
- 无 ORM 违规（HC-006 implemented）；密钥全 env 注入。
- 实体 `NotificationMessage extends TenantAggregateRoot` 显式声明 `tenantId`（租户意图正确，缺口在运行时而非声明）。
- `NotificationApplicationService` 已 `@NoDomainEvent`，save 配对合规。
- `ArchitectureTest` 已就位（含租户/异常/DSL 规则，部分 freeze 存量债）。

## 五、A' 自审（三问）

| 问 | 结论 |
| - | - |
| **Q1 有无假阳性（误报）？** | 本轮为模块首评，无历史锚定误报需纠正；HC-003/HC-006/密钥三项经代码实证为 implemented，非误判。HC-008 在锚定文件若标 partial/implemented 则需升级为 violated（运行时缺口），待 §G 锚定复核确认。 |
| **Q2 有无漏检（假阴性）？** | N-1 的异步线程丢失租户是**最易漏检**的复合缺陷（单看 `InAppNotificationChannel` 直读 TenantContext 像普通"已知债"，但结合 `CompositeAlertService` 线程池派发才是真正失效点），已补。N-5 SMS 非 JSON bug 为独立真实缺陷，已补。 |
| **Q3 证据是否可复现？** | 是。全部结论带 `文件:行`；N-1 同时给出"直读 + 线程池派发 + 缺 WebTenantConfiguration + 仓储无租户过滤"四处证据，非单点猜测。 |

**自审结论：PASS（可放行至 B 阶段；N-1 租户闭环 + 异步传播应优先于其余 B 项）。**

## 六、v2 优化稿（B 阶段清单 + 锚定纠正）

### 6.1 锚定纠正（待 §G 复核）

- **HC-008**：若锚定标 `partial`/`implemented` → 升级 **`violated`**，附 N-1 四处证据（声明面合规但运行时租户来源不闭环 + 异步丢失）。
- **HC-003 / HC-006 / 密钥**：确认 implemented，与实证一致。

### 6.2 B 阶段实现清单（L2，非阻断优先序）

- **N-1（阻断，优先）**：`WebTenantConfiguration`（X-Tenant-Id→TenantContext，fail-closed）+ `domain/gateway/TenantProvider` + `infrastructure/gateway/TenantProviderGatewayAdapter`；`InAppNotificationChannel`/`NotificationApplicationService` 改走端口、去直读；`CompositeAlertService` executor 传播 TenantContext；查询侧叠加租户约束。
- **N-2（阻断）**：`markRead` 归属断言（租户 + 用户）→ `NOTIFICATION_ACCESS_DENIED`(403)；读路径租户收敛。
- **N-3（阻断）**：`NotificationErrorCodes` + `NotificationErrors`（4 参 `BizException` + 反射 fail-fast）；替换用户面 `AlertException`；台账 §6 `NOTIFICATION_` 段 + 双语语言包。
- **N-4（阻断）**：`limit` 封顶 100；`userId` 非空 → 400。
- **N-5（建议，真实 bug）**：`SmsService` 用 `ObjectMapper` 序列化模板参数；signName 独立配置。
- **N-6/N-7（建议）**：webhook 纳入 `AlertProperties` + 启动时 scheme/主机校验。
- **N-10（建议）**：模块级 SecurityConfig CORS 放行 `X-Tenant-Id`（若宿主未统一）。

### 6.3 L3 待审批

- N-8 站内信落库是否引入未读计数领域事件（publishFrom vs `@NoDomainEvent` 裁定）。
- N-9 DingTalk/邮件通道读超时是否需补（待核）。

## 七、B 段实现（2026-09-29，轮换 #9）

> 实现细节与验证回填至 `review-report-bone-notification.md` §六。
