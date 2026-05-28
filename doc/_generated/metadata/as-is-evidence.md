# 元数据模块 As-Is 证据

> **生成时间**：2026-05-28T01:19:07Z（UTC）  
> **收集器**：`tools/metadata-compliance-collector/collect.py`

| ID | 能力 | 摘要 |
|----|------|------|
| `meta-ddl-five-tables` | meta_* 五表在 bone-init.sql | DDL 5/5 表 |
| `catalog-rest-controllers` | catalog REST（Meta*CatalogController） | 源码 × 3 |
| `eav-fields-api` | EAV fields:search|allocate（MetadataController） | EAV 1 · catalog 3 · runtime 1 · 源码 × 1 |
| `runtime-crud-api` | 模式 B RuntimeRecordController + OpenAPI | 源码 × 1 |
| `runtime-jdbc-service` | JdbcRuntimeRecordService + CatalogRuntimeEntityProvider | engine × 1 · ADR |
| `generator-catalog-snapshot` | generator CATALOG_SNAPSHOT 直读 meta_* | 源码 × 2 |
| `catalog-abstract-entity` | catalog 聚合根 extends AbstractEntity（ADR-0016） | 源码 × 2 · ADR |
| `catalog-runtime-preauthorize` | catalog + runtime Controller @PreAuthorize（metadata:read|write） | EAV 1 · catalog 3 · runtime 1 |
| `catalog-runtime-201-location` | catalog + runtime POST 201 Created + Location（AIP-133） | created: EAV 1 · catalog 3 · runtime 1 |
| `eav-allocate-201` | fields:allocate 返回 201（EAV 局部符合 AIP-133） | 源码 × 1 |