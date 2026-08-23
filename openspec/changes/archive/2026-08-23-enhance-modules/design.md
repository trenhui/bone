# Design: 增强模块（扩展前端/文件/通知/生成器）

## Context
增强模块：extension-app 后端 80% 无前端且 `/logs` 路由错位指向 PluginManagement（后端实际 `/execution-logs`+`/audit-logs`）；bone-file 0%（规划中）；bone-notification 25%（仅告警通道 Alert）；studio-generator 80%（StringBuilder 拼接，无模板引擎）。

## Decision 1：扩展前端对齐
- 修正 `extension-app` 路由 `/logs` → 真实日志页（execution-logs/audit-logs）；确认与 extension-studio REST 契约一致。

## Decision 2：文件服务重建
- `bone-file` 新建 Spring Boot 模块（端口待定），抽象 `FileStorageService`：MinIO/S3/OSS 实现，复用 iam 的 MinIO client 依赖。
- 提供上传/下载/删除 REST（`/api/v1/file`）。

## Decision 3：通知通用化
- `bone-notification` 扩展：新增 In-App 站内信（`NotificationMessage` 聚合 + 未读计数）、Webhook 通道（复用现有 `AlertChannel` 机制扩展）。
- 提供统一 `send` 入口（站内信/邮件/Webhook/告警）。

## Decision 4：生成器模板引擎
- `studio-generator` 引入 FreeMarker（或 Velocity）替代 StringBuilder 拼接；模板放 `resources/templates`；生成器读取模板 + 元数据模型渲染前后端代码。

## Risks
- bone-file 新建模块需接入现有 Maven 多模块 + CI。
- 模板引擎引入需评估与现有生成器产物格式兼容。
