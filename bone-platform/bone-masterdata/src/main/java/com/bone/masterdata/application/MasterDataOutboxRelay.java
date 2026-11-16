package com.bone.masterdata.application;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.gateway.MasterDataProjectionPort;
import com.bone.masterdata.domain.gateway.OutboxRelayStorePort;
import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import com.bone.masterdata.domain.model.dispatch.OutboxDelivery;
import com.bone.masterdata.domain.model.dispatch.OutboxDelivery.State;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 平台 Outbox 中继：把事务性发件箱里的版本化事件<b>有界重试、幂等、可死信、可对账</b>地投递到消费侧投影。
 *
 * <p><b>为什么必须有独立中继</b>：{@code MasterDataOutboxPort.append} 只保证「事件与业务写入同事务」，
 * 不保证「事件被送达」。若由业务线程直接发消息，进程崩溃就永久丢失；若用「无限重扫全表」补发， 重复事件会产生重复投影（N7）且失败次数不可查。业界事务性 Outbox 模式的标准解法即
 * 「同事务写发件箱 + 独立中继 + 消费侧幂等 + 死信 + 版本戳对账」。
 *
 * <p><b>五条硬约束</b>：
 *
 * <ol>
 *   <li><b>租户/订阅绑定</b>：{@code isAuthorized} 不通过即失败入死信，绝不静默投递给未授权消费方；
 *   <li><b>信封校验</b>：版本戳与 schemaVersion 必须为正、摘要必须是 64 位小写十六进制（HMAC-SHA256）； 缺版本戳（N8）或缺摘要的事件一律不投递；
 *   <li><b>幂等 + 单调</b>：{@code applyIfNew} 与 {@code lastVersion} 双重守护，重复/陈旧事件零新投影（N7）；
 *   <li><b>有界重试</b>：失败计数递增，达上限转 DEAD 并保留原因，可人工重放，不无限重试也不静默丢弃；
 *   <li><b>可核对</b>：{@link #reconcile} 比较发件箱已投递最大版本与消费侧投影版本，并检测重复投影。
 * </ol>
 *
 * <p><b>终局一致性</b>：生产只替换 {@code OutboxRelayStorePort}（{@code mdm_outbox} JDBC）与 {@code
 * MasterDataProjectionPort}（消费侧 adapter），本类的语义、幂等键与版本规则不变。 能力开关默认关闭：Outbox 表/中继部署未就绪时不注册 bean。
 *
 * <p><b>配置树契约（2026-11-15 澄清）</b>：本模块刻意存在两棵配置树，<b>不可混写</b>——
 *
 * <ul>
 *   <li>能力开关：{@code bone.masterdata.outbox-relay.enabled}（{@code @ConditionalOnProperty}），缺省不装配；
 *   <li>业务取值：{@code masterdata.outbox-relay.consumer-id} / {@code max-attempts}（{@code @Value}）， 与
 *       {@code application.yml} 的 {@code masterdata:} 段、{@code MasterDataProperties} 同树。
 * </ul>
 *
 * <p>曾把测试属性注入到 {@code bone.masterdata.outbox-relay.consumer-id}，导致注入从未生效、静默回落到 yml 默认值，具名消费者授权在 E2E
 * 里成了空设置（假绿）。现由 {@code ChangeRequestHttpIntegrationTest#namedConsumerIdIsActuallyInjected}
 * 以真实投递断言守护： 恢复错误前缀即必红。同类约束见 {@link GoldenRecordApplicationService} 的 digest-pepper 说明。
 */
@Service
@ConditionalOnProperty(
    prefix = "bone.masterdata.outbox-relay",
    name = "enabled",
    havingValue = "true")
public class MasterDataOutboxRelay {

  private static final Pattern HEX_SHA256 = Pattern.compile("[0-9a-f]{64}");

  private final OutboxRelayStorePort store;
  private final MasterDataProjectionPort projection;
  private final String consumerId;
  private final int maxAttempts;

  public MasterDataOutboxRelay(
      OutboxRelayStorePort store,
      MasterDataProjectionPort projection,
      @Value("${masterdata.outbox-relay.consumer-id}") String consumerId,
      @Value("${masterdata.outbox-relay.max-attempts:3}") int maxAttempts) {
    if (consumerId == null || consumerId.trim().isEmpty()) {
      throw new DomainException("Outbox 中继缺少具名 consumer-id，禁止匿名投递");
    }
    if (maxAttempts < 1) {
      throw new DomainException("Outbox 中继最大重试次数必须为正数");
    }
    this.store = store;
    this.projection = projection;
    this.consumerId = consumerId;
    this.maxAttempts = maxAttempts;
  }

  /** 处理一批待投递事件；返回可核对的计数，不抛给调用方（单条失败不应中断整批）。 */
  public RelayResult relayBatch(int limit) {
    if (limit <= 0) {
      throw new DomainException("中继批量大小必须为正数");
    }
    Instant now = Instant.now();
    List<OutboxDelivery> pending = store.findPending(limit);
    int delivered = 0;
    int stale = 0;
    int duplicate = 0;
    int unauthorized = 0;
    int dead = 0;
    int retried = 0;

    for (OutboxDelivery delivery : pending) {
      MasterDataRecordDispatchedEvent event = delivery.getEvent();
      String key = event.idempotencyKey();
      try {
        validateEnvelope(event);
        if (!projection.isAuthorized(event, consumerId)) {
          unauthorized++;
          throw new DomainException("消费侧订阅授权或租户绑定校验失败，禁止投递");
        }
        long last = projection.lastVersion(event.getTenantId(), event.getPartyId());
        if (event.getRecordVersion() <= last) {
          store.markDelivered(key, "STALE");
          stale++;
          continue;
        }
        if (!projection.applyIfNew(event)) {
          store.markDelivered(key, "DUPLICATE");
          duplicate++;
          continue;
        }
        store.markDelivered(key, "DELIVERED");
        delivered++;
      } catch (RuntimeException ex) {
        OutboxDelivery failed = delivery.failed(sanitize(ex), maxAttempts, now);
        store.recordFailure(key, failed.getLastError(), failed.isDead());
        if (failed.isDead()) {
          dead++;
        } else {
          retried++;
        }
      }
    }
    return new RelayResult(
        pending.size(), delivered, stale, duplicate, unauthorized, dead, retried);
  }

  /** 版本戳对账：发件箱已投递最大版本 vs 消费侧投影版本，并检测重复投影。 */
  public ReconciliationReport reconcile(long tenantId, String partyId) {
    long outboxMax =
        store.all().stream()
            .filter(d -> d.getState() == State.DELIVERED)
            .map(OutboxDelivery::getEvent)
            .filter(e -> e.getTenantId() == tenantId && partyId.equals(e.getPartyId()))
            .mapToLong(MasterDataRecordDispatchedEvent::getRecordVersion)
            .max()
            .orElse(0L);
    long projectionVersion = projection.lastVersion(tenantId, partyId);
    Set<String> seen = new HashSet<>();
    int duplicates = 0;
    for (MasterDataRecordDispatchedEvent e : projection.projections()) {
      if (e.getTenantId() != tenantId || !partyId.equals(e.getPartyId())) {
        continue;
      }
      if (!seen.add(e.idempotencyKey())) {
        duplicates++;
      }
    }
    boolean consistent = outboxMax == projectionVersion && duplicates == 0;
    return new ReconciliationReport(
        tenantId, partyId, outboxMax, projectionVersion, duplicates, consistent);
  }

  private void validateEnvelope(MasterDataRecordDispatchedEvent event) {
    if (event == null) {
      throw new DomainException("投递单缺少事件信封");
    }
    if (event.getRecordVersion() <= 0 || event.getSchemaVersion() <= 0) {
      throw new DomainException("分发事件缺少版本戳或 schemaVersion");
    }
    if (event.getPayloadHash() == null || !HEX_SHA256.matcher(event.getPayloadHash()).matches()) {
      throw new DomainException("分发事件缺少合法的 HMAC-SHA256 摘要");
    }
    if (event.getTenantId() < 0 || event.getPartyId() == null) {
      throw new DomainException("分发事件缺少租户或主体标识");
    }
  }

  /** 异常信息不回传 PII 原文；只保留异常类型与简要信息。 */
  private static String sanitize(RuntimeException ex) {
    String message = ex.getMessage() == null ? "" : ex.getMessage();
    return ex.getClass().getSimpleName()
        + ":"
        + message.substring(0, Math.min(message.length(), 120));
  }

  public record RelayResult(
      int claimed,
      int delivered,
      int stale,
      int duplicate,
      int unauthorized,
      int dead,
      int retried) {}

  public record ReconciliationReport(
      long tenantId,
      String partyId,
      long outboxMaxVersion,
      long projectionVersion,
      int duplicateProjections,
      boolean consistent) {}
}
