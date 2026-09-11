package com.bone.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Bone DDD 共享 ArchUnit 规则（真源：{@code doc/architecture/Bone-DDD-最终实践方案.md} E-3 铁律 R1–R9 与 G-1
 * 规则集；v4.6 起文档采用 P-/E-/G- 三段编号，本节注释中的 §xx 为历史章节号，仅作索引提示）。
 *
 * <p>应用模块在 {@code ArchitectureTest} 中组合使用；对存量违规请配合 {@code
 * com.tngtech.archunit.library.freeze.FreezingArchRule}，仅拦截新增。
 */
public final class BoneDddArchRules {

  /** E-9.2 写侧仓储方法允许的标量返回类型。 */
  private static final Set<String> REPOSITORY_SCALAR_RETURN_TYPES =
      Set.of("void", "boolean", "java.lang.Boolean");

  /** 禁止进入方法名的持久化词汇（E-9.2 主判据补充）。聚合级动词 {@code save} / {@code remove} 属 SDK 标准 写法，不在禁用之列。 */
  private static final Set<String> REPOSITORY_PERSISTENCE_VOCABULARY =
      Set.of("insert", "update", "delete", "persist", "flush", "merge");

  private static final String TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";

  /** bone-core 实体基类 FQN：聚合/实体的最终父类，用于识别「聚合身份 setter」的调用目标。 */
  private static final String ENTITY_BASE_CLASS = "com.bone.core.domain.entity.Entity";

  /** E-4.4 租户上下文 FQN：业务层禁止直调，仅 infrastructure 访问（v4.7 补门禁）。 */
  private static final String TENANT_CONTEXT_CLASS = "com.bone.core.tenant.context.TenantContext";

  /** 聚合身份 setter 名（{@code AggregateRoot.setId} / {@code TenantAggregateRoot.setTenantId}）。 */
  private static final Set<String> AGGREGATE_IDENTITY_SETTERS = Set.of("setId", "setTenantId");

  /** 聚合根基类 FQN（R9 判定：只有聚合根才计入「一事务一聚合」）。 */
  private static final String AGGREGATE_ROOT_CLASS = "com.bone.core.domain.AggregateRoot";

  /** SDK 仓储基接口 FQN（用于解析仓储的实体泛型参数）。 */
  private static final String SDK_REPOSITORY_CLASS = "com.bone.metadata.sdk.Repository";

  /** R9 判定用：写侧仓储的持久化方法名，出现即视为「持久化了一个聚合」。 */
  private static final Set<String> PERSIST_METHODS =
      Set.of(
          "save",
          "saveAll",
          "insert",
          "insertAll",
          "update",
          "updateAll",
          "remove",
          "removeAll",
          "delete",
          "persist");

  /** R9 判定用：写事务的入口方法名。 */
  private static final Set<String> TRANSACTION_ENTRY_METHODS = Set.of("handle", "execute");

  /** 持久化适配器角色后缀（#18 豁免）：这些类设置聚合身份属法定职责（主键回填 / ORM 恢复 / PO 映射）， 不属外层篡改。 */
  private static final List<String> PERSISTENCE_ADAPTER_SUFFIXES =
      List.of(
          "Repository",
          "RepositoryImpl",
          "Converter",
          "Mapper",
          "RowMapper",
          "Assembler",
          "Persister");

  /** 默认共享内核包（bone-core）：跨上下文规则恒不视为「其它上下文」。 */
  private static final String SHARED_KERNEL_PACKAGE = "com.bone.core";

  /** 默认共享内核包（bone-metadata-sdk，唯一持久化方案）：跨上下文规则恒不视为「其它上下文」。 */
  private static final String METADATA_SDK_PACKAGE = "com.bone.metadata.sdk";

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

