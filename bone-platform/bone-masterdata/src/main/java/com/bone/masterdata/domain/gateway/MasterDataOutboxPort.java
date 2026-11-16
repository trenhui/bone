package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import java.util.List;

/**
 * 主数据分发 Outbox 出站端口（事务性发件箱）。
 *
 * <p><b>终局契约</b>：编排只负责把事件追加到发件箱，投递由平台 Outbox 中继完成；消费侧按 {@code idempotencyKey()} 去重。事件信封只携带 {@code
 * payloadHash}，不回传字段原文（PII 最小化）。
 */
public interface MasterDataOutboxPort {

  /** 追加待分发事件；同幂等键已存在时必须幂等忽略，绝不重复投递。 */
  void append(MasterDataRecordDispatchedEvent event);

  boolean existsByIdempotencyKey(String idempotencyKey);

  /** 按追加顺序返回待分发事件（供联调断言与后续中继消费）。 */
  List<MasterDataRecordDispatchedEvent> pending();
}
