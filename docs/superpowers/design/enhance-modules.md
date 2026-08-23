---
archived-with: 2026-08-23-enhance-modules
status: final
---
# Design Doc: 增强模块（扩展前端/文件/通知/生成器）

## 1. 背景与目标

阶段4增强四类缺口：extension-app `/logs` 路由错位指向 PluginManagement；bone-file 规划占位无实现；bone-notification 仅告警无站内信/Webhook；studio-generator 有 freemarker 但 templates 空无 .ftl 模板。

## 2. 关键决策

### Decision 1：extension 路由修正
- `extension-app/App.tsx` L68 `/logs` 从 `PluginManagement` 改为 `SandboxManagement`（日志功能已内嵌该页，消费 listAuditLogs/listExecutionLogs）。后端 `/api/v1/extension/execution-logs` + `/audit-logs` 契约已完备，无需改动。

### Decision 2：bone-file 文件服务重建
- 新建 `bone-platform/bone-file`（parent=bone-platform），加入 modules 列表。
- 抽象 `FileStorageService` 接口：`upload(String bucket, String objectName, InputStream in, long size, String contentType)` / `download(...)` / `delete(...)` / `testConnection()`。
- MinIO 实现 `MinioFileStorageService`（复用 bone-iam 的 MinioStorageClientImpl 模式，MinIO SDK）。
- `FileController`（`PlatformApiPaths.FILE_V1 + "/files"`）：POST 上传 / GET 下载 / DELETE 删除。新增 `PlatformApiPaths.FILE_V1 = "/api/v1/file"`。

### Decision 3：notification 通用化
- `bone-notification`（包 `com.bone.platform.alert`）新增：
  - `AlertChannelType.IN_APP` 枚举值。
  - `NotificationMessage` 聚合（`@Table("ntf_message")`，title/content/level/userId/read/handleType/createdAt），`NotificationMessageRepository`。
  - `InAppNotificationChannel`（implements AlertChannel，channelType()=IN_APP，落库站内信）。
  - `WebhookNotificationChannel`（implements AlertChannel，channelType()=WEBHOOK，HTTP POST）。
  - `NotificationController`（`/api/v1/notification/messages`）：分页查询 + 未读计数 + 标记已读。
  - 修复 `SmsAlertChannel` 未实现 AlertChannel 接口的缺陷。

### Decision 4：studio-generator 模板引擎落地
- `studio-generator` 已有 freemarker config + `FileGenerator` 接口 + 3 个实现（entity/controller/repository）。
- 补 `.ftl` 模板到 `src/main/resources/templates/`（entity.ftl / controller.ftl / repository.ftl），使 `CodeGeneratorServiceImpl` 的 StringBuilder 拼接逐步迁移到 freemarker 渲染（新增一条走 FileGenerator 的生成路径，或补模板供 FileGenerator 使用）。

## 3. 目录结构（新增）

```
bone-platform/bone-file/
  pom.xml
  src/main/java/com/bone/file/
    FileApplication.java
    infrastructure/storage/FileStorageService.java    # 抽象
    infrastructure/storage/MinioFileStorageService.java
    adapter/web/controller/FileController.java
  src/main/resources/application.yml

bone-notification/.../alert/
  domain/notification/NotificationMessage.java
  domain/notification/vo/NotificationHandleType.java
  domain/repository/NotificationMessageRepository.java
  application/service/NotificationService.java
  channel/InAppNotificationChannel.java
  channel/WebhookNotificationChannel.java
  adapter/web/controller/NotificationController.java

studio-generator/src/main/resources/templates/
  entity.ftl
  controller.ftl
  repository.ftl
```

## 4. 风险

- bone-file 新建模块需接入 maven 多模块 + 依赖 MinIO SDK 版本（bone-parent 可能未管理，需显式版本）。
- notification 站内信聚合需 @EnableSqlRepositories 支持（bone-notification 是否启用 SQL 仓储待确认）。
- generator 模板渲染需与现有生成产物格式兼容。

## 5. 验收对照

- extension `/logs` 指向真实日志页。
- bone-file 上传/下载/删除 REST 可用。
- notification 站内信 + 未读计数 + Webhook 通道可用。
- generator 模板引擎（.ftl）可用。
