# Proposal: 统一 metadata 引擎的双重建模体系（model ↔ metadata）

## Why

`bone-metadata-engine` 物理拆分后，`domain.model` 与 `domain.metadata` 存在**同名但零重叠字段**的两套建模类（`EntityMetadata`/`SmartFieldMetadata`/`BusinessRuleMetadata`/`OperationMetadata` 等），造成引用歧义、双维护与认知负担。

深度探索确认：这不是简单重复，而是**两套不同建模体系**：

| 类 | `model.*`（legacy 运行时模型） | `metadata.*`（引擎增强模型） |
|----|-------------------------------|-------------------------------|
| `EntityMetadata` | 用 `FieldMetadata`，`getFields(): Map<String,FieldMetadata>` | 用 `SmartFieldMetadata`，含 operations/AI/attributes |
| `SmartFieldMetadata` | calculation/virtual/readonly/businessRule 等 | apiName/label/type/required/unique/picklist 等（**零重叠**） |
| `BusinessRuleMetadata` | id/apiName/domain/condition/expression，severity 枚举 | name/label/condition/action，severity String |
| `OperationMetadata` | id/apiName/labels/methodName/implementationClass/rateLimited | name/type/steps/preconditions/async/timeout |

- **legacy 引擎路径**（`MetadataEngine`/`ExpressionEngine`/`BusinessRuleEngine`/`DefaultBusinessRuleEngine`/`FieldCalculationEngine`/`security`/`cache`）使用 `model.*`
- **现代适配器路径**（`SdkMetadataRepository`/`MetaEntityConverter`/`IamMetadataBridge`）使用 `metadata.*`

用户决策：**彻底合并到 `metadata.*`（重写 legacy 引擎调用层）**，删除整个 `model` 包。

## What Changes

- **BREAKING（内部）**：将 legacy 引擎路径的 `model.*` 引用全面迁移到 `metadata.*`，重写调用层以适配 `metadata.*` 的字段/方法契约
- **BREAKING（内部）**：删除 `domain.model` 全部类（约 12 个：`EntityMetadata`/`SmartFieldMetadata`/`FieldMetadata`/`BusinessRuleMetadata`/`OperationMetadata`/`DynamicSmartEntity`/`RuleResult`/`MetadataRegistry`/`DefaultMetadataRegistry`/`RelationshipMetadata`/`PermissionMetadata`/`EntityPermissionMetadata`）
- 统一后 `domain` 仅保留 `metadata.*` 一套建模体系
- 保持外部行为不变（纯重构，无公共 API / schema / 行为变更）

## Capabilities

### New Capabilities

（无新增 capability —— 纯内部重构，行为不变）

### Modified Capabilities

（无 —— 不改变任何 spec 级行为）

> 本 change 为纯重构（不改变任何外部可观测行为），按 OpenSpec 规则设置 `skip_specs: true`，不新增/修改 capability spec。

## Impact

- **模块**：`bone-metadata-engine-domain`（删除 model 包）、`bone-metadata-engine-runtime`（legacy 引擎类 ~23 处引用迁移）、`bone-metadata-engine-starter`（`RuleEngineConfig` 等引用）
- **代码**：约 23 个 runtime/starter 文件需改 `domain.model.*` → `domain.metadata.*` 并适配字段契约
- **风险**：高（legacy 引擎为 Object/Maps 弱类型实现，`FieldMetadata` → `SmartFieldMetadata` 迁移涉及字段语义映射）
- **依赖**：无外部依赖变更
