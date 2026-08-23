# Proposal: 增强模块（扩展前端/文件/通知/生成器）

## 目标（Why）

平台增强模块存在四类缺口：extension-app 无前端且 `/logs` 路由错位指向 PluginManagement（真实接口是 execution-logs/audit-logs）；bone-file 规划中未建；bone-notification 仅告警通道（无站内信/Webhook）；studio-generator 用 StringBuilder 拼接无模板引擎。这是阶段4增强，补齐平台横向能力。

## 范围（What）

1. **扩展前端对齐**：修正 `extension-app` 路由 `/logs` → 真实日志页（execution-logs/audit-logs），核对与 extension-studio REST 契约。
2. **文件服务重建**：新建 `bone-file` Spring Boot 模块，抽象 `FileStorageService`（MinIO/S3/OSS），提供上传/下载/删除 REST（`/api/v1/file`）。
3. **通知通用化**：`bone-notification` 新增 In-App 站内信（`NotificationMessage` 聚合 + 未读计数）+ Webhook 通道，统一 `send` 入口。
4. **生成器模板引擎**：`studio-generator` 引入 FreeMarker 替代 StringBuilder，模板置 `resources/templates`。

## 非目标（Non-Goals）

- 不做工作流/采购引擎（PRD 未入 MVP）、不做移动端。

## 验收标准（Acceptance Criteria）

- [ ] extension `/logs` 路由对齐真实日志接口。
- [ ] file 服务可用（上传/下载）。
- [ ] notification 站内信落地。
- [ ] generator 模板引擎可用。
