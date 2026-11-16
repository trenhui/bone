package com.bone.masterdata.testsupport.goldenrecord;

import com.bone.masterdata.domain.gateway.MasterDataOutboxPort;
import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Outbox 端口的联调夹具：以幂等键做唯一索引，模拟生产表的唯一约束。 */
public final class InMemoryMasterDataOutboxAdapter implements MasterDataOutboxPort {

  private final Map<String, MasterDataRecordDispatchedEvent> rows = new LinkedHashMap<>();

  @Override
  public void append(MasterDataRecordDispatchedEvent event) {
    if (rows.containsKey(event.idempotencyKey())) {
      throw new IllegalStateException("Outbox 幂等键冲突，生产表须有唯一索引: " + event.idempotencyKey());
    }
    rows.put(event.idempotencyKey(), event);
  }

  @Override
  public boolean existsByIdempotencyKey(String idempotencyKey) {
    return rows.containsKey(idempotencyKey);
  }

  @Override
  public List<MasterDataRecordDispatchedEvent> pending() {
    return new ArrayList<>(rows.values());
  }

  public void reset() {
    rows.clear();
  }
}
