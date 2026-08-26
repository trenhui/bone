# Comet Design Handoff

- Change: normalize-metadata-model-unification
- Phase: design
- Mode: compact
- Context hash: 02a253503c2099d54be6fc315ead643e4c0bb86343c0527405ff9c31d73b2378

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/normalize-metadata-model-unification/proposal.md

- Source: openspec/changes/normalize-metadata-model-unification/proposal.md
- Lines: 1-45
- SHA256: 012ef2e94fcb354982dcb98f50df41c5a6c3b7cf5b2e293b2eaa9b653228e2a3

```md
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

```

## openspec/changes/normalize-metadata-model-unification/design.md

- Source: openspec/changes/normalize-metadata-model-unification/design.md
- Lines: 1-68
- SHA256: 53bebc1a9372cc92a6fea49eb05ad7ee33830cdad8a94e0f20291a177b98caac

```md
# Design: 统一 metadata 引擎的双重建模体系

## Context

物理拆分后，`domain.model`（legacy 运行时模型）与 `domain.metadata`（引擎增强模型）存在 4 对同名但零重叠字段的类。legacy 引擎路径（MetadataEngine/ExpressionEngine/BusinessRuleEngine/FieldCalculationEngine/security/cache）强耦合 `model.*`，现代适配器路径（SdkMetadataRepository）使用 `metadata.*`。见 proposal.md - Why。

约束：
- `domain` 模块纯净（零 Spring/SDK），合并后仍须保持
- 外部可观测行为不变（纯重构）
- 迁移不得引入跨模块反向依赖（domain ← ports ← runtime ← starter 已由 ArchitectureTest 固化）

## Goals / Non-Goals

**Goals:**
- 删除 `domain.model` 全部 12 个类
- legacy 引擎路径 ~23 个文件改用 `domain.metadata.*` 并适配字段契约
- 统一后 `domain` 仅保留 `metadata.*` 一套建模体系

**Non-Goals:**
- 不改变外部 API / schema / 可观测行为
- 不重写 legacy 引擎的业务逻辑（仅替换模型引用与字段访问适配）
- 不合并 `metadata.*` 内部类（`metadata` 已有大量子模型，保持现状）

## Decisions

### D1：以 `metadata.*` 为唯一目标建模体系

**决策**：删除 `model.*`，legacy 引擎全部改用 `metadata.*`。
**理由**：`metadata.*` 是引擎增强模型（含 operations/AI/attributes/picklist 等），更贴近真实元数据能力；`model.*` 是早期骨架。
**备选**：① 反向合并到 model（丢弃增强能力，否决）；② 保留双体系仅重命名消除混淆（不满足"彻底合并"决策，否决）。

### D2：FieldMetadata → SmartFieldMetadata 迁移映射

**决策**：legacy 引擎访问字段的 `model.FieldMetadata`（getApiName/isPrimaryKey/isDisplayName 等）迁移到 `metadata.SmartFieldMetadata`（getApiName/isPrimaryKey/setRequired 等），逐字段映射。
**理由**：`SmartFieldMetadata` 字段集是 `FieldMetadata` 的超集（含 primaryKey/required/unique/label 等），可无损承载 legacy 访问。
**备选**：为 legacy 保留一个 `FieldMetadata` 别名接口（增加第二套接口，违背单一体系目标，否决）。
**风险**：legacy 引擎对字段的某些旧语义（如 `displayName`）需映射到 `SmartFieldMetadata.label`。

### D3：legacy 弱类型 Object/Maps 调用层适配

**决策**：`MetadataEngine`/`ExpressionEngine` 等 legacy 类当前以 `Object`/`Map` 弱类型处理元数据，迁移后**尽量改为强类型 `metadata.EntityMetadata`**；对确需 Map 兼容的边界保留 `Map` 处理，但消除对 `model.*` 的引用。
**理由**：减少 `instanceof` 分支与反射兜底，提升类型安全。
**风险**：legacy 引擎方法签名（如 `registerEntity(Object)`）对外暴露 Object，改强类型为内部渐进，不一次性破坏。

### D4：DynamicSmartEntity / RuleResult / MetadataRegistry 等非重复类归属

**决策**：`model.DynamicSmartEntity`（动态实体值对象）与 `model.RuleResult`（规则结果）无 `metadata.*` 对应，迁入 `metadata.*`（作为新类），删除原 `model.*` 位置。
**理由**：这些是 legacy 运行时真实使用的模型，须保留功能、仅迁移位置。
**风险**：低（纯移动）。

## Risks / Trade-offs

- [legacy 引擎弱类型，迁移后行为可能微变] → 逐文件迁移 + 全量编译 + runtime 既有 11 用例全绿验证；对字段访问适配建立单测
- [`FieldMetadata` 与 `SmartFieldMetadata` 语义差异（displayName vs label 等）] → 建立显式映射表，注释标明对应关系
- [跨 ~23 文件大规模改动] → 分批迁移（先移非重复类 DynamicSmartEntity/RuleResult，再迁移重复类引用），每批编译验证
- [`MetadataRegistry`/`DefaultMetadataRegistry`/`PermissionMetadata` 等 model 独有类] → 迁入 metadata 或确认无引用后删除

## Migration Plan

1. **T1 盘点与映射**：列出全部 `model.*` 类及其引用点，建立 `FieldMetadata→SmartFieldMetadata` 字段映射
2. **T2 迁移非重复模型类**：`DynamicSmartEntity`/`RuleResult` 等迁入 `metadata.*`，更新引用
3. **T3 迁移重复类引用**：legacy 引擎 ~23 文件 `model.EntityMetadata` 等 → `metadata.*`，适配字段访问
4. **T4 删除 `domain.model` 包**：确认零引用后删除全部 12 个类
5. **T5 全量编译 + 测试**：engine 四模块 + server 编译，runtime 11 用例 + 新增字段映射单测全绿，spotless + ArchUnit 通过

## Open Questions

（无 —— 迁移方案已确定，无影响 spec/方案/任务拆分的遗留未知项）

```

