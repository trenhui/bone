# 元数据模块 As-Is 证据

> **生成时间**：2026-09-21T06:49:23Z（UTC）  
> **收集器**：`tools/metadata-compliance-collector/collect.py`

| ID | 能力 | 摘要 |
|----|------|------|
| `meta-ddl-five-tables` | meta_* 五表在 bone-init.sql | DDL 5/5 表 |
| `catalog-rest-controllers` | catalog REST（Meta*CatalogController） | 源码 × 3 |
| `eav-fields-api` | EAV fields:search|allocate（MetadataController） | EAV 1 · catalog 4 · runtime 1 · 源码 × 1 |
| `runtime-crud-api` | 模式 B RuntimeRecordController + OpenAPI | 源码 × 1 |
| `runtime-jdbc-service` | JdbcRuntimeRecordService + CatalogRuntimeEntityProvider | engine × 1 · ADR |
| `generator-catalog-snapshot` | generator CATALOG_SNAPSHOT 直读 meta_* | 源码 × 2 |
| `catalog-abstract-entity` | catalog 聚合根 extends AbstractEntity（ADR-0016） | 源码 × 2 · ADR |
| `catalog-runtime-preauthorize` | catalog + runtime Controller @PreAuthorize（metadata:read|write） | EAV 1 · catalog 4 · runtime 1 |
| `catalog-runtime-201-location` | catalog + runtime POST 201 Created + Location（AIP-133） | created: EAV 1 · catalog 3 · runtime 1 |
| `eav-allocate-201` | fields:allocate 返回 201（EAV 局部符合 AIP-133） | 源码 × 1 |
| `catalog-if-match-entity` | meta_entity PUT/publish If-Match + 412（AIP-154） | 源码 × 11 |
| `catalog-idempotency-key` | publish + fields:allocate Idempotency-Key（24h 进程内） | 源码 × 5 |
| `metadata-problem-detail` | GlobalExceptionHandler → ApiResponse<ProblemDetail> | 源码 × 2 |
| `runtime-query-params` | runtime 列表 fields/sort/q 查询参数 | 源码 × 3 |
| `runtime-if-match` | runtime PUT If-Match（物理表 version 列） | engine × 1 |
| `catalog-runtime-entity-cache` | 已发布 RUNTIME 实体 Caffeine 缓存（默认 60s） | — |
| `metadata-publish-scope` | metadata:publish 权限码 + publish hasAnyAuthority | 源码 × 2 |
| `catalog-redis-cache` | RUNTIME 实体 Redis 缓存（配置 backend=redis + StringRedisTemplate） | 源码 × 2 |
| `catalog-idempotency-redis` | Idempotency-Key Redis 存储（配置 idempotency.backend=redis） | 源码 × 2 |
| `catalog-field-relation-version` | meta_field / meta_entity_relation version + If-Match PUT | 源码 × 2 |