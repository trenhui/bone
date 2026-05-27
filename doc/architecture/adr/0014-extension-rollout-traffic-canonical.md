# ADR-0014：扩展灰度字段收敛 — `config_json.traffic` 为唯一语义源

| 状态 | 已接受 |
|------|--------|
| 日期 | 2026-05-27 |
| 模块 | bone-extension-studio / bone-extension-sdk |

## 背景

`exts_extension_impl` 同时存在：

- `config_json.traffic`（0–100，SDK 路由已接线）
- `rollout_percent`（TINYINT，历史列，与 traffic 双轨）

详设 §4.3 要求 **二选一**，避免双轨语义。

## 决策

1. **唯一语义源**：`config_json.traffic`（经 `ExtensionRuntimeConfigParser` 解析）。
2. **写路径**：`StudioPersistenceConverter.toEntity` 在保存时将 `traffic` **镜像** 到 `rollout_percent`，仅供 SQL 报表/索引，**不作为** SDK 读侧输入。
3. **读路径**：SDK / `RuntimeExtensionSyncService` **只读** `config_json`；禁止从 `rollout_percent` 反写 config。
4. **API**：PATCH/PUT 更新 `config` 时，若含 `traffic`，自动刷新镜像列；单独 PATCH `rollout_percent` **不提供**。
5. **后续**：不在新功能中扩展 `rollout_percent`；列保留至大版本迁移窗口，再评估 DROP（须 expand/contract）。

## 后果

- 控制台与 OpenAPI 文档统一描述 `config.traffic`。
- Backlog `rollout-percent-traffic-merge` 关闭（以本 ADR + 现有镜像写为准）。

## 相关

- [扩展管理详设 §4.3](../../design/modules/5.%20扩展管理模块详细设计方案.md#43-路由与灰度as-is)
- `StudioPersistenceConverter`（traffic → rollout_percent 镜像）
