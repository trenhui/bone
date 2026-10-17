package com.bone.metadata.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;

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

  @ArchTest
  static final ArchRule spring_component_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  // P1-2（2026-10-13）：运行时端点授权口径门禁——@PreAuthorize 必须是
  // "@runtimeAccessGuard.canRead/canWrite(#entityCode)" 动态判据（与 Guard 同源），
  // 不得回退粗粒度 hasAnyAuthority('metadata:runtime:*'/'metadata:read'/'metadata:write')：
  // 粗码放行会让「注解层已授权」的假象掩盖 Guard 的实体级判定，两套口径必漂移
  // （RuntimeAccessGuard javadoc §通配论述）。豁免清单仅 RuntimeActionController.invoke——
  // 动作级判据 canInvoke(def) 无单一布尔简化形式，注解预判会误伤仅持细码的独立资金授权面，
  // 判定全部下沉 RuntimeActionApplicationService（该方法 javadoc 有完整论证）。
  private static final Set<String> RUNTIME_PREAUTHORIZE_EXEMPT =
      Set.of("RuntimeActionController.invoke");

  @ArchTest
  static final ArchRule runtime_endpoint_preauthorize_must_bind_guard =
      ArchRuleDefinition.methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("com.bone.metadata.runtime.adapter.web..")
          .and()
          .arePublic() // 端点均为 public；私有辅助（currentOperator/requireEntity/lambda）不挂授权注解，不属口径范围
          .should(
              new ArchCondition<JavaMethod>(
                  "annotate @runtimeAccessGuard.canRead/canWrite(#entityCode)") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                  String qualified = method.getOwner().getSimpleName() + "." + method.getName();
                  try {
                    PreAuthorize annotation = method.getAnnotationOfType(PreAuthorize.class);
                    if (annotation == null) {
                      events.add(
                          new SimpleConditionEvent(
                              method, false, qualified + " 缺少 @PreAuthorize（运行时端点必须显式授权）"));
                      return;
                    }
                    String value = annotation.value();
                    boolean satisfied =
                        RUNTIME_PREAUTHORIZE_EXEMPT.contains(qualified)
                            || value.matches(
                                "@runtimeAccessGuard\\.can(Read|Write)\\(#entityCode\\)");
                    events.add(
                        new SimpleConditionEvent(method, satisfied, qualified + ": " + value));
                  } catch (IllegalArgumentException ex) {
                    // 注解类型不在 classpath 时 ArchUnit 抛此异常——本模块 PreAuthorize 恒在，视为失败而非跳过
                    events.add(
                        new SimpleConditionEvent(
                            method, false, qualified + " 注解解析失败: " + ex.getMessage()));
                  }
                }
              })
          .because("粗粒度注解放行与 RuntimeAccessGuard 实体级判定是两套口径，必漂移");

  // P2-1（2026-10-13）：adapter 不得直注 engine 运行时持久化服务——
  // JdbcRuntimeRecordService（记录 CRUD + 行 ACL + 审计 + 乐观锁横切）/ RuntimeRelationSupport（关系 JDBC）
  // 是引擎侧持久化组件，adapter 直注即绕过 ADR-0028「Application Service First」。
  // 原 RuntimeRecordController javadoc 宣称「命中 god-object 谓词属已冻结例外」与事实不符：
  // archunit_store 中该规则冻结文件为空（从未冻结任何违规），谓词只查 ..application.. 包，
  // Jdbc 服务因位于 engine.runtime 包而根本不在谓词范围内——门禁不是「例外登记」而是「视而不见」。
  // 收敛后横切判定（currentOperator/rowOwnerId/状态机守卫/ID 生成）唯一真源在
  // RuntimeRecordApplicationService（与 RuntimeActionApplicationService 同构）。
  // 谓词边界：RuntimeEntityCatalog（实体目录查询接口）不中招——它是无副作用查询端口，
  // 非持久化编排服务；未来若其长出写能力，应一并收敛并收紧本规则。
  @ArchTest
  static final ArchRule adapter_no_engine_runtime_persistence =
      BoneDddArchRules.adapterMustNotDependOnEngineRuntimePersistence();
}
