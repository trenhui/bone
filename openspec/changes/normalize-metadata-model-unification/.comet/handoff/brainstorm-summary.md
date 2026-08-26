# Brainstorm Summary

- Change: normalize-metadata-model-unification
- Date: 2026-08-26

## 确认的技术方案

**P0.1 彻底合并到 metadata.\*（重写 legacy 引擎调用层），删除 domain.model 全部 12 个类。**

- 目标建模体系：`metadata.*`（引擎增强模型，含 operations/AI/attributes/picklist）
- 执行方式：**分批迁移**（用户确认）
  1. 先迁非重复类：`DynamicSmartEntity`/`RuleResult`/`MetadataRegistry`/`DefaultMetadataRegistry` 等迁入 `metadata.*`
  2. 再迁重复类引用：legacy 引擎 ~23 文件 `model.EntityMetadata`/`SmartFieldMetadata`/`BusinessRuleMetadata`/`OperationMetadata` → `metadata.*`，适配字段契约
  3. 最后删 `model` 包，确认零引用
- 关键映射：`model.FieldMetadata` → `metadata.SmartFieldMetadata`（getApiName/isPrimaryKey/isDisplayName→label 等）

## 关键取舍与风险

- 以 `metadata.*` 为唯一体系（否决反向合并到 model，避免丢弃增强能力）
- legacy 弱类型 Object/Maps 调用层尽量改强类型 `metadata.EntityMetadata`
- [legacy 弱类型，行为可能微变] → 逐文件迁移 + 全量编译 + 既有测试保底
- [`FieldMetadata` 与 `SmartFieldMetadata` 语义差异（displayName vs label）] → 显式映射表 + 新增单测

## 测试策略

- **新增映射单测**（用户确认）：覆盖 `FieldMetadata→SmartFieldMetadata` 字段访问适配（getApiName/isPrimaryKey/displayName→label 等）
- runtime 既有 11 用例保底
- engine 四模块 + server 全量编译，spotless + ArchUnit 通过

## Spec Patch

无（skip_specs，纯重构无行为变更）
