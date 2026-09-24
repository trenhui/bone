package com.bone.metadata.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * bone-metadata-server 架构守护 — 统一来自 {@link BoneDddArchRules}（《Bone-DDD》§21 附录 B.3）。
 *
 * <p>首次集成或基线收缩命令见 {@code bone-framework/bone-architecture-test/README.md}。
 */
@AnalyzeClasses(
    packages = "com.bone.metadata",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  /** 本模块域仓储包：全租户入口门禁（事实判据 + 命名绑定）的作用域。 */
  private static final String DOMAIN_REPOSITORY_PACKAGE =
      "com.bone.metadata.catalog.domain.repository";

  // E-2 / E-4.4 补充门禁（2026-09-20，共享规则）：全租户扫描只许 adapter.schedule 调用，schedule 也只许调全租户入口；
  // 全租户入口必须叫 *AllTenants。判据是事实（@TenantScope(ALL) 或 Criteria.disableTenantFilter()），不是名字。
  @ArchTest
  static final ArchRule all_tenants_scan_only_by_schedule =
      BoneDddArchRules.allTenantScanMethodsOnlyCalledBySchedule(DOMAIN_REPOSITORY_PACKAGE);

  @ArchTest
  static final ArchRule schedule_only_calls_all_tenants_repository_methods =
      BoneDddArchRules.scheduleOnlyCallsAllTenantScanMethods(DOMAIN_REPOSITORY_PACKAGE);

  @ArchTest
  static final ArchRule all_tenant_entry_points_must_be_named_all_tenants =
      BoneDddArchRules.allTenantEntryPointsMustBeNamedAllTenants(DOMAIN_REPOSITORY_PACKAGE);

  // CORE-02：依赖向内
  @ArchTest
  static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // CORE-05：domain 禁止读侧 DSL
  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  // CORE-05：写用例禁止读侧 DSL
  @ArchTest
  static final ArchRule command_no_query_builder =
      FreezingArchRule.freeze(BoneDddArchRules.commandHandlersMustNotUseQueryBuilder());

  // CORE-05（E-4.2 主判据，蓝图范式）：读侧 DSL（Criteria / FluentQuery）只许出现在 domain.repository
  // 或 infrastructure 的 SDK 集成点；application 层收敛后仅经仓储读模型方法取数，不得持有读侧 DSL。
  @ArchTest
  static final ArchRule read_side_dsl_only_in_query_layer =
      BoneDddArchRules.readSideDslOnlyInQueryLayer();

  // CORE-02 + E-6（A 类强制，不 freeze）：禁外层篡改聚合 setId / setTenantId
  @ArchTest
  static final ArchRule aggregate_identity_immutable =
      BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

  // CORE-05 + §18.2
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  // P0-1（R9 / CORE）：一事务一聚合，跨聚合变更须经领域事件 / Outbox / 编排器
  @ArchTest
  static final ArchRule one_aggregate_per_transaction =
      BoneDddArchRules.oneAggregatePerTransaction();

  // P0-2（E-6.4 / R2 反贫血）：application 层不得直接实例化或改领域对象状态
  @ArchTest
  static final ArchRule application_services_no_domain_rules =
      BoneDddArchRules.applicationServicesMustNotOwnDomainRules();

  // P0-3（E-5.4）：application 层 Repository.save() 必须配 publishFrom() 或声明 @NoDomainEvent
  @ArchTest
  static final ArchRule application_save_must_pair_with_publish_or_exempt =
      BoneDddArchRules.applicationSaveMustPairWithPublishOrExempt();

  // CORE-04 + §14.3
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

  // CORE-04（蓝图 module-level）：入站适配器（任意 ..adapter.. 包）只许依赖 application，不得直连 domain.repository。
  // catalog 收敛为 *ApplicationService 门面后，控制器仅注入门面，本规则稳定为零违规。
  @ArchTest
  static final ArchRule adapter_no_domain_repository_all_packages =
      noClasses()
          .that()
          .resideInAPackage("..adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain.repository..")
          .allowEmptyShould(true)
          .because("入站适配器只许依赖 application；catalog 收敛为 *ApplicationService 后控制器不再直连仓储");

  // ADR-0028：一个用例只选一种构件，CommandHandler 禁止依赖 application 层（套娃 ApplicationService）。
  // 收敛后 handler 包已清空，allowEmptyShould 保持规则在基线收缩期间不误判。
  @ArchTest
  static final ArchRule command_handlers_must_not_depend_on_application_service =
      noClasses()
          .that()
          .resideInAPackage("..application.command.handler..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..application..")
          .allowEmptyShould(true)
          .because("ADR-0028: one use case, one artifact — CommandHandler 禁止依赖 application 层");

  // v4.5：命名 / 事务四条规则已降级为 warn（tasks 2.5），不再作为 @ArchTest 硬门禁；
  // 聚合纯单测卫生检查使用 TEST-HYGIENE-01（AggregatePureUnitTestCoverageTest）。

  // P-2.3 + P-2.4（D9）：跨上下文 domain 越界守护；空匹配视为配置错误
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.metadata.catalog");

  // 《BONE 总体架构设计方案》§4.1.1：模块层级依赖方向（ARCH-LEVEL-01/02）
  @ArchTest
  static final ArchRule engine_no_platform =
      BoneDddArchRules.engineModulesMustNotDependOnPlatform();

  @ArchTest
  static final ArchRule platform_no_engine_apps =
      BoneDddArchRules.platformMustNotDependOnEngineApps();
}
