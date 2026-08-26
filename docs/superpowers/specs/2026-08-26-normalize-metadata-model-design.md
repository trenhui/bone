---
comet_change: normalize-metadata-model-unification
role: technical-design
canonical_spec: openspec
archived-with: 2026-08-26-normalize-metadata-model-unification
status: final
---

# 深度设计：统一 metadata 引擎双重建模体系

> 本 Design Doc 深化 change 的 `design.md`（高层框架），聚焦 FieldMetadata→SmartFieldMetadata 映射、分批迁移执行细节与测试策略。

## 1. 迁移目标与映射表

**目标**：删除 `domain.model` 全部 12 类，legacy 引擎路径（~23 文件）改用 `domain.metadata.*`，统一建模体系。

### 1.1 FieldMetadata → SmartFieldMetadata 字段映射

| `model.FieldMetadata` 访问 | `metadata.SmartFieldMetadata` 等价 |
|---------------------------|------------------------------------|
| `getApiName()` / `getFieldName()` | `getApiName()` / `getFieldName()` |
| `isPrimaryKey()` | `isPrimaryKey()` |
| `isRequired()` | `isRequired()` |
| `isDisplayName()` | 映射到 `getLabel()`（SmartFieldMetadata 无独立 displayName，用 label 承载） |
| `getType()` | `getType()` |
| `getDefaultValue()` | `getDefaultValue()` |
| `getDescription()` | `getDescription()` |
| `getLength()` | `getLength()` |
| `getPrecision()` | `getPrecision()` |

### 1.2 类归属清单

| `model.*` 类 | 处置 |
|--------------|------|
| `DynamicSmartEntity` | 迁入 `metadata.*`（无对应，新位置） |
| `RuleResult` | 迁入 `metadata.*`（无对应，新位置） |
| `MetadataRegistry` / `DefaultMetadataRegistry` | 迁入 `metadata.*`（无对应，新位置） |
| `PermissionMetadata` / `EntityPermissionMetadata` / `RelationshipMetadata` | 迁入 `metadata.*` 或确认零引用后删除 |
| `EntityMetadata` / `SmartFieldMetadata` / `BusinessRuleMetadata` / `OperationMetadata` | **删除**，legacy 引用改为 `metadata.*` 对应类 |

## 2. 分批迁移执行细节

### 批次 1：非重复模型类（低风险）
- 迁移 `DynamicSmartEntity`/`RuleResult`/`MetadataRegistry`/`DefaultMetadataRegistry`/`PermissionMetadata`/`EntityPermissionMetadata`/`RelationshipMetadata` 到 `metadata.*`
- 更新引用点 import + package
- **验证**：runtime + starter 编译通过

### 批次 2：重复类引用迁移（高风险）
- legacy 引擎类改用 `metadata.EntityMetadata`（字段访问按 §1.1 映射）
- `metadata.SmartFieldMetadata` 替代 `model.FieldMetadata`/`model.SmartFieldMetadata`
- `metadata.BusinessRuleMetadata`/`metadata.OperationMetadata` 替代 `model.*` 对应类
- 适配 `Object`/`Map` 弱类型调用层，尽量改强类型
- **验证**：runtime + starter 编译通过，新增映射单测

### 批次 3：删除 model 包
- grep 确认 `domain.model` 零引用后删除全部 12 类
- **验证**：domain 编译通过，无 `model` 残留

## 3. 测试策略

1. **新增映射单测**：`SmartFieldMetadata` 字段访问适配测试，覆盖 §1.1 映射（getApiName/isPrimaryKey/displayName→label 等）
2. **既有保底**：runtime 11 用例（SdkMetadataRepositoryTest/JdbcRuntimeRecordServiceTest/RuntimePageQueryTest/ArchitectureTest）
3. **全量门禁**：engine 四模块 + server `mvn clean install` BUILD SUCCESS；spotless:check；各模块 ArchitectureTest

## 4. 风险与缓解

- [legacy 弱类型，迁移后行为微变] → 分批编译 + 既有测试保底 + 映射单测
- [`FieldMetadata.displayName` vs `SmartFieldMetadata.label`] → 显式映射，单测覆盖
- [跨 23 文件大改] → 分批（先非重复再重复），每批编译验证

## 5. 退出标准

- `domain.model` 目录删除，全仓零 `domain.model` 引用
- engine 四模块 + server BUILD SUCCESS
- runtime 11 用例 + 新增映射单测全绿；spotless + ArchUnit 通过
