# 元数据模块 [Target] / [Vision] Backlog

> **生成时间**：2026-05-28T02:36:14Z（UTC）  
> **维护源**：[`backlog.yaml`](../../../tools/metadata-compliance-collector/backlog.yaml)

| Tier | ID | 项 | 引用 | 跟踪 |
|------|-----|-----|------|------|
| **Vision** | `entity-versions-api` | GET /entities/{id}/versions 版本历史 | 详设 §5.1 P1 | META-00x |
| **Vision** | `smartql-http` | SmartQL / 规则 HTTP 暴露 | 详设 §2.4 · SmartMeta §9 | engine-core 已具备库能力 |
| **Vision** | `metadata-template-rest` | meta_code_template REST（归口 generator） | 详设 §5.5 | 与 §8 generator 对齐 |
| **Target** | `engine-sdk-meta-table-executor` | sdk MetaTableExecutor SPI，engine 收敛离 JDBC | ADR-0015 收敛路线 | P1 |