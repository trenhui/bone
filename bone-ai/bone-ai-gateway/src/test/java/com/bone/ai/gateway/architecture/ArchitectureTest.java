package com.bone.ai.gateway.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/** bone-ai-gateway 架构守护（能力库模块，无聚合 / 持久化，DDD 分层规则无匹配类时统一空过）。 */
@AnalyzeClasses(
    packages = "com.bone.ai.gateway",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_independent =
      BoneDddArchRules.domainMustNotDependOnOuterLayers().allowEmptyShould(true);

  @ArchTest
  static final ArchRule application_not_depend_infrastructure =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure().allowEmptyShould(true);

  @ArchTest
  static final ArchRule app_services_not_own_domain_rules =
      BoneDddArchRules.applicationServicesMustNotOwnDomainRules().allowEmptyShould(true);

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

  @ArchTest
  static final ArchRule tenant_context_not_read_directly =
      BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly().allowEmptyShould(true);

  @ArchTest
  static final ArchRule adapters_not_depend_god_objects =
      BoneDddArchRules.adaptersMustNotDependOnGodObjects().allowEmptyShould(true);

  @ArchTest
  static final ArchRule spring_component_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  @ArchTest static final ArchRule no_aviator_dependency = BoneDddArchRules.noAviatorDependency();

  /**
   * 防「缝已设计、零消费方」反模式复发（本项目已四次踩坑：RerankPort / HttpBge*Client / LlmMetrics / ApiKeyProvider）。domain
   * 内接口（六边形端口，含 domain 根包与 domain.gateway 包）必须由模块内类实现——杜绝「设计已就位、代码零实现」（如 修复前的 ApiKeyProvider
   * 客户端构造期固化 key、零实现零消费）。
   *
   * <p>匹配口径订正（2026-10-24）：原规则按 *Port 后缀匹配，但网关缝接口的命名惯例是 *Provider / *Guard / *Metrics / *Cache /
   * *Router 等，本模块历史上从未存在过 *Port 接口——规则自引入（7b3f32ee）起匹配零类，ArchUnit 空集应失败默认语义使构建转红。现改为「domain
   * 内全部接口」，与上述注释意图（LlmMetrics / ApiKeyProvider 均非 *Port 后缀）对齐； 无匹配类时按本文件头约定空过（allowEmptyShould）。
   */
  @ArchTest
  static final ArchRule domain_ports_must_have_module_implementation =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("..gateway.domain..")
          .and()
          .areInterfaces()
          .should(
              new ArchCondition<>("have an implementation class within the module") {
                @Override
                public void check(JavaClass port, ConditionEvents events) {
                  boolean implemented =
                      port.getDirectDependenciesToSelf().stream()
                          .anyMatch(
                              d ->
                                  d.getOriginClass().isAssignableTo(port.getName())
                                      && d.getOriginClass()
                                          .getPackageName()
                                          .startsWith("com.bone.ai.gateway"));
                  events.add(
                      new SimpleConditionEvent(
                          port,
                          implemented,
                          port.getName() + " has no implementation class within module"));
                }
              })
          .allowEmptyShould(true);

  /**
   * 第二道闸：domain 接口（六边形端口）必须被 application / infrastructure / config 层实际依赖（DI 注入属依赖而非 access，故用
   * {@code getDirectDependenciesToSelf} 而非 {@code beAccessed}）。拦截「实现存在但从未接线」的死缝 （如修复前的
   * RerankPort：NoOp 实现就位、应用层不注入）。
   *
   * <p>匹配口径同上一道闸：网关缝接口无 {@code *Port} 后缀，故按 domain 内全部接口匹配；无匹配类时按本文件头约定空过。
   */
  @ArchTest
  static final ArchRule domain_ports_must_be_consumed =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("..gateway.domain..")
          .and()
          .areInterfaces()
          .should(
              new ArchCondition<>("be consumed by an application/infrastructure/config class") {
                @Override
                public void check(
                    com.tngtech.archunit.core.domain.JavaClass port, ConditionEvents events) {
                  boolean consumed =
                      port.getDirectDependenciesToSelf().stream()
                          .anyMatch(
                              d ->
                                  d.getOriginClass()
                                      .getPackageName()
                                      .matches(
                                          ".*\\.gateway\\.(application|infrastructure|config).*"));
                  events.add(
                      new SimpleConditionEvent(
                          port,
                          consumed,
                          port.getName()
                              + " is not depended on by any application/infrastructure/config class"));
                }
              })
          .allowEmptyShould(true);
}
