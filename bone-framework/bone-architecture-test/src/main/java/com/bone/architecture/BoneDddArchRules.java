package com.bone.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
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

  /** bone-core 实体基类 FQN：聚合/实体的最终父类，用于识别「聚合身份 setter」的调用目标。 */
  private static final String ENTITY_BASE_CLASS = "com.bone.core.domain.entity.Entity";

  /** 聚合身份 setter 名（{@code AggregateRoot.setId} / {@code TenantAggregateRoot.setTenantId}）。 */
  private static final Set<String> AGGREGATE_IDENTITY_SETTERS = Set.of("setId", "setTenantId");

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

  /**
   * §3.1 聚合边界：外层（application / adapter / infrastructure）<strong>禁止直接调用</strong>聚合/实体的 {@code setId}
   * / {@code setTenantId}。
   *
   * <p><b>为何约束调用方，而不是把 setter 改成 protected</b>：{@code AggregateRoot.setId} 需供 SDK 反射回填 主键，{@code
   * TenantAggregateRoot.setTenantId} 是实现 {@code Tenantable} 接口的契约方法——二者<strong> 必须保持
   * public</strong>，降级为 protected 会破坏全平台编译。后果是聚合的身份与租户归属在语言层面
   * 可被任意外部代码篡改，对多租户系统尤其危险（改租户即越权跨租户访问）。既然无法约束<strong>被调用
   * 方</strong>，就改为约束<strong>调用方</strong>：只有领域层内部（工厂方法、聚合行为）可设置身份。
   *
   * <p>只匹配调用目标是 {@code Entity} 子类的 setter，故 DTO / Builder 上的 {@code setId} 不受影响。
   */
  public static ArchRule outerLayersMustNotMutateAggregateIdentity() {
    return noClasses()
        .that()
        .resideInAnyPackage("..application..", "..adapter..", "..infrastructure..")
        .should()
        // 注意：DescribedPredicate 是抽象类而非函数式接口，必须写匿名类，不能用 lambda。
        .callMethodWhere(
            new DescribedPredicate<JavaMethodCall>("set aggregate id or tenantId") {
              @Override
              public boolean test(JavaMethodCall call) {
                AccessTarget.MethodCallTarget target = call.getTarget();
                if (!AGGREGATE_IDENTITY_SETTERS.contains(target.getName())) {
                  return false;
                }
                if (!target.getOwner().isAssignableTo(ENTITY_BASE_CLASS)) {
                  return false;
                }
                // 排除「自己设置自己」：基础设施持久化模型（如 Outbox 记录）在自身工厂方法里回填主键是
                // 正常用法，且它并非业务聚合。真正要防的是外层代码篡改<strong>业务聚合</strong>的
                // 身份 / 租户归属——那时调用方与目标类型必然不同。
                return !call.getOriginOwner().equals(call.getTargetOwner());
              }
            })
        .because(
            "DDD §3.1: aggregate id/tenantId must only be set inside domain — SDK requires the "
                + "setters to stay public, so callers are constrained instead");
  }
}
