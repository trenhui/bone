# Change: 增强模块（扩展前端/文件/通知/生成器）

**Type**: Feature（增强模块）
**Status**: Proposed
**Source**: 用户统一优先级规划（阶段4 T13-T16）。基于扫描：extension-app 后端 80% 无前端且 /logs 路由错位；bone-file 0%（规划中）；bone-notification 25%（仅告警）；studio-generator 80%（StringBuilder 拼接无模板引擎）。
**Depends on**: 无

## Intent
补齐平台增强模块：扩展前端契约对齐、文件服务重建、通知通用化、生成器模板引擎。

## Scope (In)
- `extension-app`：与 `bone-extension-studio` 契约对齐，修复 `/logs` 路由错位（指向真实 `/execution-logs`+`/audit-logs`）。
- `bone-file`：重建文件服务（对象存储抽象 MinIO/S3/OSS，复用 iam 的 MinIO 客户端）。
- `bone-notification`：升级为通用通知中心（补站内信 In-App + Webhook 落地）。
- `studio-generator`：引入 FreeMarker/Velocity 模板引擎替代 StringBuilder。

## Scope (Out / Non-Goals)
- 不做工作流/采购引擎（PRD 未入 MVP）。
- 不做移动端。

## Assumptions
- extension-studio 后端 REST 已定义（execution-logs/audit-logs）。
- iam 已有 MinIO 客户端可复用。

## Acceptance Criteria
- [ ] extension /logs 路由对齐真实日志接口。
- [ ] file 服务可用（上传/下载）。
- [ ] notification 站内信落地。
- [ ] generator 模板引擎可用（生成前后端代码）。
