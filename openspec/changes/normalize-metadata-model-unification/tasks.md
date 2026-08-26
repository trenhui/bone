# Tasks: normalize-metadata-model-unification

> 配合 `proposal.md` / `design.md`。目标：删除 `domain.model`，legacy 引擎路径全部改用 `domain.metadata.*`，统一建模体系。纯重构，行为不变。

## 1. 盘点与映射

- [x] 1.1 列出 `domain.model` 全部 12 个类及其在 runtime/starter 的全部引用点，产出引用清单（21 个 engine 引用文件 + SDK/studio 的 `sdk.domain.model` 独立类不涉及）
- [x] 1.2 建立 `model.FieldMetadata` → `metadata.SmartFieldMetadata` 字段映射表（getApiName/isPrimaryKey/isDisplayName→label 等），写入 design-doc §1.1
- [x] 1.3 判定归属：DynamicSmartEntity/RuleResult/PermissionMetadata/RelationshipMetadata/MetadataRegistry/DefaultMetadataRegistry 迁入 metadata；EntityPermissionMetadata/model.OperationMetadata 零引用可删；EntityMetadata/SmartFieldMetadata/FieldMetadata/BusinessRuleMetadata 由 metadata 同名增强类替代

## 2. 迁移非重复模型类

- [x] 2.1 将 `model.DynamicSmartEntity`、`model.RuleResult`、`model.PermissionMetadata`、`model.RelationshipMetadata` 迁入 `metadata.*`（更新 package + import），11 个引用文件已更新，domain+runtime+starter 编译通过
- [x] 2.2 迁移 `model.MetadataRegistry/DefaultMetadataRegistry` 到 `metadata.*`（FieldMetadata→SmartFieldMetadata 签名），并增强 `metadata.EntityMetadata`（entityType/businessRules + getEntityType/getRelationships/getBusinessRules）；domain+runtime+starter 编译通过

## 3. 迁移重复类引用（legacy 引擎）

- [ ] 3.1 迁移 legacy 引擎核心类（`MetadataEngine`/`ExpressionEngine`/`BusinessRuleEngine`/`DefaultBusinessRuleEngine`）的 `model.EntityMetadata` → `metadata.EntityMetadata`，适配字段访问，验证 runtime 编译
- [ ] 3.2 迁移 `model.SmartFieldMetadata`/`model.FieldMetadata` 引用到 `metadata.SmartFieldMetadata`（rule/security/calculation/cache/util/event/analysis 等），适配字段映射，验证 runtime 编译
- [ ] 3.3 迁移 `model.BusinessRuleMetadata`/`model.OperationMetadata` 引用到 `metadata.*`（rule/analysis/registry 等），适配字段契约，验证 runtime 编译
- [ ] 3.4 迁移 starter `RuleEngineConfig` 等 `model.*` 引用到 `metadata.*`，验证 starter 编译

## 4. 删除 model 包

- [ ] 4.1 全仓 grep 确认 `domain.model` 零引用后，删除 `domain.model` 全部 12 个类文件；验证 domain 编译且无 `model` 残留
- [ ] 4.2 移除 `domain.model` 包目录与任何残留 import，验证四模块全量编译

## 5. 全量验证

- [ ] 5.1 engine 四模块 + server 全量 `mvn clean install` 通过（BUILD SUCCESS）
- [ ] 5.2 runtime 既有 11 用例全绿；新增 `FieldMetadata→SmartFieldMetadata` 映射单测覆盖字段访问适配，验证通过
- [ ] 5.3 spotless:check + 各模块 ArchitectureTest 全绿，commit hook 通过
