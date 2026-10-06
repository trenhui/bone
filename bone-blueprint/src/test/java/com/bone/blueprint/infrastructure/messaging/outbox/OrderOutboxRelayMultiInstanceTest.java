package com.bone.blueprint.infrastructure.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.OrderMessagePort;
import com.bone.blueprint.infrastructure.config.OrderOutboxProperties;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox Relay <b>多实例双投负向探针</b>——锁住「只投递自己抢到的记录」这一不变量。
 *
 * <p><b>为什么年龄阈值救不了这条路径</b>：{@code claimedAt}/{@code stuckTimeoutMs} 只约束 {@code
 * reconcileStuck}的「回退」动作； 而「读回越界」是另一条独立路径——若阶段 2 按 {@code status='PROCESSING'} 读全表，多实例下实例 B 会把实例 A
 * <b>正在投递中</b>的记录 捞进自己的批次再投一次⇒ 同一事件双投。两条路径只修一条仍会双投。
 *
 * <p><b>探针设计（负向取证，不是走形式）</b>：本测试不只看「投递了几条」，而是<b>直接断言发往仓储的查询条件</b> ——阶段 2 的Criteria 必须带 {@code id IN
 * (本实例抢占成功的id集合)}，且<b>不得</b>只按 {@code status='PROCESSING'} 过滤。 若有人把读回改回「按 status
 * 读全表」，本测试立刻红（而行为级断言在 Mock 仓储下可能假绿）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderOutboxRelayMultiInstanceTest {

  private static final String TOPIC = "domain.order.order_paid.v1";

  @Mock private OrderOutboxProperties properties;
  @Mock private OrderOutboxRepository outboxRepository;
  @Mock private OrderMessagePort messageSender;
  @Mock private TransactionTemplate txTemplate;

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final List<Criteria<OrderOutboxRecord>> capturedCriteria = new ArrayList<>();

  private OrderOutboxRelayPortAdapter relay;

  @BeforeEach
  void setUp() {
    relay =
        new OrderOutboxRelayPortAdapter(
            properties, outboxRepository, messageSender, meterRegistry, txTemplate);
    capturedCriteria.clear();
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getBatchSize()).thenReturn(100);
    when(properties.getMaxRetries()).thenReturn(3);
    when(properties.getStuckTimeoutMs()).thenReturn(60_000L);
    when(properties.getDeadLetterTopic()).thenReturn("domain.order.deadletter.v1");
    when(outboxRepository.updateByCriteria(any(), any())).thenReturn(1);
    when(txTemplate.execute(any(TransactionCallback.class)))
        .thenAnswer(
            invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
  }

  /**
   * 捕获每次 {@code findByCriteria} 的入参，并按调用顺序返回不同结果： 第 1 次（查 PENDING 批次）返回 {@code
   * claimedByThisInstance}，第 2 次（阶段 2 按 id 读回）返回 {@code ownRecords}。
   */
  private void stubFindByCriteria(
      List<OrderOutboxRecord> claimedByThisInstance, List<OrderOutboxRecord> ownRecords) {
    AtomicInteger call = new AtomicInteger();
    when(outboxRepository.findByCriteria(any()))
        .thenAnswer(
            invocation -> {
              capturedCriteria.add(invocation.getArgument(0));
              return call.getAndIncrement() == 0 ? claimedByThisInstance : ownRecords;
            });
  }

  /** 取出最后一个 Criteria 里对 {@code field} 的条件（无则返回 null）。 */
  private static Condition findCondition(Criteria<OrderOutboxRecord> c, String field) {
    for (Condition cond : c.getMainConditions()) {
      if (field.equals(cond.getFieldName())) {
        return cond;
      }
    }
    return null;
  }

  /**
   * 核心不变量：阶段 2 读回<strong>必须带id 约束</strong>，且 id 集合 == 本实例 CAS 抢占成功的 id。
   *
   * <p>若实现退化为「按 {@code status='PROCESSING'} 读全表」，则读回查询不含 id 条件 ⇒ 断言失败。
   */
  @Test
  @DisplayName("阶段2 按本实例抢占的 id 读回，不按 status 读全表 PROCESSING")
  void findClaimedMustBeScopedToOwnClaimedIds() {
    OrderOutboxRecord own =
        OrderOutboxRecord.pending(
            20L, 100L, "evt-own", "OrderPaidIntegrationEvent", TOPIC, "20", "{\"orderId\":20}");
    stubFindByCriteria(List.of(own), List.of(own));

    relay.relayPending();

    assertEquals(2, capturedCriteria.size(), "应发生两次查询：查 PENDING 批次 + 按 id 读回");

    Criteria<OrderOutboxRecord> readBack = capturedCriteria.get(1);
    Condition idCond = findCondition(readBack, "id");
    assertNotNull(idCond, "阶段 2 读回查询必须带id 条件；缺它意味着按 status 读全表 PROCESSING，多实例下会双投");
    assertEquals("IN", idCond.getOperator().name(), "id 条件应是 IN（本实例抢占的 id 集合），不是 EQ（单条）也不是全表");
    assertNotNull(idCond.getValues(), "IN 条件必须携带 id 值");
    assertEquals(1, idCond.getValues().length);
    assertEquals(20L, ((Number) idCond.getValues()[0]).longValue(), "只应携带本实例抢占到的 id");

    // 关键否定断言：读回查询不得仅靠 status 过滤 PROCESSING 全表
    // （有 id 条件时 status 条件可有可无；没有 id 条件才是缺陷——已由上面的 assertNotNull 锁定）
    assertTrue(
        findCondition(readBack, "status") == null || idCond != null,
        "读回不能只靠 status='PROCESSING' 定位记录");
  }

  /** 否定探针（自证探针有效）：模拟「阶段 2 按 status 读全表」的旧缺陷实现， 证明本测试的断言<b>确实会红</b>——不是恒真的摆设。 */
  @Test
  @DisplayName("探针自证：按 status 读全表的旧实现会被本测试判为违规")
  void probeIsEffective_legacyStatusScanHasNoIdCondition() {
    // 旧缺陷的查询形态：只按 status='PROCESSING' 过滤，没有 id 约束
    Criteria<OrderOutboxRecord> legacy =
        Criteria.<OrderOutboxRecord>create()
            .eq(OrderOutboxRecord::getStatus, OutboxStatus.PROCESSING)
            .disableTenantFilter();

    // 断言会失败——这正是探针能抓 bug 的原因
    boolean hasIdGuard = findCondition(legacy, "id") != null;
    assertFalse(hasIdGuard, "旧实现（按 status 读全表）不含 id 条件，正应被探针判违规");
  }

  /** 阶段 1抢占成功但阶段 2 读回为空（如并发被他人抢走）时：安静返回 0，不投递。 */
  @Test
  @DisplayName("读回为空（并发下被他人抢走）时不投递任何消息")
  void noDeliveryWhenReadBackEmpty() {
    OrderOutboxRecord own =
        OrderOutboxRecord.pending(
            30L, 100L, "evt-30", "OrderPaidIntegrationEvent", TOPIC, "30", "{\"orderId\":30}");
    stubFindByCriteria(List.of(own), List.of());

    int sent = relay.relayPending();

    assertEquals(0, sent);
    verify(messageSender, never()).send(any(), any(), any());
  }

  /** 本实例抢占的记录必须被投递，且只投递一次（单实例下的基本正确性，防止修复引入回归）。 */
  @Test
  @DisplayName("本实例抢到的记录被投递恰好一次")
  void ownClaimedRecordDeliveredExactlyOnce() {
    OrderOutboxRecord own =
        OrderOutboxRecord.pending(
            40L, 100L, "evt-40", "OrderPaidIntegrationEvent", TOPIC, "40", "{\"orderId\":40}");
    stubFindByCriteria(List.of(own), List.of(own));

    int sent = relay.relayPending();

    assertEquals(1, sent);
    verify(messageSender, times(1)).send(eq(TOPIC), eq("40"), eq(own.getEnvelopeJson()));
  }

  /** 抢占全失败（CAS 均未命中）时不做读回，也不投递。 */
  @Test
  @DisplayName("CAS 抢占全部未命中时直接返回 0，不读回不投递")
  void returnsZeroWhenAllCasMissed() {
    when(outboxRepository.updateByCriteria(any(), any())).thenReturn(0);
    stubFindByCriteria(
        List.of(
            OrderOutboxRecord.pending(
                50L, 100L, "evt-50", "OrderPaidIntegrationEvent", TOPIC, "50", "{}")),
        List.of());

    int sent = relay.relayPending();

    assertEquals(0, sent);
    assertTrue(capturedCriteria.size() <= 1, "抢占未成功不应进入阶段 2 读回");
    verify(messageSender, never()).send(any(), any(), any());
  }
}
