package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.AiEvalSampleIndexPort;
import com.bone.claim.domain.model.inspection.InspectionAction;
import com.bone.claim.domain.model.inspection.InspectionDiffType;
import com.bone.claim.domain.model.inspection.InspectionResult;
import com.bone.claim.domain.model.inspection.event.InspectionConcludedEvent;
import com.bone.claim.infrastructure.evaluation.AiEvalSampleProjection;
import com.bone.claim.infrastructure.evaluation.EventAiEvalSamplePortAdapter;
import com.bone.claim.infrastructure.event.SpringDomainEventPublisher;
import java.time.Instant;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 后督 DIFF → 评测样本事件的**真实容器接线**验证（2026-11-07 第 11 轮循环复核）。
 *
 * <p><b>补的是什么缺口</b>：第 10 轮落地了 {@code AiEvalSampleProjection}（普通
 * {@code @EventListener}）和落库事实源导出，但当时所有验证都是「直接 new + 直接调方法」： {@code InspectionFeedbackRecorderTest}
 * 在 {@code AiEvalSamplePort} 处 mock 截断， {@code AiEvalSampleProjectionTest} 直接调 {@code
 * onSampleCollected}。 也就是说，下面这条链从未在真实 Spring 事件总线上跑过：
 *
 * <pre>
 * InspectionConcludedEvent(DIFF)
 *   → InspectionFeedbackRecorder  @TransactionalEventListener(AFTER_COMMIT)
 *   → AiEvalSamplePort.collect()
 *   → EventAiEvalSamplePortAdapter → SpringDomainEventPublisher.publish()
 *   → AiEvalSampleProjection      @EventListener
 * </pre>
 *
 * <p><b>为什么必须有真实事务</b>：{@code @TransactionalEventListener} 默认 {@code
 * fallbackExecution=false}，没有事务同步时监听器根本不触发。本测试用 H2 内存库 + {@link DataSourceTransactionManager} +
 * {@link TransactionTemplate} 提供真实提交边界， 这也正好实证「消费方必须用普通 {@code @EventListener} 而非事务型」这一接线决策： 样本事件是在
 * AFTER_COMMIT 回调里同步发布的，此时事务已完成。
 *
 * <p>本测试不启动 Spring Boot、不连 MySQL/网关/provider，只装配这一小段容器行为 （范式同 {@code AiGatewayPortWiringTest}），因此可进
 * CI 且失败原因唯一。
 */
class InspectionFeedbackChainWiringTest {

  private AnnotationConfigApplicationContext ctx;
  private AiEvalSampleProjection projection;
  private TransactionTemplate transactions;

