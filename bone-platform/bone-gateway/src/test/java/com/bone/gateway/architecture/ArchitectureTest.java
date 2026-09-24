package com.bone.gateway.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * bone-gateway 架构守护。
 *
 * <p>网关为基础设施组件（WebFlux），不含 domain/application/adapter 分层，故复用 {@link BoneDddArchRules}
 * 时相关分层规则无匹配类；统一加 {@code allowEmptyShould(true)} 避免「空 should 失败」误报。
 */
@AnalyzeClasses(packages = "com.bone.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_independent =
      BoneDddArchRules.domainMustNotDependOnOuterLayers().allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_custom_business_exception = BoneDddArchRules.noCustomBusinessException();

  @ArchTest
  static final ArchRule no_business_exception_suffix = BoneDddArchRules.noBusinessExceptionSuffix();

  @ArchTest
  static final ArchRule no_bone_core_usecase =
      BoneDddArchRules.noBoneCoreUseCaseApiDependency().allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_usecase_package =
      BoneDddArchRules.noApplicationUseCasePackage().allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_new_domain_store =
      BoneDddArchRules.noNewDomainStorePackage().allowEmptyShould(true);

  // P0-4（2026-09-23 接入）：网关是纯 WebFlux 基础设施层（filter / route / security），没有 ..adapter.. 子包，
  // 故共享规则 adaptersMustNotDependOnDomain* 的 ..adapter.. 谓词在此为 0 命中（vacuous pass）——接字面规则等于没接。
  // 按网关自身包作用域写守卫，真正拦截「gateway 触达任何上下文的 domain.repository / domain.service」的回归。
  @ArchTest
  static final ArchRule gateway_no_domain_repository =
      noClasses()
          .that()
          .resideInAPackage("com.bone.gateway..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain.repository..")
          .allowEmptyShould(true)
          .because(
              "P0-4: gateway (WebFlux infra) must not reach into any context's domain repositories");

  @ArchTest
  static final ArchRule gateway_no_domain_service =
      noClasses()
          .that()
          .resideInAPackage("com.bone.gateway..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain.service..")
          .allowEmptyShould(true)
          .because("P0-4: gateway must not depend on any context's domain services");

  @ArchTest
  static final ArchRule spring_component_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  // ARCH-LEVEL-01：引擎模块不得依赖平台（层级方向 Platform -> Engine -> Framework -> Kernel）
  @ArchTest
  static final ArchRule engine_must_not_depend_on_platform =
      BoneDddArchRules.engineModulesMustNotDependOnPlatform();

  // ARCH-LEVEL-02：平台不得依赖引擎可部署应用壳，仅允许依赖 SDK 底座（metadata-sdk / extension-sdk）
  @ArchTest
  static final ArchRule platform_must_not_depend_on_engine_apps =
      BoneDddArchRules.platformMustNotDependOnEngineApps();
}
