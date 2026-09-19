package com.bone.iam.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(packages = "com.bone.iam", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule no_new_use_cases =
      FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

  @ArchTest
  static final ArchRule no_usecase_package =
      FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

  @ArchTest
  static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

  // 《Bone-DDD》§14.5
  @ArchTest
  static final ArchRule no_new_domain_store =
      FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

  // 《Bone-DDD》§16.3
  @ArchTest
  static final ArchRule no_custom_business_exception =
      FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

  @ArchTest
  static final ArchRule no_business_exception_suffix =
      FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

  // CORE-02：application -> infrastructure
  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // CORE-05：读侧 DSL 边界
  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  @ArchTest
  static final ArchRule command_no_query_builder =
      FreezingArchRule.freeze(BoneDddArchRules.commandHandlersMustNotUseQueryBuilder());

  @ArchTest
  static final ArchRule read_side_dsl_only_in_query_adapter =
      FreezingArchRule.freeze(BoneDddArchRules.readSideDslOnlyInQueryLayer());

  @ArchTest
  static final ArchRule business_layers_no_direct_tenant_context =
      FreezingArchRule.freeze(BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly());

  // CORE-02 + E-6（A 类强制，不 freeze）：禁外层篡改聚合 setId / setTenantId
  @ArchTest
  static final ArchRule aggregate_identity_immutable =
      BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

  // §18.2：仓储方法白名单
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  @ArchTest
  static void domainLayerShouldNotDependOnOuterLayers(JavaClasses classes) {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..")
        .check(classes);
  }

  @ArchTest
  static void domainCoreShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
    classes()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "..domain..",
            "java..",
            "com.bone.core..",
            "com.bone.metadata.sdk..",
            "lombok..",
            "org.springframework.lang..")
        .check(classes);
  }

  @ArchTest
  static void repositoryInterfacesShouldBeInDomain(JavaClasses classes) {
    classes()
        .that()
        .haveNameMatching(".*Repository")
        .and()
        .areInterfaces()
        .should()
        .resideInAPackage("..domain..")
        .check(classes);
  }

  @ArchTest
  static void repositoryImplementationsShouldBeInInfrastructure(JavaClasses classes) {
    classes()
        .that()
        .haveNameMatching(".*RepositoryImpl")
        .should()
        .resideInAPackage("..infrastructure..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @ArchTest
  static void domainRepositoriesShouldNotDeclareCustomMethods(JavaClasses classes) {
    classes()
        .that()
        .areInterfaces()
        .and()
        .haveNameMatching(".*Repository")
        .and()
        .resideInAPackage("..domain.repository..")
        .should(notDeclareCustomRepositoryMethods())
        .check(classes);
  }

  private static ArchCondition<com.tngtech.archunit.core.domain.JavaClass>
      notDeclareCustomRepositoryMethods() {
    return new ArchCondition<>("not declare custom repository methods") {
      @Override
      public void check(com.tngtech.archunit.core.domain.JavaClass item, ConditionEvents events) {
        for (JavaMethod method : item.getMethods()) {
          if (method.getOwner().isEquivalentTo(Object.class) || method.isConstructor()) {
            continue;
          }
          String name = method.getName();
          if ("equals".equals(name) || "hashCode".equals(name) || "toString".equals(name)) {
            continue;
          }
          String message =
              String.format(
                  "Repository %s declares %s — use Metadata SDK Repository/Criteria",
                  item.getSimpleName(), method.getName());
          events.add(SimpleConditionEvent.violated(item, message));
        }
      }
    };
  }

  // CORE-04 + §15 + §23（存量 freeze，迁移后收缩基线）
  @ArchTest
  static final ArchRule adapter_no_application_service =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnGodObjects());

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository());

  @ArchTest
  static final ArchRule adapter_no_domain_service =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnDomainService());

  // v4.5：命名 / 事务四条规则已降级为 warn（tasks 2.5），不再作为 @ArchTest 硬门禁；
  // 聚合纯单测卫生检查使用 TEST-HYGIENE-01（AggregatePureUnitTestCoverageTest）。

  // P-2.3 + P-2.4（D9）：跨上下文 domain 越界守护；空匹配视为配置错误
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.iam");

  // 《BONE 总体架构设计方案》§4.1.1：模块层级依赖方向（ARCH-LEVEL-01/02）
  @ArchTest
  static final ArchRule engine_no_platform =
      BoneDddArchRules.engineModulesMustNotDependOnPlatform();

  @ArchTest
  static final ArchRule platform_no_engine_apps =
      BoneDddArchRules.platformMustNotDependOnEngineApps();
}