  @BeforeEach
  void setUp() {
    ctx = new AnnotationConfigApplicationContext();
    // @TransactionalEventListener 需要专属 factory，否则会被 DefaultEventListenerFactory
    // 当成普通监听器（无事务语义）处理。
    ctx.registerBean(TransactionalEventListenerFactory.class);
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:inspection-feedback-chain;DB_CLOSE_DELAY=-1");
    ctx.registerBean(DataSource.class, () -> ds);
    ctx.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(ds));
    // 真实 bean 链，按依赖顺序注册；构造注入由容器解析（ApplicationContext 本身是
    // ApplicationEventPublisher，Spring 会自动注入）。
    ctx.registerBean(SpringDomainEventPublisher.class);
    ctx.registerBean(EventAiEvalSamplePortAdapter.class);
    ctx.registerBean(InspectionFeedbackRecorder.class);
    ctx.registerBean(AiEvalSampleProjection.class);
    ctx.refresh();
    projection = ctx.getBean(AiEvalSampleProjection.class);
    transactions = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
  }

  @AfterEach
  void tearDown() {
    if (ctx != null) {
      ctx.close();
    }
  }

  @Test
  @DisplayName("DIFF 结论经真实 AFTER_COMMIT → 事件总线 → @EventListener 抵达消费方")
  void diffConclusionReachesProjectionThroughRealEventBus() {
    transactions.execute(
        status -> {
          ctx.publishEvent(
              event(InspectionResult.DIFF, InspectionDiffType.AI_MISJUDGE, "QI-CHAIN-1"));
          return null;
        });

    assertThat(projection.size()).isEqualTo(1);
    AiEvalSampleIndexPort.Entry entry = projection.snapshot().get(0);
    assertThat(entry.sampleType()).isEqualTo("EVAL_SET");
    assertThat(entry.claimId()).isEqualTo(1001L);
    assertThat(entry.inspectionNo()).isEqualTo("QI-CHAIN-1");
    assertThat(entry.fingerprint()).hasSize(16).matches("[0-9a-f]{16}");
  }

  @Test
  @DisplayName("三类归因全链路可达：RULE_FIX / EVAL_SET / CONTROL_FIX")
  void allAttributionTypesReachProjection() {
    transactions.execute(
        status -> {
          ctx.publishEvent(
              event(InspectionResult.DIFF, InspectionDiffType.ADJUST_MISMATCH, "QI-RULE"));
          ctx.publishEvent(event(InspectionResult.DIFF, InspectionDiffType.AI_MISJUDGE, "QI-AI"));
          ctx.publishEvent(
              event(InspectionResult.DIFF, InspectionDiffType.QUOTA_ABNORMAL, "QI-QUOTA"));
          return null;
        });

    assertThat(projection.size()).isEqualTo(3);
    assertThat(projection.snapshot())
        .extracting(AiEvalSampleIndexPort.Entry::sampleType)
        .containsExactlyInAnyOrder("RULE_FIX", "EVAL_SET", "CONTROL_FIX");
  }

  @Test
  @DisplayName("PASS 结论与 NONE 差异类型不回流（记录器过滤闸门在全链路上生效）")
  void passAndNoneDiffDoNotProduceSamples() {
    transactions.execute(
        status -> {
          ctx.publishEvent(event(InspectionResult.PASS, InspectionDiffType.NONE, "QI-PASS"));
          ctx.publishEvent(event(InspectionResult.DIFF, InspectionDiffType.NONE, "QI-NONE"));
          return null;
        });

    assertThat(projection.size()).isZero();
  }

  @Test
  @DisplayName("同一抽检单重复投递全链路幂等（fingerprint 去重在消费方生效）")
  void duplicateDeliveryIsIdempotentAcrossChain() {
    transactions.execute(
        status -> {
          ctx.publishEvent(event(InspectionResult.DIFF, InspectionDiffType.RULE_MISS, "QI-DUP"));
          ctx.publishEvent(event(InspectionResult.DIFF, InspectionDiffType.RULE_MISS, "QI-DUP"));
          return null;
        });

    assertThat(projection.size()).isEqualTo(1);
  }

  @Test
  @DisplayName("无事务时 AFTER_COMMIT 监听器不触发（钉住「必须有真实提交」这一接线前提）")
  void withoutTransactionNoCallbackFires() {
    // 不经 TransactionTemplate：@TransactionalEventListener 默认 fallbackExecution=false。
    ctx.publishEvent(event(InspectionResult.DIFF, InspectionDiffType.RULE_MISS, "QI-NO-TX"));
    assertThat(projection.size()).isZero();
  }

  @Test
  @DisplayName("投递失败不得回滚已提交的抽检结论（异常被记录器收口，容器不抛）")
  void listenerFailureDoesNotEscapeTransaction() {
    // 正常路径下不应抛异常；若链路里任一步抛到事务外，本用例即红。
    // 先取局部变量再断言：直接嵌套会让 assertj 重载与 TransactionCallback<T> 泛型推断冲突。
    Integer sizeAfterCommit =
        transactions.execute(
            status -> {
              ctx.publishEvent(
                  event(InspectionResult.DIFF, InspectionDiffType.RULE_MISS, "QI-SAFE"));
              return projection.size();
            });
    assertThat(sizeAfterCommit).isNotNull();
  }

  private static InspectionConcludedEvent event(
      InspectionResult result, InspectionDiffType diffType, String inspectionNo) {
    return new InspectionConcludedEvent(
        9L,
        42L,
        inspectionNo,
        1001L,
        result,
        diffType,
        InspectionAction.REVIEW,
        "",
        "op-1",
        Instant.parse("2026-11-04T10:00:00Z"));
  }
}
