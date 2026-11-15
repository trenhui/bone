package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.dispatch.OutboxDelivery;
import java.util.List;

/** 平台 Outbox 中继的投递状态仓储端口（终局生产实现：{@code mdm_outbox} + 唯一索引 + 状态列）。 */
public interface OutboxRelayStorePort {

  /** 按追加顺序取待投递单；DELIVERED / DEAD 不得再返回，否则会重复投影。 */
  List<OutboxDelivery> findPending(int limit);

  void markDelivered(String idempotencyKey, String note);

  /** 失败计数递增；{@code dead=true} 时转死信并保留原因。 */
  void recordFailure(String idempotencyKey, String reason, boolean dead);

  List<OutboxDelivery> deadLetters();

  /** 全量投递单，供版本戳对账与死信巡检，不参与投递决策。 */
  List<OutboxDelivery> all();
}
