package com.bone.integration.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * bone-integration 架构守护 — 统一来自 {@link BoneDddArchRules}（《Bone-DDD》§21 附录 B.3）。
 *
 * <p>首次集成或基线收缩命令见 {@code bone-framework/bone-architecture-test/README.md}。
 */
@AnalyzeClasses(
    packages = "com.bone.integration",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  /** 本模块域仓储包：全租户入口门禁（事实判据 + 命名绑定）的作用域。 */
  private static final String DOMAIN_REPOSITORY_PACKAGE = "com.bone.integration.domain.repository";

  // E-2 / E-4.4 补充门禁（2026-09-20，共享规则）：全租户入口只许**已登记调用方**调用，schedule 也只许调全租户入口；
  // 全租户入口必须叫 *AllTenants。判据是事实（@TenantScope(ALL) 或 Criteria.disableTenantFilter()），不是名字。
  //
  // 已登记调用方：
  //  ① ..adapter.schedule..：平台运维统计（FlowStatisticsJob，定时线程无租户上下文）；
  //  ② FlowMonitorSupport：上面那条任务依赖的统计服务——它的 *AllTenants 变体只服务平台汇总，
  //     租户可见路径（MonitorController 的监控页）仍走租户内口径，两组方法刻意分开命名。
  @ArchTest
  static final ArchRule all_tenants_scan_only_by_registered_callers =
      BoneDddArchRules.allTenantEntryPointsOnlyCalledBy(
          DOMAIN_REPOSITORY_PACKAGE,
          BoneDddArchRules.AllTenantCallers.ofPackages("..adapter.schedule..")
              .andClasses("com.bone.integration.application.support.FlowMonitorSupport"));

  @ArchTest
  static final ArchRule schedule_only_calls_all_tenants_repository_methods =
      BoneDddArchRules.scheduleOnlyCallsAllTenantScanMethods(DOMAIN_REPOSITORY_PACKAGE);

  // 模块级租户隔离修复（G-2 / P0*）：
  // 截至 2026-09-22，5 个聚合（IntegrationFlow / Connector / FlowNode / FlowConnection / IntegrationLog）
  // 已改 extends TenantAggregateRoot<Long>，SDK 按实体 tenantId 字段自动注入租户过滤（读隔离已修复）。
  // 写时由 TenantFilterInjector.resolveInsertTenantValue 取 TenantContext 当前租户填值。
  // ⚠️ 上线前置：int_* 表历史 tenant_id=0 行必须先回填真实租户（见
  //    doc/_generated/integration/integration_tenant_backfill.sql，由 DBA/业务确认归属规则后执行），
  //    否则 SDK 自动过滤会把 tenant_id=0 历史行过滤为不可见。回填完成前禁止上线本改动。
  // 登记与拆除条件见 bone-platform/bone-integration/README.md「已登记的租户隔离缺口」。
  // 本规则原先报出的"schedule 调租户内读"已通过显式 *AllTenants 入口修复（见 FlowStatisticsJob 与
  // FlowMonitorSupport 的统计口径），故不再冻结。

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
      BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

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

  // v4.5：命名 / 事务四条规则已降级为 warn（tasks 2.5），不再作为 @ArchTest 硬门禁；
  // 聚合纯单测卫生检查使用 TEST-HYGIENE-01（AggregatePureUnitTestCoverageTest）。

  // P-2.3 + P-2.4（D9）：跨上下文 domain 越界守护；空匹配视为配置错误
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.integration");

  /** R1：Outbox 中继 Job 只依赖 domain 端口，禁止直注 infrastructure 实现。 */
  @ArchTest
  static final ArchRule outbox_relay_job_must_use_domain_port =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .haveSimpleName("IntegrationOutboxRelayJob")
              .should()
              .dependOnClassesThat()
              .haveSimpleName("IntegrationOutboxRelay")
              .because("CORE-02：adapter/schedule/*Job 须经端口，禁止直注 infrastructure 实现"));

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
