package com.bone.blueprint.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.blueprint.BoneBlueprintApplication;
import com.bone.blueprint.adapter.schedule.OrderOutboxRelayJob;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ADR-0031 D3 集成测试（本地 MySQL / 真实 SDK 元数据）：领域事件的「同线程租户上下文」。
 *
 * <p><b>为什么必须集成测试</b>：{@code PaymentSucceededEventHandler} 是
 * {@code @TransactionalEventListener(AFTER_COMMIT)}——它在<strong>提交事务的同一线程</strong>上被同步回调，内部用 {@code
 * REQUIRES_NEW} 事务调 {@code orderRepository.update(order)}。而 SDK 写路径（{@code DynamicUpdateBuilder}）
 * <strong>只认 {@code TenantContext}</strong>，没有 caller-EQ 兜底、缺失即失败关闭（写侧约束见 ADR-0031 背景 §1 /
 * D0；租户注入总则见 ADR-0029）。因此整条链路成立的唯一前提是：请求线程上的 {@code TenantContext} 在 AFTER_COMMIT 回调时仍在该线程。mock
 * 单测用假仓储，永远看不到这条依赖，故只能靠本测试兜底。
 *
 * <p><b>底座</b>：复用现网 dev 数据源（{@code localhost:3306/bone}，密码取 {@code BONE_DB_PASSWORD}）与全量 {@code
 * BoneBlueprintApplication} 装配，最大化贴近生产。默认 {@code mvn test} 不执行（{@code @Tag("integration")} 被
 * surefire 排除），显式跑：{@code mvn -pl bone-blueprint test -Pintegration}。
 */
@Tag("integration")
@SpringBootTest(
    classes = BoneBlueprintApplication.class,
    properties = {
      // 中和注册中心：集成测试不依赖 Nacos。
      "spring.cloud.discovery.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.nacos.discovery.register-enabled=false",
      // 与 dev 对齐（application.yml 默认已是 false，此处显式声明避免 profile 漂移）。
      "bone.blueprint.outbox.mq-enabled=false",
      // 三个全租户扫描任务（取消超时单 / 关闭超时支付 / 钱货不一致核查）用 Spring 的 "-"（CRON_DISABLED）禁用，
      // 避免集成测试改动共享 dev 库的真实数据。
      "bone.blueprint.schedule.cancel-expired-orders-cron=-",
      "bone.blueprint.schedule.close-expired-payments-cron=-",
      "bone.blueprint.schedule.payment-inconsistency-check-cron=-",
      // 覆盖默认弱密钥，避免启动告警；本测试不验签，取值仅需 >= 32 字节。
      "bone.iam.jwt.secret-key=blueprint-it-only-secret-key-min-32-bytes-long"
    })
class EventTenantContextIntegrationTest {

  /** 固定测试租户/客户：便于断言 Outbox 归属，且不会与库内既有数据冲突。 */
  private static final long TENANT_ID = 9_900_001L;

  private static final long CUSTOMER_ID = 5_500_001L;

  private static final String ORDER_PAID_EVENT_TYPE = "OrderPaidIntegrationEvent";

  /** 单调递增的测试主键，避免与库内既有行（含历史跑批残留）撞主键。 */
  private static final AtomicLong ID_SEQ = new AtomicLong(System.currentTimeMillis() * 1000);

  @Autowired private OrderRepository orderRepository;

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private JdbcTemplate jdbcTemplate;

  /**
   * 关掉 Outbox 中继任务：它经 logging 发送器（mq 关闭）把共享 dev 库中真实的 PENDING 行标记为 SENT，等同丢弃这些事件。 集成测试跑在共享 dev
   * 库上，绝不能触发它（@MockBean 替换后其 @Scheduled 不再注册）。
   */
  @MockBean private OrderOutboxRelayJob orderOutboxRelayJob;

  private TransactionTemplate txTemplate;

