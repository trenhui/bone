package com.bone.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.architecture.fixture.adapter.controller.ControllerUsingDomainService;
import com.bone.architecture.fixture.adapter.controller.ControllerUsingRepository;
import com.bone.architecture.fixture.adapter.controller.SpoofingController;
import com.bone.architecture.fixture.adapter.messaging.MessagingListenerUsingDomainService;
import com.bone.architecture.fixture.adapter.messaging.MessagingListenerUsingRepository;
import com.bone.architecture.fixture.adapter.schedule.ScheduleJobUsingRepository;
import com.bone.architecture.fixture.application.command.handler.OrderCommandProcessor;
import com.bone.architecture.fixture.application.command.handler.PaymentCommandHandler;
import com.bone.architecture.fixture.application.command.handler.SubmitOrderCommandHandler;
import com.bone.architecture.fixture.application.query.handler.LegalQueryHandler;
import com.bone.architecture.fixture.application.query.handler.OrderQueryFetcher;
import com.bone.architecture.fixture.application.service.LegalAppService;
import com.bone.architecture.fixture.application.service.TenantSpoofingService;
import com.bone.architecture.fixture.application.service.ViolatingAppService;
import com.bone.architecture.fixture.application.usecase.SubmitOrderUseCase;
import com.bone.architecture.fixture.context.mine.domain.order.SnapshotAggregate;
import com.bone.architecture.fixture.domain.order.DomainUsingReadDsl;
import com.bone.architecture.fixture.domain.order.SampleViolations;
import com.bone.architecture.fixture.domain.order.SpoofedAggregate;
import com.bone.architecture.fixture.infrastructure.SpoofedAggregateConverter;
import com.bone.architecture.fixture.infrastructure.query.ReadDslGateway;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/**
 * 共享规则库（BoneDddArchRules）正反例验证——每条规则的判定语义以最小夹具固化，规则行为变更即测试失败。
 *
 * <p>测试置于 bone-core（与既有 {@code AdapterControllerDependencyRuleTest} 等同位）：bone-architecture-test 被
 * bone-core 以 test 作用域依赖，反向依赖会构成 Maven reactor 环。夹具位于 {@code
 * com.bone.architecture.fixture}，仅供规则所引用，非测试代码本身。
 */
class BoneDddArchRulesVerificationTest {

  private static final JavaClasses FIXTURES =
      new ClassFileImporter().importPackages("com.bone.architecture.fixture");

  private static EvaluationResult eval(ArchRule rule, Class<?>... classes) {
    return rule.evaluate(new ClassFileImporter().importClasses(classes));
  }

  private static String report(EvaluationResult result) {
    return result.getFailureReport().toString();
  }

  // ===== P0-1 分层 =====

