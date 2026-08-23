# Tasks: 增强模块（扩展前端/文件/通知/生成器）

## 1. 扩展前端对齐
- [x] 修正 `extension-app/App.tsx` `/logs` 路由 → SandboxManagement（日志内嵌页，消费 execution-logs/audit-logs）
- [x] 核对与 extension-studio REST 契约一致（后端 `/api/v1/extension/execution-logs` + `/audit-logs` 已完备）

## 2. 文件服务重建（bone-file）
- [x] 新建 `bone-platform/bone-file` 模块（pom + FileApplication + application.yml）+ 接入 bone-platform modules + `PlatformApiPaths.FILE_V1`
- [x] `FileStorageService` 抽象 + `MinioFileStorageService`（MinIO SDK，兼容 S3）
- [x] `FileController`（上传/下载/删除/test，`/api/v1/file/files`）

## 3. 通知通用化（bone-notification）
- [x] 新增 `AlertChannelType.IN_APP` + `NotificationMessage` 聚合 + `NotificationMessageRepository` + `InAppNotificationChannel`
- [x] 新增 `WebhookNotificationChannel`（HTTP POST）+ 修复 SmsAlertChannel 未实现 AlertChannel 接口缺陷
- [x] `NotificationController`（分页 + 未读计数 + summary + 标记已读，`/api/v1/notification/messages`）

## 4. 生成器模板引擎（studio-generator）
- [x] 补 entity.ftl / controller.ftl / repository.ftl 模板（freemarker 已引入，FileGenerator 渲染）

## 5. 校验
- [x] bone-file 编译 + ArchUnit 6/6；bone-notification 编译 + ArchUnit；studio-generator 编译
- [x] 前端 extension-app tsc 通过（路由修正无错误）
