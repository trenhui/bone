# bone-masterdata

主数据服务（企业主数据平台）。

## DDD 约定

- 应用模块，适用 [Bone-DDD 最终实践方案](../../doc/architecture/Bone-DDD-最终实践方案.md)。
- 出站端口：`domain/gateway/` — `MetaEntityCatalogPort`（catalog 元实体）、`MasterDataExcelImportPort`（Excel 导入）；实现在 `infrastructure/gateway/`。
- ArchUnit：`src/test/java/com/bone/masterdata/architecture/ArchitectureTest.java`；`application_no_infra` 存量已清零（2026-05-21）。
