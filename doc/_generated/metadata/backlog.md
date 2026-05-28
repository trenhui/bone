# 元数据模块 [Target] / [Vision] Backlog

> **生成时间**：2026-05-28T01:19:07Z（UTC）  
> **维护源**：[`backlog.yaml`](../../../tools/metadata-compliance-collector/backlog.yaml)

| Tier | ID | 项 | 引用 | 跟踪 |
|------|-----|-----|------|------|
| **Target** | `catalog-if-match-etag` | catalog PUT If-Match / 412（AIP-154） | 详设 §5.A | 与 meta_*.version 字段同步 |
| **Target** | `catalog-idempotency-key` | publish / fields:allocate Idempotency-Key 窗口 | 详设 §5.A · 复用 extension StudioIdempotencyStore | P1 |
| **Target** | `catalog-publish-scope` | publish 拆出独立 metadata:publish scope（当前临时复用 write） | 详设 §5.0 · IAM §4.3 | 与 IAM 权限码登记同步 |
| **Target** | `catalog-problem-detail` | 失败信封统一改 RFC 7807 ProblemDetail（含 META_* 错误码） | 详设 §5.A · Bone-API §11 | P1 |
| **Target** | `catalog-redis-cache` | 已发布 meta_entity Redis 短 TTL 缓存 | 详设 §7 | QPS 达标后 |
| **Target** | `runtime-query-params` | runtime 列裁剪 / 过滤 / 排序 query 参数 | 详设 §5.A · §5.3.2 | P1 |
| **Vision** | `entity-versions-api` | GET /entities/{id}/versions 版本历史 | 详设 §5.1 P1 | META-00x |
| **Vision** | `smartql-http` | SmartQL / 规则 HTTP 暴露 | 详设 §2.4 · SmartMeta §9 | engine-core 已具备库能力 |
| **Vision** | `metadata-template-rest` | meta_code_template REST（归口 generator） | 详设 §5.5 | 与 §8 generator 对齐 |
| **Target** | `engine-sdk-meta-table-executor` | sdk MetaTableExecutor SPI，engine 收敛离 JDBC | ADR-0015 收敛路线 | P1 |