  /** 复用现网 dev 数据源地址/用户名（application.yml），仅把密码来源补成「环境变量优先 + 文档默认值」。 */
  @DynamicPropertySource
  static void localMysqlPassword(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("BONE_DB_PASSWORD", "mysql123"));
  }

  @BeforeEach
  void setUp() {
    txTemplate = new TransactionTemplate(transactionManager);
  }

  @AfterEach
  void tearDown() {
    // 不用 TenantContext.clear()：它走 TransmittableThreadLocal.remove()，会把静态租户 TTL 从全局传播注册表中摘除，
    // 使后续依赖 TTL 传播的用例按执行顺序静默失效；setTenantId(null) 语义等价且不会摘除注册。
    TenantContext.setTenantId((Long) null);
    // 清理本测试写入共享 dev 库的数据：TENANT_ID 是本测试专用命名空间，删除其全部行即可，避免长期累积污染。
    jdbcTemplate.update("DELETE FROM bp_outbox WHERE tenant_id = ?", TENANT_ID);
    jdbcTemplate.update("DELETE FROM t_order WHERE tenant_id = ?", TENANT_ID);
  }

  @Test
  @DisplayName("AFTER_COMMIT 处理器在「同请求线程租户上下文」下正确写入：订单确认 PAID + Outbox 落库")
  void afterCommitHandlerRunsWithSameThreadTenantContext() {
    long orderId = nextId();
    long paymentId = nextId();
    BigDecimal amount = new BigDecimal("199.00");
    Order order =
        Order.create(
            orderId,
            TENANT_ID,
            CUSTOMER_ID,
            List.of(OrderItem.create(nextId(), orderId, 1001L, "集成测试商品", 1, amount)));
    PaymentSucceededEvent event =
        new PaymentSucceededEvent(
            paymentId, TENANT_ID, orderId, amount, "CH-IT-0001", Instant.now());
    int outboxBefore = countOrderPaidOutbox();

    // 模拟「请求线程」：在其上设置租户上下文，并在同一事务内保存订单、发布支付成功事件。
    txTemplate.executeWithoutResult(
        status -> {
          TenantContext.setTenantId(TENANT_ID);
          orderRepository.save(order);
          eventPublisher.publishEvent(event);
        });
    // 事务提交即触发 AFTER_COMMIT 处理器（同线程、REQUIRES_NEW）。若租户上下文不在该线程，
    // 处理器内 orderRepository.update(order) 会抛 MissingTenantContextException，下方断言随即失败。

    // 读回订单：显式置上下文，保证读与写在同一租户语义下。
    TenantContext.setTenantId(TENANT_ID);
    Order updated = Optional.ofNullable(orderRepository.findById(orderId)).orElseThrow();
    // 本断言才是「同线程租户上下文」的核心证据：CREATED→PAID 只能由 AFTER_COMMIT 处理器内的 SDK update 完成，
    // 而该 update 无 caller-EQ 兜底、缺上下文即抛异常，故能通过 ⟺ 上下文确实留存在回调线程。
    assertThat(updated.getStatus())
        .as("AFTER_COMMIT 处理器应在同线程租户上下文下把订单从 CREATED 确认为 PAID")
        .isEqualTo(OrderStatus.PAID);

    // 增量断言：只认「本 run 新增」，避免 dev 库历史残留行造成假通过。
    // 说明：bp_outbox.tenant_id 取自事件载荷（OrderPaidIntegrationEvent.tenantId ← Order.getTenantId），并非取自
    // 线程上下文；此处仅证明处理器已跑完并把集成事件落库，租户上下文的作用由上面的 PAID 断言证明。
    assertThat(countOrderPaidOutbox())
        .as("AFTER_COMMIT 处理器完成后，本 run 应新增一行 OrderPaid 集成事件")
        .isGreaterThan(outboxBefore);
  }

  @Test
  @DisplayName("前置不变量：SDK update 写路径缺 TenantContext 时失败关闭——正是 AFTER_COMMIT 必须同线程带上下文的原因")
  void updateWithoutTenantContextFailsClosed() {
    long orderId = nextId();
    Order order =
        Order.create(
            orderId,
            TENANT_ID,
            CUSTOMER_ID,
            List.of(
                OrderItem.create(nextId(), orderId, 1002L, "集成测试商品", 2, new BigDecimal("10.00"))));
    txTemplate.executeWithoutResult(
        status -> {
          TenantContext.setTenantId(TENANT_ID);
          orderRepository.save(order);
        });

    Order loaded = Optional.ofNullable(orderRepository.findById(orderId)).orElseThrow();
    TenantContext.setTenantId((Long) null);

    assertThatThrownBy(
            () -> txTemplate.executeWithoutResult(status -> orderRepository.update(loaded)))
        .as("写路径无 caller-EQ 逃生舱：租户表 update 缺 TenantContext 必须失败关闭")
        .isInstanceOf(MissingTenantContextException.class)
        // 绑定到具体租户表名：防止元数据退化导致 update 静默成功时，反例变成「越测试越假通过」。
        .hasMessageContaining("t_order");
  }

  private int countOrderPaidOutbox() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bp_outbox WHERE tenant_id = ? AND event_type = ?",
            Integer.class,
            TENANT_ID,
            ORDER_PAID_EVENT_TYPE);
    return count == null ? 0 : count;
  }

  private static long nextId() {
    return ID_SEQ.incrementAndGet();
  }
}
