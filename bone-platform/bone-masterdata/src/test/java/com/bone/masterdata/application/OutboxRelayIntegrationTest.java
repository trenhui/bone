package com.bone.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.masterdata.application.MasterDataOutboxRelay.ReconciliationReport;
import com.bone.masterdata.application.MasterDataOutboxRelay.RelayResult;
import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import com.bone.masterdata.domain.model.dispatch.OutboxDelivery.State;
import com.bone.masterdata.testsupport.changerequest.InMemoryMasterDataProjection;
import com.bone.masterdata.testsupport.changerequest.InMemoryOutboxRelayStore;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 平台 Outbox 中继联调：幂等、版本单调、租户绑定、有界重试/死信、版本戳对账。 */
class OutboxRelayIntegrationTest {

  private static final Instant T0 = Instant.parse("2026-11-13T02:00:00Z");
  private static final String CONSUMER = "procura-srm";
  private static final String HASH = "a".repeat(64);

  private final InMemoryOutboxRelayStore outbox = new InMemoryOutboxRelayStore();
  private final InMemoryMasterDataProjection projection = new InMemoryMasterDataProjection();
  private MasterDataOutboxRelay relay;

  @BeforeEach
  void setUp() {
    outbox.reset();
    projection.reset();
    projection.authorize(1L, "P-1", CONSUMER);
    relay = new MasterDataOutboxRelay(outbox, projection, CONSUMER, 1);
  }

  @Test
  @DisplayName("正常投递：事件被投影一次，投递单转 DELIVERED，对账一致")
  void happyPath() {
    MasterDataRecordDispatchedEvent event = event(1L, "P-1", 1L, "GOLDEN_RECORD_MERGED", HASH);
    outbox.append(event);

    RelayResult result = relay.relayBatch(10);
    assertThat(result.claimed()).isEqualTo(1);
    assertThat(result.delivered()).isEqualTo(1);
    assertThat(outbox.stateOf(event.idempotencyKey())).isEqualTo(State.DELIVERED);
    assertThat(projection.projections()).hasSize(1);

    ReconciliationReport report = relay.reconcile(1L, "P-1");
    assertThat(report.consistent()).isTrue();
    assertThat(report.outboxMaxVersion()).isEqualTo(1L);
    assertThat(report.projectionVersion()).isEqualTo(1L);
    assertThat(report.duplicateProjections()).isZero();
  }

  @Test
  @DisplayName("N7：模拟「投影已写但状态未回写」的崩溃重放，不得产生第二份投影")
  void replayAfterCrashDoesNotDuplicateProjection() {
    MasterDataRecordDispatchedEvent event = event(1L, "P-1", 1L, "GOLDEN_RECORD_MERGED", HASH);
    outbox.append(event);
    assertThat(relay.relayBatch(10).delivered()).isEqualTo(1);

    outbox.resetDeliveryState(event.idempotencyKey());
    RelayResult replay = relay.relayBatch(10);
    assertThat(replay.delivered()).isZero();
    assertThat(replay.stale() + replay.duplicate()).isEqualTo(1);
    assertThat(projection.projections()).hasSize(1);
    assertThat(relay.reconcile(1L, "P-1").consistent()).isTrue();
  }

  @Test
  @DisplayName("N8：低版本陈旧事件被跳过，投影版本不倒退")
  void staleVersionIsSkipped() {
    outbox.append(event(1L, "P-1", 2L, "GOLDEN_RECORD_MERGED", HASH));
    assertThat(relay.relayBatch(10).delivered()).isEqualTo(1);
    MasterDataRecordDispatchedEvent stale =
        event(1L, "P-1", 1L, "GOLDEN_RECORD_MERGED", "b".repeat(64));
    outbox.append(stale);

    RelayResult result = relay.relayBatch(10);
    assertThat(result.stale()).isEqualTo(1);
    assertThat(result.delivered()).isZero();
    assertThat(projection.lastVersion(1L, "P-1")).isEqualTo(2L);
    assertThat(projection.projections()).hasSize(1);
  }

  @Test
  @DisplayName("N8：缺版本戳的事件在信封构造期即 fail-closed")
  void missingVersionStampRejected() {
    assertThatThrownBy(
            () -> MasterDataRecordDispatchedEvent.of("E-0", 1L, "P-1", 0L, "T", 1, T0, HASH))
        .hasMessageContaining("版本必须为正数");
  }

