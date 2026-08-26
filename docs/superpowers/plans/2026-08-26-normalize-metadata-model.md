---
change: normalize-metadata-model-unification
design-doc: docs/superpowers/specs/2026-08-26-normalize-metadata-model-design.md
base-ref: 5f2a9f9d2b8babf5b81251db4453e4f02127c667
---

# Implementation Plan: 统一 metadata 引擎双重建模体系（删除 domain.model）

> change：`normalize-metadata-model-unification`（纯重构，外部行为不变）
> 设计依据：`docs/superpowers/specs/2026-08-26-normalize-metadata-model-design.md`（§1 映射表 / §2 分批 / §3 测试）
> 任务边界：`openspec/changes/normalize-metadata-model-unification/tasks.md`（1 盘点 → 2 非重复 → 3 重复 → 4 删包 → 5 全量）
> 模块坐标：engine 四模块 = `bone-metadata-engine-domain` / `-ports` / `-runtime` / `-starter`（父 POM：`bone-engine/bone-metadata-engine/pom.xml`）；server = `bone-engine/bone-metadata-server`。

## 执行原则

- 每批完成后必做一次**编译验证**（`mvn -pl <模块> compile`），批次间隙绝不叠加未验证改动。
- 迁移顺序严格遵循 tasks.md：先非重复类（低风险），再重复类引用（高风险），最后删包。
- 任何删类操作前必须先 `grep` 确认零引用。
- 行为不变：不改 legacy 引擎业务逻辑，仅替换模型引用 + 字段访问适配。

## 一、盘点与映射（tasks 1.x，先决，不产生代码改动）

**model 包 12 类归属判定（已核实）：**

| `model.*` 类 | 处置 | 依据 |
|--------------|------|------|
| `DynamicSmartEntity` | **迁入 metadata**（批次 1） | 无重复对应，7 个 runtime 引用点 |
| `RuleResult` | **迁入 metadata**（批次 1） | 无重复对应，2 个引用点 |
| `PermissionMetadata` | **迁入 metadata**（批次 1） | 被 `metadata.FieldLevelSecurityMetadata` 实现，必须迁 |
| `RelationshipMetadata` | **迁入 metadata**（批次 1） | metadata 无对应，`MetadataImpactAnalyzer` 依赖 |
| `MetadataRegistry` / `DefaultMetadataRegistry` | **迁入 metadata**（批次 1，签名适配） | 仅 `EngineConfiguration` 引用；签名 `FieldMetadata`→`SmartFieldMetadata` |
| `EntityMetadata` / `SmartFieldMetadata` / `FieldMetadata` / `BusinessRuleMetadata` / `OperationMetadata` | **删除**，legacy 引用改 `metadata.*`（批次 2） | metadata 已有同名增强类 |
| `EntityPermissionMetadata` | **删除**（零引用，批次 3） | grep 无 import |
| `OperationMetadata`（model 版） | **删除**（零引用，批次 3） | grep 无 import |

**runtime/starter 引用点清单（经 `import domain.model.*` 核实，22 个文件）：**
- `EntityMetadata`：MetadataChangedEvent、MetadataEngine、DefaultPermissionEvaluator、FieldLevelSecurityManager、PermissionEvaluator、SpelBusinessRuleEngine、EntityObjectConverter、MetadataCacheManager、DefaultMetadataCacheManager、MetadataImpactAnalyzer、MetadataImpactDataAccess
- `FieldMetadata`：DefaultPermissionEvaluator、FieldLevelSecurityManager、SpelBusinessRuleEngine、EntityObjectConverter、MetadataImpactAnalyzer、FieldCalculationEngine、DefaultFieldCalculationEngine
- `BusinessRuleMetadata`：UnifiedRuleEngine、MetadataImpactAnalyzer、DefaultBusinessRuleEngine、BusinessRuleRegistry、DefaultBusinessRuleRegistry、BusinessRuleEngine（+ RuleEngineConfig FQN）
- `DynamicSmartEntity`：SmartQueryExecutor、UnifiedRuleEngine、ExpressionEngine、DefaultBusinessRuleEngine、FieldCalculationEngine、DefaultFieldCalculationEngine、BusinessRuleEngine（+ RuleEngineConfig FQN）
- `RuleResult`：BusinessRuleEngine、SpelBusinessRuleEngine
- `MetadataRegistry`/`DefaultMetadataRegistry`：EngineConfiguration
- `RelationshipMetadata`：MetadataImpactAnalyzer
- `PermissionMetadata`：metadata.FieldLevelSecurityMetadata（domain 内部）

