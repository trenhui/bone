package com.bone.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

/**
 * Bone DDD 共享 ArchUnit 规则（真源：doc/architecture/Bone-DDD-最终实践方案.md §12、§21）。
 *
 * <p>应用模块在 {@code ArchitectureTest} 中组合使用；对存量违规请配合 {@code
 * com.tngtech.archunit.library.freeze.FreezingArchRule}，仅拦截新增。
 */
public final class BoneDddArchRules {

  /** §18.2 仓储方法名白名单（不含 {@code findBy...} 以外自定义命名）。 */
  private static final Set<String> REPOSITORY_METHOD_WHITELIST =
      Set.of("save", "saveAll", "remove", "removeAll", "findById", "findAllById", "existsById");

  /** §18.2 单键辅助方法允许的前缀（如 {@code existsByCode}、{@code findByBusinessKey}）。 */
  private static final Set<String> REPOSITORY_METHOD_PREFIX_WHITELIST =
      Set.of("existsBy", "findBy");

  /** §18.2 禁止的多条件组合片段（方法名中出现即违规）。 */
  private static final Set<String> REPOSITORY_METHOD_FORBIDDEN_FRAGMENTS = Set.of("And", "Or");

  private static final String TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";

  private BoneDddArchRules() {}

  public static ArchRule domainMustNotDependOnOuterLayers() {
    return noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..")
        .because("DDD P0-1: domain must not depend on outer layers");
  }

  public static ArchRule applicationMustNotDependOnInfrastructure() {
    return noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..")
        .because("DDD P0-1: application must not depend on infrastructure implementations");
  }

  public static ArchRule domainMustNotUseQueryBuilder() {
    return noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat(areAnnotatedWithReadSideOnly())
        .because("DDD P0-5: read-side DSL (@ReadSideOnly) must not appear in domain");
  }

  public static ArchRule commandHandlersMustNotUseQueryBuilder() {
    return noClasses()
        .that()
        .resideInAPackage("..application.command.handler..")
        .should()
        .dependOnClassesThat(areAnnotatedWithReadSideOnly())
        .because("DDD P0-6: CommandHandler must not use read-side DSL");
  }

  public static ArchRule noUseCaseClassesInApplication() {
    return noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .haveSimpleNameEndingWith("UseCase")
        .allowEmptyShould(true)
        .because("DDD §14.3: forbid *UseCase; Controller injects Handler");
  }

  public static ArchRule noApplicationUseCasePackage() {
    return noClasses()
        .should()
        .resideInAPackage("..application.usecase..")
        .allowEmptyShould(true)
        .because("DDD §14.3: forbid application/usecase package");
  }

  /** 防回滚：{@code com.bone.core.usecase} 已从 bone-core 删除。不纳入 freeze（无存量命中）。 */
  public static ArchRule noBoneCoreUseCaseApiDependency() {
    return noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.bone.core.usecase..")
        .because(
            "DDD P0-7: com.bone.core.usecase was removed; use Handler + com.bone.core.capability");
  }

  /** studio-generator 自造 {@code @UseCase} 注解，禁止业务模块新增依赖。 */
  public static ArchRule noStudioGeneratorUseCaseAnnotation() {
    return noClasses()
        .that()
        .resideOutsideOfPackage("com.bone.studio.generator.application.usecase..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.bone.studio.generator.application.usecase..")
        .because("studio-generator must not export custom UseCase SPI to other modules");
  }

  public static ArchRule noNewDomainStorePackage() {
    return noClasses()
        .should()
        .resideInAPackage("..domain.store..")
        .allowEmptyShould(true)
        .because("DDD §14.5: new code uses domain.repository only");
  }

  /**
   * §16.3：业务模块禁止自建 {@code BusinessException} / {@code *BusinessException}， 统一使用 {@code
   * com.bone.core.exception.BizException}。允许 {@code *BizException} 后缀基类 （如 {@code
   * MetadataEngineBizException}）**继承** {@code BizException}，但禁止 simpleName 等于 {@code
   * BusinessException} 或以 {@code BusinessException} 结尾。
   */
  public static ArchRule noCustomBusinessException() {
    return noClasses()
        .that()
        .resideOutsideOfPackage("com.bone.core..")
        .should()
        .haveSimpleName("BusinessException")
        .allowEmptyShould(true)
        .because("DDD §16.3: business exception is com.bone.core.exception.BizException");
  }

  /**
   * §16.3 加强：禁止业务/引擎模块新增 {@code *BusinessException} 后缀类，鼓励改名为 {@code *BizException} 或继承 {@code
   * BizException}；引擎 SDK 使用 {@code ExtensionBizException} 等命名。
   */
  public static ArchRule noBusinessExceptionSuffix() {
    return noClasses()
        .that()
        .resideOutsideOfPackage("com.bone.core..")
        .should()
        .haveSimpleNameEndingWith("BusinessException")
        .allowEmptyShould(true)
        .because("DDD §16.3: prefer *BizException or extend com.bone.core.exception.BizException");
  }

  /** §12.1 P0-7 + §15：Controller 禁止直接注入 {@code application.service}。 */
  public static ArchRule adapterControllersMustNotDependOnApplicationService() {
    return noClasses()
        .that()
        .resideInAPackage("..adapter..controller..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..application.service..")
        .allowEmptyShould(true)
        .because("DDD P0-7 + §15: adapter must not inject application/service");
  }

  /** §15：Controller 禁止直接注入 {@code domain.repository} 写侧仓储。 */
  public static ArchRule adapterControllersMustNotDependOnDomainRepository() {
    return noClasses()
        .that()
        .resideInAPackage("..adapter..controller..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..domain.repository..")
        .allowEmptyShould(true)
        .because("DDD §15: adapter must not inject domain.repository; use Handler");
  }

