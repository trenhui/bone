package com.bone.blueprint;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * bone-blueprint 架构守护（参考样板）。
 *
 * <p>真源：{@code doc/architecture/Bone-DDD-最终实践方案.md} CORE-01–CORE-12（含 E-3 应用用例）+ G-1（ArchUnit 规则集）。
 * 共享规则在 {@link BoneDddArchRules}，所有应用模块复用同一份。
 *
 * <p>首次集成 / 收缩基线见 {@code bone-framework/bone-architecture-test/README.md}。
 */
@AnalyzeClasses(
    packages = "com.bone.blueprint",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  /** 本模块域仓储包：全租户入口的三条共享规则都以此包为作用域。 */
  private static final String DOMAIN_REPOSITORY_PACKAGE = "com.bone.blueprint.domain.repository";

  // P0-1：依赖方向
  @ArchTest
  static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // §3.1：聚合身份/租户归属不可被外层篡改。
  // 框架侧 AggregateRoot.setId / TenantAggregateRoot.setTenantId 因 SDK 反射回填与 Tenantable 契约
  // 必须保持 public，故改为约束调用方：只有 domain 内部（工厂方法/聚合行为）可设置，杜绝越权改租户。
  @ArchTest
  static final ArchRule no_outer_aggregate_identity_mutation =
      BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

  // P0-5：domain 不可使用 QueryBuilder（豁免 ..domain.repository.. ——仓储是 SDK 框架集成点）。
  // 2026-09-20：本地重写的版本收敛回共享规则——blueprint / iam / system 三处各自复制同一豁免，
  // 而 integration / masterdata 的域仓储读到同一形态时又会重新踩一遍（E-4.1 已把该豁免写成平台口径）。
  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  // P0-6：CommandHandler 禁用 QueryBuilder
  @ArchTest
  static final ArchRule command_no_query_builder =
      BoneDddArchRules.commandHandlersMustNotUseQueryBuilder().allowEmptyShould(true);

  // E-4.2（v4.6 主判据）：读侧 DSL（Criteria / @ReadSideOnly）不得出现在 application 层。
  // 规则只约束 ..application..；domain.repository 是 SDK 框架集成点（见 domain_no_query_builder 的豁免）。
  // 本模块的读侧 DSL 现落在 domain/repository 与 infrastructure 适配器内——infrastructure/query 已随 ADR-0030 P4
  // 折叠删除。
  // 参考样板不 freeze，须 0 违规。
  @ArchTest
  static final ArchRule read_side_dsl_only_in_query_layer =
      BoneDddArchRules.readSideDslOnlyInQueryLayer();

  // P0-4 + §18.2：仓储方法白名单
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  // P0-7 + §14.3：禁止 UseCase（三件套）
  @ArchTest
  static final ArchRule no_new_use_cases =
      FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

  @ArchTest
  static final ArchRule no_usecase_package =
      FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

  @ArchTest
  static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

  // §14.5：禁止新增 domain.store 包
  @ArchTest
  static final ArchRule no_new_domain_store =
      FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

  // §16.3：禁止模块自建 BusinessException
  @ArchTest
  static final ArchRule no_custom_business_exception =
      FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

  @ArchTest
  static final ArchRule no_business_exception_suffix =
      FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

  // P0-7 + §15：adapter 入站约束（参考样板不 freeze，须 0 违规）
  @ArchTest
  static final ArchRule adapter_no_application_service =
      BoneDddArchRules.adaptersMustNotDependOnGodObjects();

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      BoneDddArchRules.adaptersMustNotDependOnDomainRepository();

  @ArchTest
  static final ArchRule adapter_no_domain_service =
      BoneDddArchRules.adaptersMustNotDependOnDomainService();

  // P0-1（本模块专用补门禁）：adapter 全包（不止 controller）不得依赖 domain.repository。
  //
  // 2026-09-19 收口：共享规则 adapterControllersMustNotDependOnDomainRepository 的谓词已从
  // ..adapter..controller.. 放宽为 ..adapter..（见
  // BoneDddArchRules#adaptersMustNotDependOnDomainRepository），
  // 与下面本条等价。保留本条的两个理由：① 共享规则在其它模块是 FreezingArchRule 冻结态（冻结=允许存量违规），
  // 而参考样板要求 0 违规，本条不冻结，是模块内可读的自证；② 它把「授权边界只开在 adapter.schedule」
  // 这条 ADR-0030 结论就地写在模块里，避免读者跨模块追溯。授权边界只开在 adapter.schedule：ADR-0030 §2 把
  // 「全租户运维扫描」的调用方明确写为定时 Job（现为 CancelExpiredOrderJob / CloseExpiredPaymentJob /
  // OrderPaymentInconsistencyJob 三个）。
  // 用「包」而不是「类名豁免名单」表达边界——名单会随 Job 增加而变长、读起来像历史遗留，且它豁免的是
  // 「依赖」：被列入名单的类仍可自由调用 save/update。写能力的收紧由下一条规则承担。
  @ArchTest
  static final ArchRule adapter_no_domain_repository_all_packages =
      noClasses()
          .that()
          .resideInAPackage("..adapter..")
          .and()
          .resideOutsideOfPackage("..adapter.schedule..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain.repository..")
          .allowEmptyShould(true)
          .because(
              "入站适配器（web/rpc/messaging）只许依赖 application；"
                  + "adapter.schedule 是 ADR-0030 授权的平台运维入口，其可用面另由 "
                  + "schedule_only_calls_all_tenants_repository_methods 收紧");

  // P0-1 延伸（2026-09-19 补、2026-09-20 收敛为共享规则）：schedule 拿到域仓储后，也只许用它做全租户运维扫描。
  // 域仓储在 ADR-0030 合并读写后自带 save/update/delete，「拿到接口就等于同时握有写能力」，而上一条按
  // 「依赖」设限，对已授权的 schedule 包没有约束力，所以调用面必须按方法逐个收紧。
  // 判据是**事实**而不是名字：@TenantScope(ALL) 或方法体内 Criteria.disableTenantFilter()——Criteria 通道的
  // TenantFilterInjector 不读注解，只按注解或只按后缀判定都会漏（双通道说明见 BoneDddArchRules）。
  // 命名要求（*AllTenants）由 all_tenant_entry_points_must_be_named_all_tenants 双向绑定，不再各自为政。
  @ArchTest
  static final ArchRule schedule_only_calls_all_tenants_repository_methods =
      BoneDddArchRules.scheduleOnlyCallsAllTenantScanMethods(DOMAIN_REPOSITORY_PACKAGE);

  // E-1.3：跨上下文 domain 越界守护；空匹配视为配置错误
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.blueprint");

  // E-1.3（v4.6）：全模块禁止依赖其它 Bone 上下文的 domain 模型——
  // 覆盖读侧 QueryHandler 直用 QueryBuilder.from(其它上下文实体) 的穿透路径。
  // 参考样板不 freeze，须 0 违规；空匹配视为配置错误。
  @ArchTest
  static final ArchRule no_cross_context_model =
      BoneDddArchRules.noCrossContextModelDependency("com.bone.blueprint");

  // E-6.4 / CORE-03（v4.6 内容禁令）：application/service 只许用例级编排，
  // 不得 new 领域对象、不得调聚合 setter 改状态（须经工厂方法/仓储与领域行为方法）。
  @ArchTest
  static final ArchRule application_services_no_domain_rules =
      BoneDddArchRules.applicationServicesMustNotOwnDomainRules();

  // CORE-06 / E-5.1（v4.6 + v4.7 修正）：一事务一聚合；扫描范围含 application/service 与 orchestration。
  // 参考样板不 freeze，须 0 违规。
  @ArchTest
  static final ArchRule one_aggregate_per_transaction =
      BoneDddArchRules.oneAggregatePerTransaction();

  // E-2（v4.7 补门禁）：租户取值收敛到 TenantPort 端口，业务层禁止直调 TenantContext。
  // 参考样板不 freeze，须 0 违规。
  @ArchTest
  static final ArchRule tenant_context_via_provider =
      BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly();

  // E-13.3（v5.7 补门禁）：infrastructure 层实现 application/port/out 接口的类，
  // 命名必须是 <Port短名>PortAdapter（如 PricingPort → PricingPortAdapter）。
  // 禁止用 *Impl / *Sender / *Writer / *Provider 等暗示内部实现而非端口适配的后缀。
  @ArchTest
  static final ArchRule port_implementations_must_end_with_port_adapter =
      classes()
          .that()
          .resideInAPackage("..infrastructure..")
          .and()
          .implement(applicationPortInterface())
          .should()
          .haveSimpleNameEndingWith("PortAdapter")
          .allowEmptyShould(true)
          .because(
              "E-13.3: application Port implementations in infrastructure must be named "
                  + "*PortAdapter (not *Impl / *Sender / *Writer / *Provider)");

  private static com.tngtech.archunit.base.DescribedPredicate<
          com.tngtech.archunit.core.domain.JavaClass>
      applicationPortInterface() {
    return new com.tngtech.archunit.base.DescribedPredicate<>(
        "implements an application.port.out.*Port interface") {
      @Override
      public boolean test(com.tngtech.archunit.core.domain.JavaClass input) {
        for (com.tngtech.archunit.core.domain.JavaType t : input.getInterfaces()) {
          if (!(t instanceof com.tngtech.archunit.core.domain.JavaClass)) continue;
          com.tngtech.archunit.core.domain.JavaClass iface =
              (com.tngtech.archunit.core.domain.JavaClass) t;
          if (iface.getName().startsWith("com.bone.blueprint.application.port.out.")
              && iface.getSimpleName().endsWith("Port")) {
            return true;
          }
        }
        return false;
      }
    };
  }

  // E-4.3 / E-10.2（#5 回归门禁）：domain/gateway 只允许合法 Domain Gateway（业务语言外部能力，
  // 如账户余额/库存这类业务规则依赖的外部事实）。技术端口（MQ 投递、Outbox 写、Outbox 中继、租户上下文、
  // 缓存、时钟、通知、文件、幂等等）一律不得进入，必须落 application/port/out。
  // 白名单即当前合规的全部 Domain Gateway；新增业务网关须同步更新此白名单，否则 CI 拦截。
  @ArchTest
  static final ArchRule domain_gateway_only_business_gateways =
      classes()
          .that()
          .resideInAPackage("..domain.gateway..")
          .should()
          .haveSimpleName("PaymentGateway")
          .orShould()
          .haveSimpleName("InventoryGateway")
          .orShould()
          .haveSimpleName("package-info");

  // E-5.4（v5.5 补门禁）：application 层 Repository.save() 必须配 publishFrom() 或声明 @NoDomainEvent。
  // 参考样板不 freeze，须 0 违规；豁免类须确保聚合方法 JavaDoc 说明"不发 DomainEvent"的理由。
  @ArchTest
  static final ArchRule save_must_pair_with_publish_or_exempt =
      BoneDddArchRules.applicationSaveMustPairWithPublishOrExempt();

  // P2-4（E-2 补充门禁，2026-09-20 收敛为共享规则）：全租户扫描仅限 schedule 包调用。
  // 定时任务无请求上下文，TenantPort 降级为平台租户 0，故全租户方法是平台运维入口——
  // 禁止 web/handler/infrastructure 等其它调用方（会导致跨租户数据泄漏或静默"扫描完成"但一笔没处理）。
  // 租户上下文直取同一约束见 a4caaed6（TenantContext 只许经租户端口读）。
  @ArchTest
  static final ArchRule all_tenants_scan_only_by_schedule =
      BoneDddArchRules.allTenantScanMethodsOnlyCalledBySchedule(DOMAIN_REPOSITORY_PACKAGE);

  // P2-5（E-4.4 补充门禁，2026-09-20 新）：全租户入口必须叫 *AllTenants，且 *AllTenants 方法必须真的绕过租户隔离。
  // 命名是给调用点看的（一眼知道会跨租户），注解 / disableTenantFilter 是给运行时用的；双向绑定防止
  // "只改名不改行为"或"只加注解不留痕迹"——那正是"危险的东西从签名里消失"的两种形态。
  @ArchTest
  static final ArchRule all_tenant_entry_points_must_be_named_all_tenants =
      BoneDddArchRules.allTenantEntryPointsMustBeNamedAllTenants(DOMAIN_REPOSITORY_PACKAGE);

  // ADR-0028（P6 · CQRS 双构件迁移债）：一个用例只选一种构件，禁止 CommandHandler 与 ApplicationService 套娃。
  // CommandHandler 不得依赖 *ApplicationService——若需编排应在 ApplicationService 内完成，而非 Handler 套
  // ApplicationService（双构件）。
  // 参考样板不 freeze，须 0 违规（当前 blueprint 无此类依赖）。
  @ArchTest
  static final ArchRule command_handlers_must_not_depend_on_application_service =
      noClasses()
          .that()
          .resideInAPackage("..application.command.handler..")
          .should()
          .dependOnClassesThat(commandHandlerDualArtifactPredicate())
          .allowEmptyShould(true)
          .because(
              "ADR-0028: one use case, one artifact — CommandHandler must not wrap an "
                  + "ApplicationService (no 套娃 / dual-artifact)");

  private static com.tngtech.archunit.base.DescribedPredicate<
          com.tngtech.archunit.core.domain.JavaClass>
      commandHandlerDualArtifactPredicate() {
    return new com.tngtech.archunit.base.DescribedPredicate<>(
        "depend on an *ApplicationService in ..application..") {
      @Override
      public boolean test(com.tngtech.archunit.core.domain.JavaClass input) {
        return input.getPackageName().contains(".application.")
            && input.getSimpleName().endsWith("ApplicationService");
      }
    };
  }

  // E-13.0（v5.6 补门禁）：包路径表达协议边界后，同模块两个协议的 Controller/Assembler 可合法同名，
  // 但 Spring 默认按类短名注册 bean，两个 orderController 会启动期抛 ConflictingBeanDefinitionException。
  // 本规则按同口径推算有效 bean 名（注解显式 value 优先，否则类短名首字母小写），把冲突提前到构建期。
  // 参考样板不 freeze，须 0 违规——冲突用显式 bean 名 / MapStruct implementationName 消解。
  @ArchTest
  static final ArchRule spring_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  // v4.5：命名 / 事务四条规则已降级为 warn（tasks 2.5），不再作为 @ArchTest 硬门禁；
  // 反贫血主判据切换为 R8 聚合纯单测（AggregatePureUnitTestCoverageTest）。
}