  @Test
  @DisplayName("非法摘要事件入死信且保留原因，零投影")
  void invalidHashGoesToDeadLetter() {
    MasterDataRecordDispatchedEvent bad = event(1L, "P-1", 1L, "T", "not-a-sha256");
    outbox.append(bad);

    RelayResult result = relay.relayBatch(10);
    assertThat(result.dead()).isEqualTo(1);
    assertThat(result.delivered()).isZero();
    assertThat(outbox.deadLetters()).hasSize(1);
    assertThat(outbox.deadLetters().get(0).getLastError()).contains("摘要");
    assertThat(projection.projections()).isEmpty();
  }

  @Test
  @DisplayName("未授权租户/主体/消费方绑定被判失败，绝不静默投递")
  void unauthorizedBindingGoesToDeadLetter() {
    MasterDataRecordDispatchedEvent foreign = event(2L, "P-9", 1L, "GOLDEN_RECORD_MERGED", HASH);
    outbox.append(foreign);

    RelayResult result = relay.relayBatch(10);
    assertThat(result.unauthorized()).isEqualTo(1);
    assertThat(result.dead()).isEqualTo(1);
    assertThat(outbox.deadLetters().get(0).getLastError()).contains("授权");
    assertThat(projection.projections()).isEmpty();
  }

  @Test
  @DisplayName("有界重试：未达上限留 PENDING 可重试，达上限转死信")
  void boundedRetryThenDeadLetter() {
    MasterDataOutboxRelay retrying = new MasterDataOutboxRelay(outbox, projection, CONSUMER, 2);
    MasterDataRecordDispatchedEvent foreign = event(2L, "P-9", 1L, "GOLDEN_RECORD_MERGED", HASH);
    outbox.append(foreign);

    RelayResult first = retrying.relayBatch(10);
    assertThat(first.retried()).isEqualTo(1);
    assertThat(first.dead()).isZero();
    assertThat(outbox.stateOf(foreign.idempotencyKey())).isEqualTo(State.PENDING);

    RelayResult second = retrying.relayBatch(10);
    assertThat(second.dead()).isEqualTo(1);
    assertThat(outbox.deadLetters()).hasSize(1);
  }

  @Test
  @DisplayName("对账必须检出重复投影（负向控制：证明对账不是恒绿判据）")
  void reconcileDetectsDuplicateProjection() {
    MasterDataRecordDispatchedEvent event = event(1L, "P-1", 1L, "T", HASH);
    outbox.append(event);
    relay.relayBatch(10);

    // 模拟消费侧完全不做幂等/版本守护，直接重复落投影。
    projection.setDuplicateGuardEnabled(false);
    assertThat(projection.applyIfNew(event)).isTrue();
    ReconciliationReport report = relay.reconcile(1L, "P-1");
    assertThat(report.duplicateProjections()).isEqualTo(1);
    assertThat(report.consistent()).isFalse();
  }

  @Test
  @DisplayName("对账在零投递时亦为一致（空集不误报漂移）")
  void reconcileEmptyIsConsistent() {
    ReconciliationReport report = relay.reconcile(1L, "P-1");
    assertThat(report.consistent()).isTrue();
    assertThat(report.outboxMaxVersion()).isZero();
  }

  @Test
  @DisplayName("批量大小与具名 consumer-id fail-closed")
  void configurationGuards() {
    assertThatThrownBy(() -> relay.relayBatch(0)).hasMessageContaining("批量大小必须为正数");
    assertThatThrownBy(() -> new MasterDataOutboxRelay(outbox, projection, " ", 1))
        .hasMessageContaining("缺少具名 consumer-id");
    assertThatThrownBy(() -> new MasterDataOutboxRelay(outbox, projection, CONSUMER, 0))
        .hasMessageContaining("最大重试次数必须为正数");
  }

  private static MasterDataRecordDispatchedEvent event(
      long tenantId, String partyId, long version, String type, String hash) {
    return MasterDataRecordDispatchedEvent.of(
        "E-" + tenantId + "-" + partyId + "-" + version,
        tenantId,
        partyId,
        version,
        type,
        1,
        T0,
        hash);
  }
}
