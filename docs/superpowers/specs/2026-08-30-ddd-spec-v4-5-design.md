---
comet_change: ddd-spec-v4-5-convergence
role: technical-design
canonical_spec: openspec
archived-with: 2026-09-11-ddd-spec-v4-5-convergence
status: final
---

# 技术设计：v4.5 架构门禁重配（M2）

## 1. 背景与上游

动机与高层方案见 `openspec/changes/ddd-spec-v4-5-convergence/proposal.md` 与 `design.md`（canonical spec = OpenSpec）。本文只做**深度技术细化**，聚焦 **M2 门禁重配**；M1（文档重构）与 M3（平台能力 ADR）不在本文细化范围。

**已确认的三项决策（用户明确选择）**

1. P2 聚合纯单测判定 = **仅存在性检查**（不引入新注解，内容质量交 CR + 测试模板）。
2. 存量启用 = **blueprint 先行、其余 7 模块 freeze**。
3. 仓储返回类型 = **严格四类**（聚合根 / `Optional<聚合根>` / `boolean` / `void`），**不允许 `Collection<聚合根>`**，多结果加载一律走读侧。

**平台硬约束（实测）**

| 约束 | 值 |
|---|---|
| 共享规则库 | `bone-framework/bone-architecture-test/.../BoneDddArchRules.java`，390 行，**20 个规则方法** |
| SDK 基接口 | `com.bone.metadata.sdk.Repository` —— **不在** `..domain.repository..` 包，继承方法不受新规则影响 |
| 现有测试入口 | `ArchitectureTest` 使用 `@AnalyzeClasses(importOptions = ImportOption.DoNotIncludeTests.class)` |
| 静态检查现状 | Checkstyle **仅**在 `bone-metadata-sdk` / `bone-extension-sdk` 配置且 `failsOnError=false`，**未全局启用** |

## 2. 规则分类处置（20 条 → 19 强制 + 4 可选）

| 类 | 条数 | 具体规则 |
|---|---|---|
| **A 保留强制**（不 freeze） | 14 | `domainMustNotDependOnOuterLayers`、`applicationMustNotDependOnInfrastructure`、`domainMustNotUseQueryBuilder`、`commandHandlersMustNotUseQueryBuilder`、`noUseCaseClassesInApplication`、`noApplicationUseCasePackage`、`noBoneCoreUseCaseApiDependency`、`noStudioGeneratorUseCaseAnnotation`、`noNewDomainStorePackage`、`noCustomBusinessException`、`noBusinessExceptionSuffix`、`adapterControllersMustNotDependOnDomainRepository`、`adapterControllersMustNotDependOnDomainService`、`outerLayersMustNotMutateAggregateIdentity` |
| **B 改造后仍强制** | 2 | `domainRepositoriesShouldOnlyDeclareWhitelistedMethods`（→ 返回类型）、`adapterControllersMustNotDependOnApplicationService`（→ 禁包 + 命名黑名单） |
| **C 降级为可选** | 4 | `commandHandlersShouldBeNamedCommandHandler`、`queryHandlersShouldBeNamedQueryHandler`、`commandHandlersShouldBeTransactional`、`queryHandlersShouldBeReadOnlyTransactional` |
| **新增强制** | 3 | 聚合纯单测存在性、跨上下文 FQN、CommandHandler 禁直注消息生产者 |

> **设计发现（需同步修正上游）**：`design.md` 中「规则数收敛至约 13 条强制」为估算，**实际为 19 条强制 + 4 条可选**。

> **设计发现（C 类降级的真实落地方式）**：ArchUnit 没有 warn 级别，且项目**未全局启用 Checkstyle**。因此「降级」的实际含义是：**从各模块 `ArchitectureTest` 的默认清单中移除**，方法保留在 `BoneDddArchRules` 中并标注为风格约束（非架构门禁），供模块自愿引用。若要在 CI 保留提示，需额外引入静态检查配置——列为 build 阶段可选任务，不作为 M2 必做。

## 3. 关键规则设计

### R1 · 聚合纯单测存在性（铁律 P2）

**⚠️ 阻断性发现**：现有 `ArchitectureTest` 使用 `DoNotIncludeTests`，**测试类不在分析集内**，规则无法探测同名测试。若直接在其中加入本规则，会因「分析集无测试类」导致**全量违规**——反而是明显信号，不会静默通过，但会污染现有测试入口。

**解法**：独立测试类承载，不改动既有分析范围。

