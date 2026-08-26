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
