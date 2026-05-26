# bone-architecture-test

Bone DDD 共享 ArchUnit 规则（`BoneDddArchRules`），真源见 `doc/architecture/Bone-DDD-最终实践方案.md` §21。

## 规则清单（与 DDD §12 P0 对应）

| 方法 | 对应规范 |
|------|----------|
| `domainMustNotDependOnOuterLayers()` | P0-1 |
| `applicationMustNotDependOnInfrastructure()` | P0-1 |
| `domainMustNotUseQueryBuilder()` | P0-5 |
| `commandHandlersMustNotUseQueryBuilder()` | P0-6 |
| `domainRepositoriesShouldOnlyDeclareWhitelistedMethods()` | P0-4 + §18.2 |
| `noUseCaseClassesInApplication()` | P0-7 + §14.3 |
| `noApplicationUseCasePackage()` | P0-7 + §14.3 |
| `noBoneCoreUseCaseApiDependency()` | P0-7 |
| `noStudioGeneratorUseCaseAnnotation()` | studio-generator 专用 |
| `noNewDomainStorePackage()` | §14.5 |
| `noCustomBusinessException()` | §16.3 |
| `noBusinessExceptionSuffix()` | §16.3 |
| `adapterControllersMustNotDependOnApplicationService()` | P0-7 + §15 |
| `adapterControllersMustNotDependOnDomainRepository()` | §15 |
| `commandHandlersShouldBeNamedCommandHandler()` | §23 |
| `queryHandlersShouldBeNamedQueryHandler()` | §23 |
| `commandHandlersShouldBeTransactional()` | §15 |
| `queryHandlersShouldBeReadOnlyTransactional()` | §12.1 P0-6 |

读侧 DSL 通过 `@com.bone.core.annotation.ReadSideOnly` 标注（如 `QueryBuilder`、`FluentQuery`），规则 `domainMustNotUseQueryBuilder` / `commandHandlersMustNotUseQueryBuilder` 检测对该注解类型的依赖。

## 用法

1. 模块 `pom.xml` 增加 test 依赖：`bone-architecture-test`、`archunit-junit5`。
2. 新增 `ArchitectureTest`，对存量违规使用 `FreezingArchRule.freeze(...)`（模板见 DDD 附录 B.3）。
3. **首次**生成基线（模块根目录 `archunit_store/`）：

```bash
mvn test -pl <module> -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true
```

4. 将 `archunit_store/` 提交入库；日常 CI 勿开启 `allowStoreCreation`。基线随每次迁移**收缩**，**禁止扩张**。

5. **收缩基线**（违规已修复、冻结文件应清空时）：

```bash
mvn test -pl <module> -Dtest=ArchitectureTest \
  -Darchunit.freeze.store.default.allowStoreUpdate=true
```

`allowStoreUpdate=true` 仅允许基线**收缩**（与 DDD 附录 B.3 一致）。全量覆盖快照用 `refreeze=true`（慎用，须确认无新增违规）。

6. **`noBoneCoreUseCaseApiDependency`**：不纳入 freeze（包已删除，无存量命中，作防回滚保险）。

### 建议 freeze 策略（2026-05-23）

| 规则 | 建议 |
|------|------|
| `applicationMustNotDependOnInfrastructure` | **不 freeze**（ACL 整改后应 0 违规；直接门禁） |
| `domainRepositoriesShouldOnlyDeclareWhitelistedMethods` | **不 freeze**（空仓储 / ReadPort 拆分后应 0 违规） |
| `domainMustNotDependOnOuterLayers` / QueryBuilder 禁令 | **不 freeze** |
| `adapterControllers*` / Handler 命名 / 事务（#11–#16） | **`bone-blueprint` 不 freeze**；其它应用模块 **freeze 存量** |
| `noUseCase*` / `noNewDomainStore` / `noCustomBusinessException*` | **freeze**（防历史形态回潮） |

`@AnalyzeClasses` **必须**加 `importOptions = ImportOption.DoNotIncludeTests.class`，否则 `src/test` 下位于 `application.*` 的测试类会误触发 P0-1。
