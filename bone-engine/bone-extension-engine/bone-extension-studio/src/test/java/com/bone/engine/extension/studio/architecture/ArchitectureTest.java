package com.bone.engine.extension.studio.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(
    packages = "com.bone.engine.extension.studio",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /** 本模块域仓储包：全租户入口门禁（事实判据 + 命名绑定）的作用域。 */
  private static final String DOMAIN_REPOSITORY_PACKAGE =
      "com.bone.engine.extension.studio.domain.repository";

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

  @ArchTest
  static final ArchRule no_new_use_cases =
      FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

  @ArchTest
  static final ArchRule no_usecase_package =
      FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

  @ArchTest
  static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

  @ArchTest
  static final ArchRule no_new_domain_store =
      FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

  @ArchTest
  static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  @ArchTest
  static final ArchRule command_no_query_builder =
      BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

  // R2 + E-8（A 类强制，不 freeze）：禁外层篡改聚合 setId / setTenantId
  @ArchTest
  static final ArchRule aggregate_identity_immutable =
      BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  // P0-1（R9）：一事务一聚合——存量跨聚合写事务（Extension + PluginVersion 同事务）冻结为已知债，禁止新增
  @ArchTest
  static final ArchRule one_aggregate_per_transaction =
      FreezingArchRule.freeze(BoneDddArchRules.oneAggregatePerTransaction());

  // P0-2（E-6.4 / R2 反贫血）：application 层不直接改领域对象状态——存量贫血写法冻结为已知债，禁止新增
  @ArchTest
  static final ArchRule application_services_no_domain_rules =
      FreezingArchRule.freeze(BoneDddArchRules.applicationServicesMustNotOwnDomainRules());

  // P0-3（E-5.4）：application 层 Repository.save() 必须配 publishFrom() 或声明 @NoDomainEvent
  // （本模块 4 个含 save 的应用服务已声明 @NoDomainEvent，见各自类级豁免）
  @ArchTest
  static final ArchRule application_save_must_pair_with_publish_or_exempt =
      BoneDddArchRules.applicationSaveMustPairWithPublishOrExempt();

  @ArchTest
  static final ArchRule no_custom_business_exception =
      FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

  @ArchTest
  static final ArchRule no_business_exception_suffix =
      FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

  // P0-7 + §15 + §23（存量 freeze，迁移后收缩基线）
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
  // 反贫血主判据切换为 R8 聚合纯单测（AggregatePureUnitTestCoverageTest）。

  // E-2（v4.7 补门禁）：租户取值收敛到 TenantProvider 端口，业务层（application/domain/adapter）禁止直调 TenantContext。
  // 存量直调先冻结为已知债，禁止新增（与 iam/masterdata 同口径）。
  @ArchTest
  static final ArchRule tenant_context_via_provider =
      FreezingArchRule.freeze(BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly());

  // E-4.2（v4.6 主判据）：读侧 DSL（Criteria / @ReadSideOnly）不得出现在 application 层；DSL 须落在
  // infrastructure/query 或 domain.repository（SDK 框架集成点）。参考样板不 freeze，须 0 违规。
  @ArchTest
  static final ArchRule read_side_dsl_only_in_query_layer =
      BoneDddArchRules.readSideDslOnlyInQueryLayer();

  // E-13.0（v5.6 补门禁）：同模块内 Spring 组件 bean 名必须唯一，提前到构建期捕获启动期
  // ConflictingBeanDefinitionException（G-1.5 已确认全仓零冲突）。
  @ArchTest
  static final ArchRule spring_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  // P-2.3 + P-2.4（D9）：跨上下文 domain 越界守护；空匹配视为配置错误
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.engine.extension.studio");
}