  /**
   * P0-7 + §15：Controller 禁止直接注入 {@code domain.service} 领域服务。
   *
   * <p>领域服务只能由 {@code *CommandHandler} / {@code *QueryHandler} / {@code *Orchestrator}
   * 在应用层编排时调用，不允许 adapter 层越层直接依赖，以保持分层边界清晰。
   */
  public static ArchRule adapterControllersMustNotDependOnDomainService() {
    return noClasses()
        .that()
        .resideInAPackage("..adapter..controller..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..domain.service..")
        .allowEmptyShould(true)
        .because("DDD P0-7 + §15: adapter must not inject domain.service; use Handler");
  }

  /** §23：{@code application.command.handler} 下类名必须以 {@code CommandHandler} 结尾。 */
  public static ArchRule commandHandlersShouldBeNamedCommandHandler() {
    return classes()
        .that()
        .resideInAPackage("..application.command.handler..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should()
        .haveSimpleNameEndingWith("CommandHandler")
        .allowEmptyShould(true)
        .because("DDD §23: command handler classes must be named *CommandHandler");
  }

  /** §23：{@code application.query.handler} 下类名必须以 {@code QueryHandler} 结尾。 */
  public static ArchRule queryHandlersShouldBeNamedQueryHandler() {
    return classes()
        .that()
        .resideInAPackage("..application.query.handler..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should()
        .haveSimpleNameEndingWith("QueryHandler")
        .allowEmptyShould(true)
        .because("DDD §23: query handler classes must be named *QueryHandler");
  }

  /** §12.1 + §15：写侧 Handler 须在类或 {@code handle}/{@code execute} 方法上标注 {@code @Transactional}。 */
  public static ArchRule commandHandlersShouldBeTransactional() {
    return classes()
        .that()
        .resideInAPackage("..application.command.handler..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(haveTransactionalOnClassOrEntryMethod())
        .allowEmptyShould(true)
        .because("DDD §15: write transaction boundary on *CommandHandler");
  }

  /**
   * §12.1 P0-6：读侧 Handler 建议 {@code @Transactional(readOnly = true)}；规则至少要求存在
   * {@code @Transactional}（类或 {@code handle}/{@code execute} 方法）。
   */
  public static ArchRule queryHandlersShouldBeReadOnlyTransactional() {
    return classes()
        .that()
        .resideInAPackage("..application.query.handler..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(haveTransactionalOnClassOrEntryMethod())
        .allowEmptyShould(true)
        .because("DDD §12.1 P0-6: QueryHandler should use @Transactional(readOnly = true)");
  }

  /** §15 + §18.2：领域仓储接口方法名白名单（含 {@code existsBy*}/{@code findBy*} 前缀）。 */
  public static ArchRule domainRepositoriesShouldOnlyDeclareWhitelistedMethods() {
    return classes()
        .that()
        .areInterfaces()
        .and()
        .haveSimpleNameEndingWith("Repository")
        .and()
        .resideInAPackage("..domain.repository..")
        .should(onlyDeclareWhitelistedRepositoryMethods())
        .allowEmptyShould(true);
  }

  private static ArchCondition<JavaClass> onlyDeclareWhitelistedRepositoryMethods() {
    return new ArchCondition<>("only declare whitelisted repository methods") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (JavaMethod method : item.getMethods()) {
          String name = method.getName();
          if (REPOSITORY_METHOD_WHITELIST.contains(name)) {
            continue;
          }
          boolean matchPrefix =
              REPOSITORY_METHOD_PREFIX_WHITELIST.stream().anyMatch(name::startsWith);
          if (matchPrefix) {
            if (hasForbiddenCombinationInName(name)) {
              String message =
                  String.format(
                      "Repository %s declares multi-condition method %s — use QueryBuilder",
                      item.getSimpleName(), name);
              events.add(SimpleConditionEvent.violated(method, message));
            }
            continue;
          }
          String message =
              String.format(
                  "Repository %s declares non-whitelisted method %s — use QueryBuilder for read",
                  item.getSimpleName(), name);
          events.add(SimpleConditionEvent.violated(method, message));
        }
      }
    };
  }

  private static boolean hasForbiddenCombinationInName(String methodName) {
    for (String fragment : REPOSITORY_METHOD_FORBIDDEN_FRAGMENTS) {
      if (methodName.contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  private static ArchCondition<JavaClass> haveTransactionalOnClassOrEntryMethod() {
    return new ArchCondition<>("declare @Transactional on class or handle/execute method") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (item.isAnnotatedWith(TRANSACTIONAL)) {
          return;
        }
        boolean entryMethodTransactional =
            item.getMethods().stream()
                .filter(
                    method ->
                        "handle".equals(method.getName()) || "execute".equals(method.getName()))
                .anyMatch(method -> method.isAnnotatedWith(TRANSACTIONAL));
        if (!entryMethodTransactional) {
          String message =
              String.format(
                  "%s must declare @Transactional on class or handle()/execute() method",
                  item.getSimpleName());
          events.add(SimpleConditionEvent.violated(item, message));
        }
      }
    };
  }

  private static DescribedPredicate<JavaClass> areAnnotatedWithReadSideOnly() {
    return new DescribedPredicate<>("annotated with @ReadSideOnly") {
      @Override
      public boolean test(JavaClass input) {
        return input.isAnnotatedWith("com.bone.core.annotation.ReadSideOnly");
      }
    };
  }
}
