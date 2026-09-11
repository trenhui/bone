# bone-masterdata

主数据服务（企业主数据平台）。

## DDD 约定

- 应用模块，适用 [Bone-DDD 最终实践方案](../../doc/architecture/Bone-DDD-最终实践方案.md)。
- 出站端口：`domain/gateway/` — `MetaEntityCatalogPort`（catalog 元实体）、`MasterDataExcelImportPort`（Excel 导入）；实现在 `infrastructure/gateway/`。
- ArchUnit：`src/test/java/com/bone/masterdata/architecture/ArchitectureTest.java`；`application_no_infra` 存量已清零（2026-05-21）。

### 命名差异（2026-08）

- **既有风格**：CommandHandler 命名为 `{Action}{Entity}Handler`（如 `CreateMasterDataEntityHandler`、`DeleteMasterDataEntityHandler`），不带 `*CommandHandler` 后缀（ArchUnit `command_handler_naming` 冻结基线内）。
- **v5.0 口径**：Handler 后缀属于 Advisory 工程风格，不用于证明 DDD 语义；新增代码优先保持模块内部一致。
- **迁移计划**：是否全量改为 `*CommandHandler` 由模块 Maintainer 决定。随功能重构逐类更名时同步 Controller 注入与冻结基线收缩；当前不做一次性批量改名。
- **拆除条件**：全部 Handler 更名完成且冻结基线收缩后，本段删除。
