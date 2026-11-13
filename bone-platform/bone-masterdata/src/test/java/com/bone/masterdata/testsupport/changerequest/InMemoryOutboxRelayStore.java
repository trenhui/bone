package com.bone.masterdata.testsupport.changerequest;

import com.bone.masterdata.domain.gateway.MasterDataOutboxPort;
import com.bone.masterdata.domain.gateway.OutboxRelayStorePort;
import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import com.bone.masterdata.domain.model.dispatch.OutboxDelivery;
import com.bone.masterdata.domain.model.dispatch.OutboxDelivery.State;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同时实现「生产侧 Outbox 写入端口」与「中继投递状态端口」的联调夹具。
 *
 * <p>两个端口共用一份行，才能证明「Golden Record / CR 应用产生的事件」确实能被中继消费， 而不是两套各自为政的内存列表（那就测不到真正的端到端链路）。生产实现对应同一张
 * {@code mdm_outbox} 表。
 */
public final class InMemoryOutboxRelayStore implements MasterDataOutboxPort, OutboxRelayStorePort {

  private final Map<String, OutboxDelivery> rows = new LinkedHashMap<>();

  @Override
  public void append(MasterDataRecordDispatchedEvent event) {
    if (rows.containsKey(event.idempotencyKey())) {
      throw new IllegalStateException("Outbox 幂等键冲突，生产表须有唯一索引: " + event.idempotencyKey());
    }
    rows.put(event.idempotencyKey(), OutboxDelivery.pending(event, Instant.now()));
  }

  @Override
  public boolean existsByIdempotencyKey(String idempotencyKey) {
    return rows.containsKey(idempotencyKey);
  }

  @Override
  public List<MasterDataRecordDispatchedEvent> pending() {
    List<MasterDataRecordDispatchedEvent> events = new ArrayList<>();
    rows.values().stream().filter(OutboxDelivery::isPending).forEach(d -> events.add(d.getEvent()));
    return events;
  }

  @Override
  public List<OutboxDelivery> findPending(int limit) {
    List<OutboxDelivery> result = new ArrayList<>();
    for (OutboxDelivery d : rows.values()) {
      if (d.isPending() && result.size() < limit) {
        result.add(d);
      }
    }
    return result;
  }

  @Override
  public void markDelivered(String idempotencyKey, String note) {
    OutboxDelivery d = require(key(idempotencyKey));
    rows.put(idempotencyKey, d.delivered(note, Instant.now()));
  }

  @Override
  public void recordFailure(String idempotencyKey, String reason, boolean dead) {
    OutboxDelivery d = require(key(idempotencyKey));
    // maxAttempts 传 1 即立刻转 DEAD；是否转死信由中继根据 attempts 裁决，夹具只落终态。
    OutboxDelivery next =
        d.failed(reason, dead ? d.getAttempts() + 1 : Integer.MAX_VALUE, Instant.now());
    rows.put(idempotencyKey, next);
  }

  @Override
  public List<OutboxDelivery> deadLetters() {
    return rows.values().stream().filter(OutboxDelivery::isDead).toList();
  }

  @Override
  public List<OutboxDelivery> all() {
    return new ArrayList<>(rows.values());
  }

  /** 模拟「投影已写但投递状态未回写」的崩溃重放：把已投递行重置为 PENDING。 */
  public void resetDeliveryState(String idempotencyKey) {
    OutboxDelivery d = require(key(idempotencyKey));
    rows.put(idempotencyKey, OutboxDelivery.pending(d.getEvent(), Instant.now()));
  }

  public int size() {
    return rows.size();
  }

  public void reset() {
    rows.clear();
  }

  private OutboxDelivery require(String idempotencyKey) {
    OutboxDelivery d = rows.get(idempotencyKey);
    if (d == null) {
      throw new IllegalStateException("Outbox 行不存在: " + idempotencyKey);
    }
    return d;
  }

  private static String key(String idempotencyKey) {
    return idempotencyKey;
  }

  /** 供联调断言：指定键的投递状态。 */
  public State stateOf(String idempotencyKey) {
    return require(key(idempotencyKey)).getState();
  }
}