**字段映射表（tasks 1.2，写入 design-doc §1.1 附录，批次 2 依据）：**

| `model.FieldMetadata` | `metadata.SmartFieldMetadata` |
|-----------------------|------------------------------|
| `getApiName()` / `getName()` | `getApiName()` / `getFieldName()` |
| `isPrimaryKey()` | `isPrimaryKey()` |
| `isRequired()` | `isRequired()` |
| `isDisplayName()` | 映射到 `getLabel()`（SmartFieldMetadata 无独立 displayName） |
| `getType()` / `getDefaultValue()` / `getDescription()` / `getLength()` / `getPrecision()` | 同名 |

**验证**：grep `domain\.model\.` 清单与上述一致；映射表覆盖 runtime 用到的全部字段访问（编译后确认）。

---

## 二、批次 1：迁移非重复模型类（tasks 2.x，低风险）

**1.1 `DynamicSmartEntity`、`RuleResult` 迁入 metadata**
- 改 package `com.bone.metadata.engine.domain.model` → `com.bone.metadata.engine.domain.metadata`。
- 更新引用点 import：SmartQueryExecutor、UnifiedRuleEngine、ExpressionEngine、DefaultBusinessRuleEngine、FieldCalculationEngine、DefaultFieldCalculationEngine、BusinessRuleEngine、SpelBusinessRuleEngine、RuleEngineConfig（FQN → import）。
- `DynamicSmartEntity` 依赖 `domain.core.SmartBaseEntity`（不受影响）。

**1.2 `PermissionMetadata`、`RelationshipMetadata` 迁入 metadata**
- 迁 `PermissionMetadata`（保住 `FieldLevelSecurityMetadata implements` 同包正确性）。
- 迁 `RelationshipMetadata`（供 MetadataImpactAnalyzer），移除 model 原位置。

**1.3 `MetadataRegistry` / `DefaultMetadataRegistry` 迁入 metadata（签名适配）**
- 迁移后其签名中的 `FieldMetadata` 在 metadata 包无对应，需改为 `SmartFieldMetadata`（getFieldMetadata/getAllFieldMetadata/getCalculatedFieldMetadata/getVirtualFieldMetadata 等 5 处）。
- 更新引用点：`EngineConfiguration`（import 改 metadata.*）。

**验证（批次 1）**：
```bash
# domain 自洽编译
mvn -pl bone-engine/bone-metadata-engine/bone-metadata-engine-domain -am compile
# runtime + starter 编译（引用点已更新）
mvn -pl bone-engine/bone-metadata-engine/bone-metadata-engine-runtime,bone-metadata-engine-starter -am compile
```

---

## 三、批次 2：迁移重复类引用（tasks 3.x，高风险）

**2.1 legacy 引擎核心类 `model.EntityMetadata` → `metadata.EntityMetadata`（tasks 3.1）**
- 文件：MetadataEngine、SpelBusinessRuleEngine、DefaultBusinessRuleEngine、ExpressionEngine（已混用）、MetadataImpactAnalyzer、MetadataCacheManager、DefaultMetadataCacheManager、EntityObjectConverter、DefaultPermissionEvaluator、FieldLevelSecurityManager、PermissionEvaluator、MetadataChangedEvent、MetadataImpactDataAccess。
- 字段适配：`getFields()` 返回 `Map<String, SmartFieldMetadata>`（非 `List<FieldMetadata>`）；`metadata.EntityMetadata` **缺** `getRelationships()/getBusinessRules()` → 在 `metadata.EntityMetadata` 补兼容 getter 或调用侧适配（MetadataImpactAnalyzer 是重点）。
- 强类型化：把 `Object`/`Map` 弱类型处理尽量改为 `metadata.EntityMetadata`。

**2.2 `model.FieldMetadata` / `model.SmartFieldMetadata` → `metadata.SmartFieldMetadata`（tasks 3.2）**
- 文件：rule/security/calculation/cache/util/event/analysis 相关（见盘点清单）。
- 适配：`isDisplayName()`→`getLabel()`；model 版 `SmartFieldMetadata extends FieldMetadata` 无对应，直接改用 `metadata.SmartFieldMetadata`（超集）。

**2.3 `model.BusinessRuleMetadata` / `model.OperationMetadata` → `metadata.*`（tasks 3.3）**
- 文件：rule/analysis/registry（BusinessRuleRegistry、DefaultBusinessRuleRegistry、UnifiedRuleEngine、MetadataImpactAnalyzer、RuleEngineConfig FQN）。
- 适配：`model.BusinessRuleMetadata.getApiName()` → `metadata.BusinessRuleMetadata.getName()/getLabel()`；`getDependentFields()` → metadata 版无 → 适配为 `condition`/`fieldName`。
- `model.OperationMetadata` 零引用，此步仅确保不引入新引用。