## openspec/changes/normalize-metadata-model-unification/tasks.md

- Source: openspec/changes/normalize-metadata-model-unification/tasks.md
- Lines: 1-32
- SHA256: aa141dec54bf4195e67573d3a300f0e473e9d1d0b4b0c486a2970f551ed7c1ac

```md
# Tasks: normalize-metadata-model-unification

> 配合 `proposal.md` / `design.md`。目标：删除 `domain.model`，legacy 引擎路径全部改用 `domain.metadata.*`，统一建模体系。纯重构，行为不变。

## 1. 盘点与映射

- [ ] 1.1 列出 `domain.model` 全部 12 个类及其在 runtime/starter 的全部引用点（约 23 文件），产出一份引用清单；验证清单覆盖 grep 结果
- [ ] 1.2 建立 `model.FieldMetadata` → `metadata.SmartFieldMetadata` 字段映射表（getApiName/isPrimaryKey/isDisplayName→label 等），写入 design.md 附录；验证映射覆盖 legacy 引擎用到的全部字段访问
- [ ] 1.3 判定 `model.MetadataRegistry/DefaultMetadataRegistry/PermissionMetadata/EntityPermissionMetadata/RelationshipMetadata` 的归属（迁入 metadata 或确认零引用后删除）；验证每类有明确去向

## 2. 迁移非重复模型类

- [ ] 2.1 将 `model.DynamicSmartEntity`、`model.RuleResult` 迁入 `metadata.*`（更新 package + import），验证引用点编译通过
- [ ] 2.2 迁移 `model.MetadataRegistry/DefaultMetadataRegistry` 等确认需保留的类到 `metadata.*`，验证引用点编译通过

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

```