  /**
   * §12.1 P0-7 + §15（E-6）：Controller 禁止直接注入 {@code application.service} 实现，也禁止注入 {@code
   * ..application..} 层中 {@code *Manager} / {@code Common*} / {@code Base*} / {@code Business*}
   * 命名的类（上帝对象反模式）。{@code ..application.facade..} / {@code ..application.orchestration..}
   * 门面与编排层不受影响。
   */
  public static ArchRule adapterControllersMustNotDependOnApplicationService() {
    DescribedPredicate<JavaClass> forbiddenApplicationTargets =
        resideInAPackage("..application.service..")
            .or(
                resideInAPackage("..application..")
                    .and(
                        simpleNameStartingWith("Common")
                            .or(simpleNameStartingWith("Base"))
                            .or(simpleNameStartingWith("Business"))
                            .or(simpleNameEndingWith("Manager"))));
    return noClasses()
        .that()
        .resideInAPackage("..adapter..controller..")
        .should()
        .dependOnClassesThat(forbiddenApplicationTargets)
        .allowEmptyShould(true)
        .because(
            "DDD P0-7 + §15: adapter must not inject application/service impl or "
                + "Manager/Common/Base/Business-named application classes; use facade/orchestration");
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

  /**
   * §23：{@code application.command.handler} 下类名应以 {@code CommandHandler} 结尾。
   *
   * <p><b>v4.5 已降级为 warn（非硬门禁）</b>：命名合规不可机器验证语义质量，改用 R8 聚合纯单测 （{@link
   * AggregatePureUnitTestGuard}）作为反贫血主判据；本规则保留供 CR / 手动检查使用，各模块 {@code ArchitectureTest} 不再以
   * {@code @ArchTest} 引用（见 tasks 2.5）。
   */
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

  /**
   * §23：{@code application.query.handler} 下类名应以 {@code QueryHandler} 结尾。
   *
   * <p><b>v4.5 已降级为 warn（非硬门禁）</b>：同 {@link #commandHandlersShouldBeNamedCommandHandler()}， 保留供 CR
   * / 手动检查使用，各模块 {@code ArchitectureTest} 不再以 {@code @ArchTest} 引用。
   */
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

  /**
   * §12.1 + §15：写侧 Handler 建议在类或 {@code handle}/{@code execute} 方法上标注 {@code @Transactional}。
   *
   * <p><b>v4.5 已降级为 warn（非硬门禁）</b>：事务边界是编写习惯而非命名约束，改由 CR / 代码评审把关； 本规则保留供手动检查，各模块 {@code
   * ArchitectureTest} 不再以 {@code @ArchTest} 引用。
   */
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
   * §12.1 P0-6：读侧 Handler 建议 {@code @Transactional(readOnly = true)}。
   *
   * <p><b>v4.5 已降级为 warn（非硬门禁）</b>：同 {@link #commandHandlersShouldBeTransactional()}， 保留供 CR /
   * 手动检查使用，各模块 {@code ArchitectureTest} 不再以 {@code @ArchTest} 引用。
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

  /**
   * §15 + §18.2（E-9.2 主判据）：写侧仓储是「聚合根的集合抽象」——方法返回类型只能是聚合根 / {@code Optional<聚合根>} / {@code boolean}
   * / {@code void}；返回投影 / DTO / {@code Page} / {@code List} 即 违规（那是读侧 QueryBuilder 的职责）。
   *
   * <p>允许复合自然键（如 {@code findByIdInTenant(id, tenantId)}，只要返回聚合）；方法名不得携带持久化词汇 （{@code
   * insert/update/delete/persist/flush/merge}），聚合级动词 {@code save} / {@code remove} 除外。
   *
   * <p>方法名保留旧名 {@code ShouldOnlyDeclareWhitelistedMethods} 以兼容各模块 {@code ArchitectureTest}
   * 引用；判据已从「方法名白名单（脆弱启发式：{@code queryByStatusAndType} 可绕过）」升级为「返回类型」。
   */
  public static ArchRule domainRepositoriesShouldOnlyDeclareWhitelistedMethods() {
    return classes()
        .that()
        .areInterfaces()
        .and()
        .haveSimpleNameEndingWith("Repository")
        .and()
        .resideInAPackage("..domain.repository..")
        .should(onlyDeclareAggregateLoadMethods())
        .allowEmptyShould(true);
  }

  /**
   * P-2.3 / P-10.4（D9）：跨上下文边界守护——本模块 {@code ..domain..} 禁止直接依赖<strong>其它 Bone 上下文</strong>的 {@code
   * domain} 包（跨上下文集成须经公开 API / 事件契约 / 防腐层，而非直接 import 对方领域模型）。
   *
   * <p><b>判定方式</b>：按依赖目标的全限定名匹配（匹配字节码中记录的引用，与 classpath 无关）；排除 自身模块根包与共享内核包（默认 {@code
   * com.bone.core} 与 {@code com.bone.metadata.sdk}，可通过参数追加更多白名单）。
   *
   * <p><b>不加 {@code allowEmptyShould(true)}</b>：空匹配视为配置错误——若本模块没有任何 {@code domain}
   * 类，规则立即失败，防止「对方类不在 classpath 时规则空命中即通过」等于没有守护。
   *
   * @param selfRootPackage 本模块根包（如 {@code com.bone.blueprint}）；其下 {@code domain} 包中的类为判定主体
   * @param sharedKernelPackages 追加的共享内核白名单包（默认已含 {@code com.bone.core}、{@code
   *     com.bone.metadata.sdk}）
   */
  public static ArchRule noCrossContextDomainDependency(
      String selfRootPackage, String... sharedKernelPackages) {
    DescribedPredicate<JavaClass> otherContextDomain =
        otherContextDomainPredicate(selfRootPackage, sharedKernelList(sharedKernelPackages));
    return noClasses()
        .that()
        .resideInAPackage(selfRootPackage + ".domain..")
        .should()
        .dependOnClassesThat(otherContextDomain)
        .because(
            "DDD P-2.3 + P-10.4: cross-context integration must go through public API / event "
                + "contract / anti-corruption layer, not direct domain-type dependency; empty "
                + "match means the rule is misconfigured");
  }

  private static boolean isWithinOrEquals(String fqn, String root) {
    return fqn.equals(root) || fqn.startsWith(root + ".");
  }

  private static ArchCondition<JavaClass> onlyDeclareAggregateLoadMethods() {
    return new ArchCondition<>(
        "only return aggregate root / Optional<aggregate root> / boolean / void") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (JavaMethod method : item.getMethods()) {
          if (method.getModifiers().contains(JavaModifier.STATIC)) {
            continue;
          }
          String name = method.getName();
          if (hasPersistenceVocabularyInName(name)) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    String.format(
                        "Repository %s declares method %s with persistence vocabulary — write-side "
                            + "repository methods must read as domain operations",
                        item.getSimpleName(), name)));
            continue;
          }
          if (!returnsAggregateRootOrScalar(method)) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    String.format(
                        "Repository %s declares method %s returning %s — write-side repositories must "
                            + "return aggregate root / Optional<aggregate root> / boolean / void; use "
                            + "QueryBuilder for projections",
                        item.getSimpleName(),
                        name,
                        method.getReturnType().toErasure().getSimpleName())));
          }
        }
      }
    };
  }

  private static boolean returnsAggregateRootOrScalar(JavaMethod method) {
    JavaClass rawType = method.getReturnType().toErasure();
    String typeName = rawType.getName();
    if (REPOSITORY_SCALAR_RETURN_TYPES.contains(typeName)) {
      return true;
    }
    if (rawType.isAssignableTo(ENTITY_BASE_CLASS)) {
      return true;
    }
    if ("java.util.Optional".equals(typeName)) {
      return method.getReturnType() instanceof JavaParameterizedType parameterized
          && parameterized.getActualTypeArguments().size() == 1
          && isEntityType(parameterized.getActualTypeArguments().get(0));
    }
    return false;
  }

  private static boolean isEntityType(JavaType type) {
    return type instanceof JavaClass clazz && clazz.isAssignableTo(ENTITY_BASE_CLASS);
  }

  private static boolean hasPersistenceVocabularyInName(String methodName) {
    for (String word : camelCaseWords(methodName)) {
      if (REPOSITORY_PERSISTENCE_VOCABULARY.contains(word)) {
        return true;
      }
    }
    return false;
  }

  /** 驼峰拆词并小写：{@code findByIdInTenant} → [find, by, id, in, tenant]。 */
  private static List<String> camelCaseWords(String name) {
    return Arrays.stream(name.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"))
        .map(String::toLowerCase)
        .collect(Collectors.toList());
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
        .and(notPersistenceAdapterRole())
        .should()
        // 注意：DescribedPredicate 是抽象类而非函数式接口，必须写匿名类，不能用 lambda。
        .callMethodWhere(
            new DescribedPredicate<JavaMethodCall>("set a domain aggregate's id or tenantId") {
              @Override
              public boolean test(JavaMethodCall call) {
                AccessTarget.MethodCallTarget target = call.getTarget();
                if (!AGGREGATE_IDENTITY_SETTERS.contains(target.getName())) {
                  return false;
                }
                JavaClass owner = target.getOwner();
                if (!owner.isAssignableTo(ENTITY_BASE_CLASS)) {
                  return false;
                }
                // 排除基础设施 PO：PO↔领域对象互转时设置主键 / 租户列是映射动作，PO 不是聚合、
                // 不承载一致性边界。（bone-extension-studio 的 StudioPersistenceConverter 即因此误报。）
                if (owner.getPackageName().contains(".infrastructure.")) {
                  return false;
                }
                // 排除「自己设置自己」：基础设施持久化模型（如 Outbox 记录）在自身工厂方法里回填主键
                // 是正常用法。真正要防的是外层代码篡改<strong>业务聚合</strong>的身份 / 租户归属。
                return !call.getOriginOwner().equals(call.getTargetOwner());
              }
            })
        .because(
            "DDD §3.1 / E-8: aggregate id/tenantId must only be set inside domain — SDK requires "
                + "the setters to stay public, so callers are constrained instead; persistence "
                + "adapters (Repository/Converter/Mapper) and PO mapping are exempt");
  }

  /**
   * 持久化适配器角色豁免：{@code *Repository} / {@code *Converter} / {@code *Mapper} 等类的职责就是 主键回填、ORM 恢复与
   * PO↔领域对象互转，设置身份是<strong>法定职责</strong>而非篡改——E-9.1 明确 SDK 在 {@code insert} / {@code save}
   * 时生成并回填主键，E-8 也承认 ORM 恢复经字段级反射。
   *
   * <p>真正的越权风险在<strong>业务代码</strong>（{@code application} / {@code adapter}）改聚合的租户 归属或主键，那部分不受本豁免影响。
   */
  private static DescribedPredicate<JavaClass> notPersistenceAdapterRole() {
    return new DescribedPredicate<JavaClass>(
        "not be a persistence adapter (Repository/Converter/Mapper/Assembler)") {
      @Override
      public boolean test(JavaClass input) {
        String simpleName = input.getSimpleName();
        return PERSISTENCE_ADAPTER_SUFFIXES.stream().noneMatch(simpleName::endsWith);
      }
    };
  }

  /**
   * R9（v4.6）：一个写事务内只持久化<strong>一个</strong>聚合根（真源：主规范 E-3.1.2）。
   *
   * <p><b>为何单列一条</b>：聚合在 DDD 中的定义即「一致性边界」。若一个事务内同时写多个聚合，等于宣称
   * 二者的不变量必须同时成立——那它们本就该是同一个聚合；若不是，则事务边界与一致性边界背离，既放大锁 竞争，又把最终一致伪装成强一致（本地事务只能保证本库原子）。
   *
   * <p><b>判定方式</b>：对 {@code ..application.command.handler..} / {@code ..application.service..} /
   * {@code ..application.orchestration..} 下具体类的<strong>入口方法</strong>（{@code handle} / {@code
   * execute}；不存在时回退到全部 public 实例方法），收集其直接调用中目标位于 {@code ..domain.repository..} 的持久化方法，按
   * <strong>Repository 接口类型</strong>去重计数， {@code > 1} 即违规。
   *
   * <p><b>为何扫描范围含 service / orchestration（v4.7 修正）</b>：Handler 委托给 ApplicationService /
   * Orchestrator 后， 直接在 Handler 上扫描 save 调用将完全不可见（v4.6 的盲区）——ApplicationService 同样可能是写事务入口，须受 R9 约束
   * （E-5.3.1 内容禁令 #5）。门禁按「直接调用」判定：Handler → Service → save(A)+save(B) 会在 Service 层被拦截。
   *
   * <p><b>为何按「聚合根类型」而非「Repository 类型」去重（v4.7 修正）</b>：R9 约束的对象是<strong>聚合</strong>，不是仓储接口。
   * 按仓储计数会把聚合内子实体的仓储（如 {@code OrderItemRepository}）误判为第二个聚合，进而诱导实现方把端口挪出 {@code domain.repository}
   * 包来"绕过门禁"——既违反 E-5.5，也让规则形同虚设。改为解析 {@code Repository<T, ID>} 的 泛型参数，只有 {@code T} 是 {@code
   * AggregateRoot} 子类时才计数，{@code Order} + {@code OrderItem} 因此正确地算作一个聚合。<b>按调用次数仍不去重</b>：同一聚合
   * {@code save} 两次仍属单聚合（幂等 upsert）。
   */
  public static ArchRule oneAggregatePerTransaction() {
    return classes()
        .that()
        .resideInAnyPackage(
            "..application.command.handler..",
            "..application.service..",
            "..application.orchestration..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(persistAtMostOneAggregateTypePerTransaction())
        .allowEmptyShould(true);
  }

  private static ArchCondition<JavaClass> persistAtMostOneAggregateTypePerTransaction() {
    return new ArchCondition<>("persist at most one aggregate type per write transaction") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (JavaMethod entry : transactionEntryMethods(item)) {
          Set<String> aggregates = new TreeSet<>();
          for (JavaMethodCall call : entry.getMethodCallsFromSelf()) {
            AccessTarget.MethodCallTarget target = call.getTarget();
            if (!PERSIST_METHODS.contains(target.getName())) {
              continue;
            }
            if (!isDomainRepository(target.getOwner())) {
              continue;
            }
            // 按「被持久化的聚合根类型」计数：聚合内子实体的仓储不计入（见 persistedAggregateRootKey）
            String aggregateKey = persistedAggregateRootKey(target.getOwner());
            if (aggregateKey == null) {
              continue;
            }
            aggregates.add(aggregateKey);
          }
          if (aggregates.size() > 1) {
            events.add(
                SimpleConditionEvent.violated(
                    item,
                    String.format(
                        "%s.%s() persists %d aggregate types in one transaction %s — R9: one"
                            + " aggregate per transaction; use domain events / Outbox /"
                            + " Orchestrator for cross-aggregate changes",
                        item.getSimpleName(), entry.getName(), aggregates.size(), aggregates)));
          }
        }
      }
    };
  }

  /** 写事务入口方法：名为 handle / execute 的方法；不存在时回退到全部 public 实例方法。 */
  private static List<JavaMethod> transactionEntryMethods(JavaClass item) {
    List<JavaMethod> named =
        item.getMethods().stream()
            .filter(method -> TRANSACTION_ENTRY_METHODS.contains(method.getName()))
            .collect(Collectors.toList());
    if (!named.isEmpty()) {
      return named;
    }
    return item.getMethods().stream()
        .filter(
            method ->
                method.getModifiers().contains(JavaModifier.PUBLIC)
                    && !method.getModifiers().contains(JavaModifier.STATIC))
        .collect(Collectors.toList());
  }

  private static boolean isDomainRepository(JavaClass owner) {
    return owner.isInterface() && owner.getPackageName().contains(".domain.repository");
  }

  /**
   * R9 计数键：返回该仓储持久化的<strong>聚合根类型名</strong>；不是聚合根时返回 {@code null}（不计入 R9）。
   *
   * <p><b>为何按聚合根类型而非 Repository 类型计数（v4.7 修正）</b>：R9 约束的是「一个事务内只提交一个<strong>聚合</strong> 的变更」。若按
   * Repository 接口计数，聚合内子实体（如 {@code OrderItem}）的仓储会被误算成第二个"聚合"， 迫使实现方为了让门禁变绿而<strong>把仓储挪出 {@code
   * domain.repository} 包</strong>——那既违反 E-5.5（端口包唯一）， 又让门禁失去意义（改个包名就能绕过）。按类型判定后，「Order + OrderItem
   * 同属一个聚合」是自然结论， 包位置回归规范。
   *
   * <p><b>解析方式</b>：从仓储接口的 {@code Repository<T, ID>} 泛型实参取 {@code T}，再判断 {@code T} 是否继承 {@code
   * AggregateRoot}；解析不到泛型时<strong>保守按仓储名计数</strong>（宁可多报，不放过）。
   */
  private static String persistedAggregateRootKey(JavaClass repository) {
    JavaClass entity = resolveRepositoryEntityType(repository);
    if (entity == null) {
      return repository.getSimpleName(); // 保守兜底：泛型不可解析时仍计数
    }
    return isAggregateRoot(entity) ? entity.getSimpleName() : null;
  }

  /** 解析仓储接口的实体泛型参数 {@code T}（递归向父接口查找 {@code com.bone.metadata.sdk.Repository}）。 */
  private static JavaClass resolveRepositoryEntityType(JavaClass repository) {
    for (JavaType iface : repository.getInterfaces()) {
      if (SDK_REPOSITORY_CLASS.equals(iface.toErasure().getName())) {
        if (iface instanceof JavaParameterizedType parameterized) {
          List<JavaType> args = parameterized.getActualTypeArguments();
          if (!args.isEmpty()) {
            return args.get(0).toErasure();
          }
        }
        return null;
      }
      JavaClass nested = resolveRepositoryEntityType(iface.toErasure());
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static boolean isAggregateRoot(JavaClass entity) {
    if (AGGREGATE_ROOT_CLASS.equals(entity.getName())) {
      return true;
    }
    return entity.getAllRawSuperclasses().stream()
        .anyMatch(superClass -> AGGREGATE_ROOT_CLASS.equals(superClass.getName()));
  }

  /**
   * E-4.4（v4.7 补门禁）：租户取值收敛到模块统一端口（{@code TenantProvider}）。
   *
   * <p><b>为何新增门禁</b>：规范禁止业务代码散落 {@code TenantContext.get*()} 直调——线程本地直调读得分散、
   * 无法审计、异步线程丢失，且框架层写入、业务层只读。此前只有条文无门禁（违反 E-9.6 原则：规范不应宣称 尚未具备支撑能力的约束），本版补齐。
   *
   * <p><b>判定</b>：{@code application} / {@code domain} / {@code adapter} 调用 {@code TenantContext}
   * 任意方法即违规； 仅允许 {@code infrastructure}（写入过滤器、{@code TenantProvider} 端口实现）访问。领域层通过 {@code
   * domain/gateway/TenantProvider} 端口取值，实现下沉 infrastructure。
   */
  public static ArchRule businessLayersMustNotReadTenantContextDirectly() {
    return noClasses()
        .that()
        .resideInAnyPackage("..application..", "..domain..", "..adapter..")
        .should()
        .callMethodWhere(
            new DescribedPredicate<JavaMethodCall>(
                "call com.bone.core.tenant.context.TenantContext") {
              @Override
              public boolean test(JavaMethodCall call) {
                return call.getTargetOwner().getName().equals(TENANT_CONTEXT_CLASS);
              }
            })
        .allowEmptyShould(true)
        .because(
            "DDD E-4.4: tenant context reads must go through the module's single TenantProvider port"
                + " (domain/gateway) implemented in infrastructure; direct TenantContext calls in"
                + " application/domain/adapter bypass audit, propagation and null-guards");
  }

  /**
   * E-9.3（v4.6 主判据）：读侧 DSL 只允许出现在基础设施查询层，禁止进入 {@code application}。
   *
   * <p><b>为何用位置判据替代注解判据</b>：注解判据（{@code @ReadSideOnly}）属「标记坏人」式否定检测——新增 一个 DSL
   * 类型忘了标注，所有依赖它的类全部静默放行。位置判据按包路径判定，漏标无法绕过。注解规则 {@link #domainMustNotUseQueryBuilder()} / {@link
   * #commandHandlersMustNotUseQueryBuilder()} 保留为防回滚补充。
   *
   * <p><b>目标态</b>：{@code *QueryHandler} 只依赖读侧端口（{@code *ReadPort}），DSL 实现下沉 {@code
   * infrastructure/query}。存量在 QueryHandler 中直用 DSL 的代码以 {@code FreezingArchRule} 登记后逐步 收敛（E-9.3
   * 过渡期双轨）。
   */
  public static ArchRule readSideDslOnlyInQueryLayer() {
    return noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat(areReadSideDsl())
        .allowEmptyShould(true)
        .because(
            "DDD E-9.3: read-side DSL must live in infrastructure/query behind a *ReadPort; "
                + "the application layer must not depend on persistence DSL");
  }

  private static DescribedPredicate<JavaClass> areReadSideDsl() {
    return new DescribedPredicate<JavaClass>("be a read-side DSL type") {
      @Override
      public boolean test(JavaClass input) {
        return input.getPackageName().startsWith("com.bone.metadata.sdk.query")
            || input.isAnnotatedWith("com.bone.core.annotation.ReadSideOnly");
      }
    };
  }

  /**
   * E-5.3.1（v4.6 内容禁令）：{@code application/service} 可承载<strong>用例级编排</strong>，但不得承载 领域规则——具体禁止两件事：
   *
   * <ol>
   *   <li>{@code new} 领域对象（须经聚合工厂方法或 Repository 获取）；
   *   <li>调用聚合 setter 修改状态（须经领域行为方法，反贫血 R2）。
   * </ol>
   *
   * <p>v4.5 曾全面禁止 {@code application/service} 这一<strong>名称</strong>，结果真实需求被逼进 Orchestrator / Facade
   * / ApplicationService 三套例外通道。v4.6 改为约束<strong>内容</strong>——管住 「里面不许有什么」，比管「不许叫什么」更紧，且可机器判定。
   */
  public static ArchRule applicationServicesMustNotOwnDomainRules() {
    return classes()
        .that()
        .resideInAPackage("..application.service..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(notOwnDomainRules())
        .allowEmptyShould(true);
  }

  private static ArchCondition<JavaClass> notOwnDomainRules() {
    return new ArchCondition<>("not instantiate or mutate domain objects directly") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (JavaConstructorCall call : item.getConstructorCallsFromSelf()) {
          JavaClass owner = call.getTarget().getOwner();
          if (!isDomainModel(owner)) {
            continue;
          }
          events.add(
              SimpleConditionEvent.violated(
                  item,
                  String.format(
                      "%s instantiates domain type %s — application services must obtain domain"
                          + " objects from aggregate factory methods or a Repository (E-5.3.1)",
                      item.getSimpleName(), owner.getSimpleName())));
        }
        for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
          AccessTarget.MethodCallTarget target = call.getTarget();
          if (!target.getName().startsWith("set")) {
            continue;
          }
          if (!target.getOwner().isAssignableTo(ENTITY_BASE_CLASS)) {
            continue;
          }
          events.add(
              SimpleConditionEvent.violated(
                  item,
                  String.format(
                      "%s calls %s.%s() — application services must change state through domain"
                          + " behaviour methods, not setters (E-5.3.1 / R2 anti-anemia)",
                      item.getSimpleName(), target.getOwner().getSimpleName(), target.getName())));
        }
      }
    };
  }

  /** 领域模型类型：{@code ..domain..} 包下的 {@code Entity} 子类（排除基础设施 PO 与 DTO）。 */
  private static boolean isDomainModel(JavaClass owner) {
    return owner.isAssignableTo(ENTITY_BASE_CLASS)
        && (owner.getPackageName().contains(".domain.")
            || owner.getPackageName().endsWith(".domain"));
  }

  /**
   * E-4.1.1（v4.6）：跨上下文<strong>模型</strong>依赖守护——本模块<strong>任何层</strong>（含读侧 QueryHandler 与 {@code
   * infrastructure.query}）均禁止直接依赖其它 Bone 上下文的 {@code domain} 模型类型。
   *
   * <p><b>与 {@link #noCrossContextDomainDependency(String, String...)} 的区别</b>：后者只约束本模块 {@code
   * domain} 包的依赖（战术层守护），本条覆盖<strong>全模块</strong>，用于拦截「读侧直接 {@code
   * QueryBuilder.from(其他上下文的实体)}」——这是限界上下文最容易被穿透的地方：类型依赖好查，跨表读 难查，而后者恰恰是数据所有权失守的主要形式。
   *
   * <p>同样<b>不加</b> {@code allowEmptyShould(true)}：空匹配视为配置错误。
   *
   * @param selfRootPackage 本模块根包（如 {@code com.bone.integration}）
   * @param sharedKernelPackages 追加的共享内核白名单包（默认已含 {@code com.bone.core}、{@code
   *     com.bone.metadata.sdk}）
   */
  public static ArchRule noCrossContextModelDependency(
      String selfRootPackage, String... sharedKernelPackages) {
    DescribedPredicate<JavaClass> otherContextDomain =
        otherContextDomainPredicate(selfRootPackage, sharedKernelList(sharedKernelPackages));
    return noClasses()
        .that()
        .resideInAPackage(selfRootPackage + "..")
        .should()
        .dependOnClassesThat(otherContextDomain)
        .because(
            "DDD E-4.1.1: data ownership is per context — reading another context's domain model "
                + "(including read-side queries) must go through public API or event projection; "
                + "empty match means the rule is misconfigured");
  }

  private static DescribedPredicate<JavaClass> otherContextDomainPredicate(
      String selfRootPackage, List<String> sharedKernel) {
    return new DescribedPredicate<JavaClass>("belong to another Bone context's domain package") {
      @Override
      public boolean test(JavaClass target) {
        String name = target.getName();
        if (!name.startsWith("com.bone.")) {
          return false;
        }
        if (isWithinOrEquals(name, selfRootPackage)) {
          return false;
        }
        for (String shared : sharedKernel) {
          if (isWithinOrEquals(name, shared)) {
            return false;
          }
        }
        return name.contains(".domain.");
      }
    };
  }

  private static List<String> sharedKernelList(String... extraPackages) {
    List<String> sharedKernel = new ArrayList<>();
    sharedKernel.add(SHARED_KERNEL_PACKAGE);
    sharedKernel.add(METADATA_SDK_PACKAGE);
    sharedKernel.addAll(Arrays.asList(extraPackages));
    return sharedKernel;
  }
}