  @Test
  void domainMustNotDependOnOuterLayers_detectsViolation() {
    EvaluationResult result =
        eval(BoneDddArchRules.domainMustNotDependOnOuterLayers(), SampleViolations.class);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("OuterLayerGateway"), report(result));
  }

  @Test
  void domainMustNotDependOnOuterLayers_allowsCleanDomain() {
    assertFalse(
        eval(BoneDddArchRules.domainMustNotDependOnOuterLayers(), SnapshotAggregate.class)
            .hasViolation());
  }

  @Test
  void applicationMustNotDependOnInfrastructure_detectsViolation() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.applicationMustNotDependOnInfrastructure(),
            PaymentCommandHandler.class);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("ReadDslGateway"), report(result));
  }

  @Test
  void applicationMustNotDependOnInfrastructure_allowsCleanApplication() {
    assertFalse(
        eval(BoneDddArchRules.applicationMustNotDependOnInfrastructure(), LegalAppService.class)
            .hasViolation());
  }

  // ===== P0-5 / P0-6 读写侧 =====

  @Test
  void domainMustNotUseQueryBuilder_detectsAnnotatedRuleTarget() {
    // ReadSideOnly 自身位于 com.bone.core.annotation（.core. 不含 .domain.）且依赖方为空，规则只在
    // 出现"domain 类依赖 @ReadSideOnly 类"时命中——夹具上下文里构造该场景
    EvaluationResult result =
        eval(
            BoneDddArchRules.domainMustNotUseQueryBuilder(),
            DomainUsingReadDsl.class,
            ReadDslGateway.class);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("DomainUsingReadDsl"), report(result));
  }

  @Test
  void commandHandlersMustNotUseQueryBuilder_detectsViolation() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.commandHandlersMustNotUseQueryBuilder(),
            PaymentCommandHandler.class,
            ReadDslGateway.class);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("PaymentCommandHandler"), report(result));
  }

  @Test
  void commandHandlersMustNotUseQueryBuilder_allowsCleanHandler() {
    assertFalse(
        eval(
                BoneDddArchRules.commandHandlersMustNotUseQueryBuilder(),
                SubmitOrderCommandHandler.class)
            .hasViolation());
  }

  // ===== §14.3 / §14.5 UseCase / Store / 异常 =====

  @Test
  void noUseCaseClassesInApplication_detectsViolation() {
    EvaluationResult result =
        eval(BoneDddArchRules.noUseCaseClassesInApplication(), SubmitOrderUseCase.class);
    assertTrue(result.hasViolation(), report(result));
  }

  @Test
  void noApplicationUseCasePackage_detectsViolation() {
    EvaluationResult result = BoneDddArchRules.noApplicationUseCasePackage().evaluate(FIXTURES);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("SubmitOrderUseCase"), report(result));
  }

  @Test
  void noBoneCoreUseCaseApiDependency_doesNotFlagLegacyApi() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.noBoneCoreUseCaseApiDependency(),
            UsecaseClient.class,
            com.bone.architecture.fixture.usecase.LegacyUseCaseApi.class);
    // com.bone.core.usecase 包已删除；依赖同名但不同的 com.bone.architecture.fixture.usecase 不应被误判
    assertFalse(result.hasViolation(), report(result));
  }

  @Test
  void noNewDomainStorePackage_detectsViolation() {
    EvaluationResult result = BoneDddArchRules.noNewDomainStorePackage().evaluate(FIXTURES);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("LegacyStore"), report(result));
  }

  @Test
  void noCustomBusinessException_detectsViolation() {
    EvaluationResult result = BoneDddArchRules.noCustomBusinessException().evaluate(FIXTURES);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("BusinessException"), report(result));
  }

  @Test
  void noBusinessExceptionSuffix_detectsViolation() {
    EvaluationResult result = BoneDddArchRules.noBusinessExceptionSuffix().evaluate(FIXTURES);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("PaymentBusinessException"), report(result));
  }

  // ===== §15 adapter 依赖（既有 AdapterControllerDependencyRuleTest 之外补充 Repo/Ds 两条） =====

  @Test
  void adapterControllersMustNotDependOnDomainRepository_detectsViolation() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository(),
            ControllerUsingRepository.class,
            com.bone.architecture.fixture.domain.repository.OrderAggregateRepository.class);
    assertTrue(result.hasViolation(), report(result));
  }

  @Test
  void adapterControllersMustNotDependOnDomainService_detectsViolation()
      throws ClassNotFoundException {
    EvaluationResult result =
        eval(
            BoneDddArchRules.adapterControllersMustNotDependOnDomainService(),
            ControllerUsingDomainService.class,
            Class.forName("com.bone.architecture.fixture.domain.service.TaxDomainService"));
    assertTrue(result.hasViolation(), report(result));
  }

  // ===== 2026-09-19 谓词放宽（..adapter..controller.. → ..adapter..）的负向探针与受控例外固化 =====

  @Test
  void adaptersMustNotDependOnDomainRepository_detectsMessagingAdapter() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.adaptersMustNotDependOnDomainRepository(),
            MessagingListenerUsingRepository.class,
            com.bone.architecture.fixture.domain.repository.OrderAggregateRepository.class);
    assertTrue(
        result.hasViolation(),
        "谓词放宽为 ..adapter.. 后，adapter.messaging 直注域仓储必须被拦下"
            + "（放宽前该类形态会整条逃逸、门禁永远绿）\n"
            + report(result));
  }

  @Test
  void adaptersMustNotDependOnDomainRepository_allowsAuthorizedScheduleJob() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.adaptersMustNotDependOnDomainRepository(),
            ScheduleJobUsingRepository.class,
            com.bone.architecture.fixture.domain.repository.OrderAggregateRepository.class);
    assertFalse(
        result.hasViolation(),
        "adapter.schedule 是 ADR-0030 代价 C3 授权的全租户运维扫描入口，必须放行——"
            + "它是受控例外而非疏漏，误改成「连 schedule 一起禁」时本用例必须失败\n"
            + report(result));
  }

  @Test
  void adaptersMustNotDependOnDomainService_detectsMessagingAdapter() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.adaptersMustNotDependOnDomainService(),
            MessagingListenerUsingDomainService.class,
            com.bone.architecture.fixture.domain.service.TaxDomainService.class);
    assertTrue(
        result.hasViolation(),
        "领域服务规则无 schedule 例外，且放宽后 adapter.messaging 直调领域服务必须被拦下\n" + report(result));
  }

  // ===== §23 命名与事务（v4.5 降级 warn 的两条命名 + 两条事务） =====

  @Test
  void commandHandlersShouldBeNamedCommandHandler_rejectsBadName() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.commandHandlersShouldBeNamedCommandHandler(),
            OrderCommandProcessor.class);
    assertTrue(result.hasViolation(), report(result));
  }

  @Test
  void commandHandlersShouldBeNamedCommandHandler_acceptsGoodName() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.commandHandlersShouldBeNamedCommandHandler(),
            SubmitOrderCommandHandler.class);
    assertFalse(result.hasViolation(), report(result));
  }

  @Test
  void queryHandlersShouldBeNamedQueryHandler_rejectsBadName() {
    EvaluationResult result =
        eval(BoneDddArchRules.queryHandlersShouldBeNamedQueryHandler(), OrderQueryFetcher.class);
    assertTrue(result.hasViolation(), report(result));
  }

  @Test
  void queryHandlersShouldBeNamedQueryHandler_acceptsGoodName() {
    EvaluationResult result =
        eval(BoneDddArchRules.queryHandlersShouldBeNamedQueryHandler(), LegalQueryHandler.class);
    assertFalse(result.hasViolation(), report(result));
  }

  @Test
  void commandHandlersShouldBeTransactional_rejectsMissingAnnotation() {
    EvaluationResult result =
        eval(BoneDddArchRules.commandHandlersShouldBeTransactional(), OrderCommandProcessor.class);
    assertTrue(result.hasViolation(), report(result));
  }

  @Test
  void commandHandlersShouldBeTransactional_acceptsClassLevelAnnotation() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.commandHandlersShouldBeTransactional(),
            SubmitOrderCommandHandler.class);
    assertFalse(result.hasViolation(), report(result));
  }

  @Test
  void queryHandlersShouldBeReadOnlyTransactional_acceptsReadOnly() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.queryHandlersShouldBeReadOnlyTransactional(), LegalQueryHandler.class);
    assertFalse(result.hasViolation(), report(result));
  }

  @Test
  void queryHandlersShouldBeReadOnlyTransactional_rejectsMissingAnnotation() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.queryHandlersShouldBeReadOnlyTransactional(), OrderQueryFetcher.class);
    assertTrue(result.hasViolation(), report(result));
  }

  // ===== §3.1 / E-6.4 / E-2 身份与内容禁令 =====

  @Test
  void outerLayersMustNotMutateAggregateIdentity_detectsControllerSetId() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity(),
            SpoofingController.class,
            SpoofedAggregate.class);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("setId"), report(result));
  }

  @Test
  void outerLayersMustNotMutateAggregateIdentity_exemptPersistenceAdapter() {
    // 持久化适配器（*Converter / *Mapper）设置身份是法定职责——规则内置豁免。
    // 单独传入 Converter 时 that() 子句无可匹配类，本测试显式允许空 should（与兄弟规则一致）。
    EvaluationResult result =
        BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity()
            .allowEmptyShould(true)
            .evaluate(new ClassFileImporter().importClasses(SpoofedAggregateConverter.class));
    assertFalse(result.hasViolation(), report(result));
  }

  @Test
  void applicationServicesMustNotOwnDomainRules_detectsNewAndSetter() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.applicationServicesMustNotOwnDomainRules(), ViolatingAppService.class);
    assertTrue(result.hasViolation(), report(result));
    String r = report(result);
    // 双违例：new 领域对象 + HashSetter
    assertTrue(r.contains("instantiates domain type"), r);
    assertTrue(r.contains("calls"), r);
  }

  @Test
  void applicationServicesMustNotOwnDomainRules_allowsRepositorySourced() {
    assertFalse(
        eval(BoneDddArchRules.applicationServicesMustNotOwnDomainRules(), LegalAppService.class)
            .hasViolation());
  }

  @Test
  void businessLayersMustNotReadTenantContextDirectly_detectsViolation() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly(),
            TenantSpoofingService.class);
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("TenantContext"), report(result));
  }

  @Test
  void readSideDslOnlyInQueryLayer_detectsApplicationDslDependency() {
    EvaluationResult result =
        eval(
            BoneDddArchRules.readSideDslOnlyInQueryLayer(),
            PaymentCommandHandler.class,
            ReadDslGateway.class);
    assertTrue(result.hasViolation(), report(result));
  }

  @Test
  void readSideDslOnlyInQueryLayer_allowsInfrastructureQuery() {
    assertFalse(
        eval(BoneDddArchRules.readSideDslOnlyInQueryLayer(), ReadDslGateway.class).hasViolation());
  }

  // ===== E-13.0 包表达协议后，同模块 bean 名唯一 =====

  @Test
  void springComponentBeanNamesMustBeUnique_detectsSameSimpleNameAcrossProtocolPackages() {
    // 夹具：beans.colliding.{web,rpc}.OrderController 同名且都用默认 bean 名
    EvaluationResult result =
        BoneDddArchRules.springComponentBeanNamesMustBeUnique()
            .evaluate(
                new ClassFileImporter()
                    .importPackages("com.bone.architecture.fixture.beans.colliding"));
    assertTrue(result.hasViolation(), report(result));
    assertTrue(report(result).contains("orderController"), report(result));
  }

  @Test
  void springComponentBeanNamesMustBeUnique_allowsExplicitBeanNameDisambiguation() {
    // 夹具：beans.resolved.rpc.OrderController 显式 @Component("rpcOrderController")，标记下沉 DI 标识
    EvaluationResult result =
        BoneDddArchRules.springComponentBeanNamesMustBeUnique()
            .evaluate(
                new ClassFileImporter()
                    .importPackages("com.bone.architecture.fixture.beans.resolved"));
    assertFalse(result.hasViolation(), report(result));
  }

  @Test
  void springComponentBeanNamesMustBeUnique_ignoresNonComponentSameSimpleName() {
    // 非 Spring 组件不参与判定：beans.noncomponent.{web,rpc}.OrderDetailResp 同名但不注册 bean
    EvaluationResult result =
        BoneDddArchRules.springComponentBeanNamesMustBeUnique()
            .evaluate(
                new ClassFileImporter()
                    .importPackages("com.bone.architecture.fixture.beans.noncomponent"));
    assertFalse(result.hasViolation(), report(result));
  }

  // ===== 供 imported classes 用的引用（编译期固定，运行时按 FQN 导入） =====

  /** application 类依赖 com.bone.core.usecase（noBoneCoreUseCaseApiDependency 违例夹具）。 */
  static class UsecaseClient {
    String invoke() {
      return com.bone.architecture.fixture.usecase.LegacyUseCaseApi.NAME;
    }
  }
}