```java
// 新增：src/test/java/<module>/architecture/DomainModelTest.java
// 注意：不得使用 DoNotIncludeTests，否则测试类不可见
@AnalyzeClasses(packages = "com.bone.{module}")
class DomainModelTest {
    @ArchTest
    static final ArchRule aggregate_pure_unit_test =
        BoneDddArchRules.aggregateRootsShouldHavePureUnitTest("com.bone.{module}");
}
```

```java
// BoneDddArchRules 新增
public static ArchRule aggregateRootsShouldHavePureUnitTest(String rootPackage) {
    JavaClasses all = new ClassFileImporter().importPackages(rootPackage);
    Set<String> testNames = all.stream()
        .map(JavaClass::getSimpleName)
        .filter(n -> n.endsWith("Test"))
        .collect(Collectors.toSet());

    return noClasses()
        .that().resideInAPackage(rootPackage + "..domain..")
        .and().areAssignableTo(AggregateRoot.class)          // 含 TenantAggregateRoot
        .and().haveSimpleNameNotStartingWith("Abstract")     // 沿用 Surefire 排除约定
        .should(beCoveredByPureUnitTest(testNames))
        .because("每个聚合根必须有可在无容器下运行的纯单测（铁律 P2）");
}

private static ArchCondition<JavaClass> beCoveredByPureUnitTest(Set<String> testNames) {
    return new ArchCondition<>("存在同名纯单测类") {
        @Override
        public void check(JavaClass aggregate, ConditionEvents events) {
            String expected = aggregate.getSimpleName() + "Test";
            if (!testNames.contains(expected)) {
                events.add(SimpleConditionEvent.violated(
                    aggregate, aggregate.getName() + " 缺少纯单测 " + expected));
            }
        }
    };
}
```

- **判定逻辑**：聚合根类（继承 `AggregateRoot`）必须有 `<SimpleName>Test` 同名测试类。
- **已知弱点**：可被空测试类绕过（用户已接受）。**缓解**：CR 检查清单 + 提供测试模板（「主状态机」与「拒绝路径」两节骨架）。
- **误伤清单**：`Abstract*` 开头已排除；抽象聚合基类本身也会被 `Abstract` 过滤覆盖；枚举/内部类的 `getSimpleName()` 差异需在实现时处理（取最外层简单名）。

### R2 · 仓储返回类型（铁律 P4 / D3）

```java
private static final Set<String> SCALAR = Set.of("void", "boolean", "java.lang.Boolean");

public static ArchRule domainRepositoriesShouldOnlyDeclareWhitelistedMethods() {
    return methods()
        .that().areDeclaredInClassesThat().resideInAPackage("..domain.repository..")
        .and().arePublic()
        .should(returnOnlyAggregateOrExistence())
        .because("写侧仓储只承担聚合持久化；分页/投影/统计一律走读侧（铁律 P4）");
}

// 允许：void | boolean | 聚合根 | Optional<聚合根>
// 拒绝：其余一切（含 List/Set/Collection、Page、DTO、投影、Tuple、String、数值）
```

**判定要点**

| 项 | 处理 |
|---|---|
| 聚合根识别 | 返回类型 `isAssignableTo(AggregateRoot.class)` |
| `Optional` | 允许 `java.util.Optional`（含原始类型与 `Optional<聚合根>`） |
| **多结果加载** | **一律拒绝**——`List<聚合>`、`findAll()` 等走读侧（用户确认） |
| 继承方法 | SDK 基 `Repository`（`com.bone.metadata.sdk`）不在判定包内，**不受影响**；模块若**重新声明**多结果方法则违规 |
| 复合自然键 | 与方法名无关，`findByTenantIdAndCode` 只要返回聚合根即合法 |
| 持久化词汇 | 方法名含 `Sql`/`Criteria`/`Dynamic`/`Query` 列为 CR 指南（不作门禁，避免与方法名解耦的目标自相矛盾） |

### R3 · 跨上下文 FQN（铁律 P5 / D9）

```java
public static ArchRule noCrossContextDomainDependency(String selfRootPackage) {
    String self = selfRootPackage.substring(selfRootPackage.lastIndexOf('.') + 1);
    String fqn = "com\\.bone\\.(?!" + self + ")[a-z0-9]+\\.domain\\..*";

    return noClasses()
        .that().resideInAPackage(selfRootPackage + "..")
        .should().dependOnClassesThat().haveFullyQualifiedNameMatching(fqn)
        .because("跨限界上下文集成须经公开 API / 事件契约 / 防腐层（铁律 P5）");
}
```

