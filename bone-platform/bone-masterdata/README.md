# bone-masterdata

主数据服务（企业主数据平台）。

## DDD 约定

- 应用模块，适用 [Bone-DDD 最终实践方案](../../doc/architecture/Bone-DDD-最终实践方案.md)。
- 出站端口：`domain/gateway/` — `MetaEntityCatalogPort`（catalog 元实体）、`MasterDataExcelImportPort`（Excel 导入）；实现在 `infrastructure/gateway/`。
- ArchUnit：`src/test/java/com/bone/masterdata/architecture/ArchitectureTest.java`；`application_no_infra` 存量已清零（2026-05-21）。

### 命名例外（§0.2 登记，2026-08）

- **既有风格**：CommandHandler 命名为 `{Action}{Entity}Handler`（如 `CreateMasterDataEntityHandler`、`DeleteMasterDataEntityHandler`），不带 `*CommandHandler` 后缀（ArchUnit `command_handler_naming` 冻结基线内）。
- **覆盖范围**：新增同类 CommandHandler 遵循本模块既有风格**不视为新违规**（避免冻结基线「只收缩不扩张」被持续违反，见[主文档 §0.2 命名冲突裁决路径](../../doc/architecture/Bone-DDD-最终实践方案.md#02-存量不符合规范代码的处理)）。
- **迁移计划**：目标全量对齐主文档 §23（`*CommandHandler` 后缀）。随功能重构**逐类更名**（更名时同步 Controller 注入与冻结基线收缩）；当前不做一次性批量改名，避免大 diff 与回归风险。
- **拆除条件**：全部 Handler 更名完成且冻结基线收缩后，本条例外删除。
