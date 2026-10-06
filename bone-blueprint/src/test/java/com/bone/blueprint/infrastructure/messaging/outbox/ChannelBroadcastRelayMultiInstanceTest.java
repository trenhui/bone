package com.bone.blueprint.infrastructure.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.ChannelExtensionPort;
import com.bone.blueprint.domain.extension.channel.ChannelListingResult;
import com.bone.blueprint.domain.repository.ChannelProductRepository;
import com.bone.blueprint.infrastructure.config.ChannelBroadcastProperties;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.ArrayList;
import java.util.List;
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
 * 渠道库存广播 Relay <b>多实例双投负向探针</b>——与 {@code OrderOutboxRelayMultiInstanceTest} <b>同构且对称</b>。
 *
 * <p><b>为什么要对称地写两份</b>：Order relay 与 Broadcast relay 是同一机制的两份拷贝，而这份缺陷<em>正是</em>「同构」时最危险的地方：
 * 两处会<strong>同时错、同时被复制、同时缺防护</strong>。只在一个侧写探针＝给「两处漂移」留了后门——Broadcast 侧退回status 全表写法时
 * 不会有任何测试报警。因此每修一处，另一处必须同步补探针。
 *
 * <p><b>断言层次</b>：不断言「同步了几条」（Mock 扩展点下行为断言易假绿），而<b>直接断言发往仓储的查询条件</b> ——阶段 2 必须带 {@code id IN
 * (本实例抢占成功的id集合)}。有人把读回改回「按 {@code status='PROCESSING'} 读全表」时本测试立刻红。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChannelBroadcastRelayMultiInstanceTest {

  @Mock private ChannelBroadcastProperties properties;
  @Mock private ChannelBroadcastTaskRepository broadcastTaskRepository;
  @Mock private ChannelProductRepository channelProductRepository;
  @Mock private ChannelExtensionPort channelExtensionPort;
  @Mock private TransactionTemplate txTemplate;

  private final List<Criteria<ChannelBroadcastTask>> capturedCriteria = new ArrayList<>();

  private ChannelBroadcastRelayPortAdapter relay;

  @BeforeEach
  void setUp() {
    relay =
        new ChannelBroadcastRelayPortAdapter(
            properties,
            broadcastTaskRepository,
            channelProductRepository,
            channelExtensionPort,
            txTemplate);
    capturedCriteria.clear();
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getBatchSize()).thenReturn(50);
    when(properties.getStuckTimeoutMs()).thenReturn(120_000L);
    when(properties.getMaxRetry()).thenReturn(5);
    when(properties.getBaseBackoffMs()).thenReturn(10_000L);
    when(properties.getMaxBackoffMs()).thenReturn(300_000L);
    when(broadcastTaskRepository.updateByCriteria(any(), any())).thenReturn(1);
    when(txTemplate.execute(any(TransactionCallback.class)))
        .thenAnswer(
            invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    when(channelExtensionPort.syncInventory(any()))
        .thenReturn(ChannelListingResult.ok("cp-1", "ok"));
  }

  private static ChannelBroadcastTask task(long id, String channelCode) {
    return ChannelBroadcastTask.pending(id, 100L, 900L, channelCode, "cp-" + id, "商品" + id, 10, 5);
  }

  /**
   * 捕获每次 {@code findByCriteria} 入参，并<strong>按条件内容</strong>（而非调用顺序）分派返回结果。
   *
   * <p><b>为什么不能按顺序</b>（2026-10-06 修正）：早期实现用「第 0 次=自愈、1=抢占、2+=读回」的顺序假设， 但 {@code reconcileStuck}
   * 是否先执行、内部是否有其它仓储调用都会让顺序错位， 一错位则阶段 2 拿到错误的结果集 ⇒「按 id 读回」这一断言<b>测的其实是桩的时序而非产品行为</b>，
   * 属于典型的假绿风险。现在改为识别条件语义：
   *
   * <ul>
   *   <li>带 {@code id} 条件 ⇒ 阶段 2 按 id 读回 ⇒ 返回 {@code ownRecords}
   *   <li>带 {@code status=PENDING} ⇒ 抢占源 ⇒ 返回 {@code claimedSource}
   *   <li>其余（自愈扫描等）⇒ 返回空
   * </ul>
   *
   * <p>⚠️ <b>必须让 default 方法执行真实实现</b>：mock 的是 {@code ChannelBroadcastTaskRepository} 接口， Mockito
   * 会拦截 default 方法直接返回 null，其内部的 {@code findByCriteria} 永远不会执行 ⇒ 捕获不到任何条件。
   */
  private void stubFindByCriteria(
      List<ChannelBroadcastTask> claimedSource, List<ChannelBroadcastTask> ownRecords) {
    when(broadcastTaskRepository.findStuckProcessing(any())).thenCallRealMethod();
    when(broadcastTaskRepository.findPendingBatch(anyInt())).thenCallRealMethod();
    when(broadcastTaskRepository.findProcessingByIds(any())).thenCallRealMethod();

    when(broadcastTaskRepository.findByCriteria(any()))
        .thenAnswer(
            invocation -> {
              Criteria<ChannelBroadcastTask> c = invocation.getArgument(0);
              capturedCriteria.add(c);
              // 按条件语义分派，与调用顺序解耦
              if (findCondition(c, "id") != null) {
                return ownRecords;
              }
              if (findCondition(c, "status") != null) {
                Condition status = findCondition(c, "status");
                Object v =
                    status.getValues() == null || status.getValues().length == 0
                        ? null
                        : status.getValues()[0];
                // PENDING 才是抢占源；PROCESSING 是自愈扫描（返回空表示无卡死）
                return v == BroadcastTaskStatus.PENDING ? claimedSource : List.of();
              }
              return List.of();
            });
  }

  private static Condition findCondition(Criteria<ChannelBroadcastTask> c, String field) {
    for (Condition cond : c.getMainConditions()) {
      if (field.equals(cond.getFieldName())) {
        return cond;
      }
    }
    return null;
  }

  /**
   * 核心不变量：阶段 2 读回<strong>必须带 id 约束</strong>，且 id 集合 == 本实例 CAS 抢占成功的 id。
   *
   * <p>若实现退化为「按 {@code status='PROCESSING'} 读全表」，则读回查询不含 id 条件 ⇒ 断言失败。
   */
  @Test
  @DisplayName("阶段2 按本实例抢占的 id 读回，不按 status 读全表 PROCESSING")
  void findProcessingMustBeScopedToOwnClaimedIds() {
    ChannelBroadcastTask own = task(20L, "TAOBAO");
    stubFindByCriteria(List.of(own), List.of(own));

    relay.relayPending();

    // 不依赖调用顺序：定位「带 id 条件」的那次查询——它就是阶段 2 的按 id 读回
    Criteria<ChannelBroadcastTask> readBack = null;
    for (Criteria<ChannelBroadcastTask> c : capturedCriteria) {
      if (findCondition(c, "id") != null) {
        readBack = c;
        break;
      }
    }
    assertNotNull(readBack, "阶段 2 读回查询必须带 id 条件；缺它意味着按 status 读全表 PROCESSING，多实例下会重复同步渠道库存");

    Condition idCond = findCondition(readBack, "id");
    assertEquals("IN", idCond.getOperator().name(), "id 条件应是 IN（本实例抢占的 id 集合）");
    assertNotNull(idCond.getValues(), "IN 条件必须携带 id 值");
    assertEquals(1, idCond.getValues().length);
    assertEquals(20L, ((Number) idCond.getValues()[0]).longValue(), "只应携带本实例抢占到的 id");
  }

  /** 否定探针（自证探针有效）：构造「按 status 读全表」的旧缺陷查询形态， 证明本测试的断言<b>确实会红</b>——不是恒真的摆设。 */
  @Test
  @DisplayName("探针自证：按 status 读全表的旧实现会被本测试判为违规")
  void probeIsEffective_legacyStatusScanHasNoIdCondition() {
    Criteria<ChannelBroadcastTask> legacy =
        Criteria.<ChannelBroadcastTask>create()
            .eq(ChannelBroadcastTask::getStatus, BroadcastTaskStatus.PROCESSING)
            .disableTenantFilter();

    boolean hasIdGuard = findCondition(legacy, "id") != null;
    assertFalse(hasIdGuard, "旧实现（按 status 读全表）不含 id 条件，正应被探针判违规");
  }

  /** 阶段 2 读回为空（如并发下被他人抢走）时：安静返回 0，不调渠道。 */
  @Test
  @DisplayName("读回为空（并发下被他人抢走）时不调渠道")
  void noDeliveryWhenReadBackEmpty() {
    ChannelBroadcastTask own = task(30L, "TAOBAO");
    stubFindByCriteria(List.of(own), List.of());

    int sent = relay.relayPending();

    assertEquals(0, sent);
    verify(channelExtensionPort, never()).syncInventory(any());
  }

  /** 本实例抢到的记录必须被同步，且只同步一次（防修复引入回归）。 */
  @Test
  @DisplayName("本实例抢到的记录被同步恰好一次")
  void ownClaimedRecordDeliveredExactlyOnce() {
    ChannelBroadcastTask own = task(40L, "TAOBAO");
    stubFindByCriteria(List.of(own), List.of(own));

    int sent = relay.relayPending();

    assertEquals(1, sent);
    verify(channelExtensionPort, times(1)).syncInventory(any());
  }

  /** CAS 抢占全部未命中时不做读回，也不调渠道。 */
  @Test
  @DisplayName("CAS 抢占全部未命中时直接返回 0，不读回不同步")
  void returnsZeroWhenAllCasMissed() {
    when(broadcastTaskRepository.updateByCriteria(any(), any())).thenReturn(0);
    stubFindByCriteria(List.of(task(50L, "TAOBAO")), List.of());

    int sent = relay.relayPending();

    assertEquals(0, sent);
    // 抢占未成功 ⇒ 不进入阶段 2；唯一一次查询是 reconcileStuck 的自愈扫描
    for (Criteria<ChannelBroadcastTask> c : capturedCriteria) {
      assertNull(findCondition(c, "id"), "抢占未成功不应出现按 id 读回 PROCESSING 的查询");
    }
    verify(channelExtensionPort, never()).syncInventory(any());
  }
}
