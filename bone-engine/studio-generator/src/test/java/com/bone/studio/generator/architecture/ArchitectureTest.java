package com.bone.studio.generator.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * studio-generator 架构守护 — 统一来自 {@link BoneDddArchRules}（《Bone-DDD》§21 附录 B.3）。
 *
 * <p>首次集成或基线收缩命令见 {@code bone-framework/bone-architecture-test/README.md}。
 */
@AnalyzeClasses(
    packages = "com.bone.studio.generator",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  // P0-1
  @ArchTest
  static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // P0-5
  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  // P0-6
  @ArchTest
  static final ArchRule command_no_query_builder =
      BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

  // R2 + E-8（A 类强制，不 freeze）：禁外层篡改聚合 setId / setTenantId
  @ArchTest
  static final ArchRule aggregate_identity_immutable =
      BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

  // P0-4 + §18.2
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  // P0-7 + §14.3
  @ArchTest
  static final ArchRule no_new_use_cases =
      FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

  @ArchTest
  static final ArchRule no_usecase_package =
      FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

  @ArchTest
  static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

  // §14.5
  @ArchTest
  static final ArchRule no_new_domain_store =
      FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

  // §16.3
  @ArchTest
  static final ArchRule no_custom_business_exception =
      FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

  @ArchTest
  static final ArchRule no_business_exception_suffix =
      FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

  // P0-7 + §15 + §23（存量 freeze，迁移后收缩基线）
  @ArchTest
  static final ArchRule adapter_no_application_service =
      FreezingArchRule.freeze(
          BoneDddArchRules.adapterControllersMustNotDependOnApplicationService());

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository());

  @ArchTest
  static final ArchRule adapter_no_domain_service =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnDomainService());

  // v4.5：命名 / 事务四条规则已降级为 warn（tasks 2.5），不再作为 @ArchTest 硬门禁；
  // 反贫血主判据切换为 R8 聚合纯单测（AggregatePureUnitTestCoverageTest）。

  @ArchTest
  static final ArchRule extra_0 =
      FreezingArchRule.freeze(BoneDddArchRules.noStudioGeneratorUseCaseAnnotation());

  // P-2.3 + P-10.4（D9）：跨上下文 domain 越界守护；空匹配视为配置错误
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.studio.generator");
}