- **为什么用 FQN 正则**：`haveFullyQualifiedNameMatching` 匹配字节码中记录的引用，**不依赖对方类是否在 classpath**；而现状 §10.4 的 `resideInAPackage` + `allowEmptyShould(true)` 在对方类缺失时空命中即通过，**守护是假的**。
- **关键**：**不得设 `allowEmptyShould(true)`**；空匹配视为配置错误。
- **承载位置**：各下游模块 `ArchitectureTest`（与 §10.4 一致，跨模块 classpath 通常不含对方内部类）。
- **误伤**：共享内核（`com.bone.core..`、`com.bone.metadata.sdk..`）不属于任何上下文 `domain` 包，不受影响。

### R4 · CommandHandler 禁直注消息生产者（铁律 P7）

```java
public static ArchRule commandHandlersMustNotDependOnMessageProducer(String... producerPackages) {
    return noClasses()
        .that().haveSimpleNameEndingWith("CommandHandler")
        .should().dependOnClassesThat().resideInAnyPackage(producerPackages)
        .allowEmptyShould(true)   // 无 MQ 依赖时合法空命中
        .because("跨进程事件必须经 Outbox 异步投递，禁止 Handler 同步直发（铁律 P7）");
}
```

- **唯一允许 `allowEmptyShould(true)` 的场景**：项目当前未引入 MQ 客户端，无依赖即无违规；待引入 MQ 后规则自动生效。需在规则 Javadoc 明确该理由，避免被误读为「空命中放行」。

## 4. 启用、验证与回滚

| 阶段 | 动作 | 验收 |
|---|---|---|
| ① 规则自测 | 每条新增/改造规则配正向 + 反向用例，随 `bone-architecture-test` 提交 | 自测用例覆盖「应拦截」与「应放行」两侧 |
| ② blueprint 生效 | 新规则直接加入 `bone-blueprint` 测试入口，**要求 0 违规**；先补齐各聚合纯单测 | 136 个既有测试 + 新规则全绿 |
| ③ 其余 7 模块冻结 | `FreezingArchRule` 生成基线（首次 `allowStoreCreation=true`），只拦新增 | 基线入库，违规数不高于上次记录 |
| ④ 回归 | `mvn clean install` | BUILD SUCCESS |

**回滚**：规则从 `ArchitectureTest` 清单移除即可（无代码耦合）；冻结基线回退到上一 commit。

## 5. 测试策略

- **规则自测**：ArchUnit 规则本身用 `ClassFileImporter` 导入 fixture 类验证（正向/反向）。
- **真实验证**：`bone-blueprint` 作为唯一 0 违规门禁模块。
- **存量**：7 个模块冻结基线 + 违规数单调不增。
- **M3 前置**：每项 ADR 前先 spike（SDK 尊重非空 id、基类去 `@Data` 影响面统计）。

## 6. Spec Patch 记录（已回写 delta spec）

1. ✅ Requirement「每个聚合根必须具备无容器的行为测试」→ 新增场景：**门禁只校验同名测试类存在，内容质量由代码评审与测试模板保证**。
2. ❌ ~~允许 `Collection<聚合根>`~~ —— **用户否决，已取消**。仓储返回严格限制为四类，多结果加载走读侧。

## 7. 风险登记

| 风险 | 影响 | 缓解 |
|---|---|---|
| P2 可被空测试绕过 | 门禁虚设 | CR 检查清单 + 测试模板；后续可升级为注解方案 |
| R2 拒绝多结果加载 → 部分模块需改造 | 存量 `findAll` 类方法需迁读侧 | blueprint 先验证；其余模块 freeze 后逐步迁移 |
| R1 需导入测试字节码，与既有 `DoNotIncludeTests` 冲突 | 测试入口需新增 | 独立 `DomainModelTest` 承载，不改动既有分析范围 |
| C 类降级后无 CI 提示（项目未全局启用 Checkstyle） | 风格约束失去提醒 | 方法保留 + 标注为可选；引入静态检查配置列为 build 可选任务 |
| R3 正则随新增上下文变化 | 规则需同步 | 规则入参显式传自身根包，无需改正则 |

## 8. build 阶段入口

本 change 已确认「先只做 Design」。进入 build 时的执行顺序：

1. M1 文档重构（18 任务）—— 纯文档，风险最低
2. M2 门禁重配（8 任务）—— 按 §4 的四阶段推进
3. M3 前置 ADR（6 任务）—— 每项先 spike 再写 ADR
