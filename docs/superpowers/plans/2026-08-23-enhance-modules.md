---
archived-with: 2026-08-23-enhance-modules
status: final
---
# Plan: 增强模块（扩展前端/文件/通知/生成器）

> 对应 `openspec/changes/enhance-modules/tasks.md`。
> 设计：`docs/superpowers/design/enhance-modules.md`

## 1. 扩展前端对齐
- [x] `extension-app/App.tsx` `/logs` 路由从 PluginManagement 改为 SandboxManagement（日志内嵌页）
- [x] 核对后端 execution-logs/audit-logs 契约（已完备，前端 api 已有）

## 2. 文件服务重建（bone-file）
- [x] 新建 `bone-platform/bone-file` 模块（pom + FileApplication + application.yml）+ 接入 bone-platform modules + PlatformApiPaths.FILE_V1
- [x] `FileStorageService` 抽象 + `MinioFileStorageService`（MinIO SDK 8.5.7，兼容 S3）
- [x] `FileController`（POST 上传 / GET 下载 / DELETE 删除 / GET test）

## 3. 通知通用化（bone-notification）
- [x] `AlertChannelType.IN_APP` 枚举 + `NotificationMessage` 聚合 + `NotificationMessageRepository` + `InAppNotificationChannel`
- [x] `WebhookNotificationChannel`（HTTP POST）+ 修复 SmsAlertChannel 未实现 AlertChannel 接口缺陷
- [x] `NotificationController`（分页 + 未读计数 + summary + 标记已读）

## 4. 生成器模板引擎（studio-generator）
- [x] 补 entity.ftl / controller.ftl / repository.ftl 模板到 resources/templates（供 FileGenerator 渲染）

## 5. 校验
- [x] bone-file 编译 + ArchUnit 6/6；bone-notification 编译 + ArchUnit；studio-generator 编译
- [x] 前端 extension-app tsc 通过（路由修正无错误）