**2.4 starter 引用迁移（tasks 3.4）**
- `RuleEngineConfig`：全限定名 `domain.model.DynamicSmartEntity/BusinessRuleMetadata` → `domain.metadata.*`。

**验证（批次 2）**：runtime + starter 编译；`spotless:check`；新增映射单测（见测试策略）。

```bash
mvn -pl bone-engine/bone-metadata-engine/bone-metadata-engine-runtime,bone-metadata-engine-starter -am compile
```

---

## 四、批次 3：删除 model 包（tasks 4.x）

**3.1 grep 确认零引用**
```bash
# 应返回 0 结果
grep -rn "domain\.model" bone-engine/bone-metadata-engine --include="*.java"
```

**3.2 删除 model 包全部 12 个类文件**，删除包目录；移除任何残留 import/FQN。

**验证（批次 3）**：
```bash
mvn -pl bone-engine/bone-metadata-engine/bone-metadata-engine-domain,bone-metadata-engine-runtime,bone-metadata-engine-starter -am compile
# 确认无 model 残留
grep -rn "domain\.model" bone-engine/bone-metadata-engine --include="*.java" | wc -l   # = 0
```

---

## 五、批次 4：全量验证（tasks 5.x）

**4.1 全量构建**：engine 四模块 + server `mvn clean install` 必须 BUILD SUCCESS。
```bash
mvn -pl bone-engine/bone-metadata-engine/bone-metadata-engine-domain,bone-metadata-engine-ports,bone-metadata-engine-runtime,bone-metadata-engine-starter,bone-metadata-server -am clean install
```

**4.2 测试**：runtime 既有 11 用例全绿（SdkMetadataRepositoryTest / JdbcRuntimeRecordServiceTest / RuntimePageQueryTest / ArchitectureTest）；新增 `FieldMetadata→SmartFieldMetadata` 映射单测（覆盖 getApiName / isPrimaryKey / displayName→label 等，见 design-doc §3.1）。

**4.3 门禁**：`spotless:check` + 各模块 ArchitectureTest 全绿；commit hook 通过。

## 测试策略（design-doc §3）

1. 新增映射单测：`SmartFieldMetadata` 字段访问适配测试，覆盖 §1.1 映射。
2. 既有保底：runtime 11 用例。
3. 全量门禁：engine 四模块 + server `mvn clean install` BUILD SUCCESS；spotless:check；ArchUnit。

## 关键文件（变更清单）
- **domain**：删除 `domain/model/*.java`（12）；新增 `domain/metadata/{DynamicSmartEntity,RuleResult,PermissionMetadata,RelationshipMetadata,MetadataRegistry,DefaultMetadataRegistry}.java`；改 `domain/metadata/{EntityMetadata,SmartFieldMetadata,BusinessRuleMetadata,FieldLevelSecurityMetadata}.java`（适配 getter）。
- **runtime**：约 20 个文件 import/适配（MetadataEngine、ExpressionEngine、BusinessRuleEngine、DefaultBusinessRuleEngine、FieldCalculationEngine、DefaultFieldCalculationEngine、UnifiedRuleEngine、SmartQueryExecutor、SpelBusinessRuleEngine、EngineConfiguration、EntityObjectConverter、MetadataCacheManager、DefaultMetadataCacheManager、MetadataImpactAnalyzer、MetadataImpactDataAccess、DefaultPermissionEvaluator、FieldLevelSecurityManager、PermissionEvaluator、MetadataChangedEvent、BusinessRuleRegistry、DefaultBusinessRuleRegistry）。
- **starter**：`RuleEngineConfig.java`（FQN → import）。

## 风险与缓解
- [legacy 弱类型，行为微变] → 分批编译 + 既有 11 用例保底 + 映射单测。
- [`FieldMetadata.displayName` vs `SmartFieldMetadata.label`] → 显式映射 + 单测。
- [`metadata.EntityMetadata` 缺 `getRelationships()/getBusinessRules()`] → 批次 2 补兼容 getter 或调用侧适配（MetadataImpactAnalyzer 核心）。
- [跨 ~23 文件大改] → 分批（先非重复再重复），每批编译验证。

## 退出标准
- `domain.model` 目录删除，全仓零 `domain.model` 引用。
- engine 四模块 + server BUILD SUCCESS。
- runtime 11 用例 + 新增映射单测全绿；spotless + ArchUnit 通过。
- `tasks.md` 全部勾选。
