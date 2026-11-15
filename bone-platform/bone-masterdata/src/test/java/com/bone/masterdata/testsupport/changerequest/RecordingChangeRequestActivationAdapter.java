package com.bone.masterdata.testsupport.changerequest;

import com.bone.masterdata.domain.gateway.ChangeRequestActivationPort;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest;
import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Active 区发布夹具：只有收到 {@code APPLIED} 状态的 CR 才记录激活并追加一条版本化 Outbox 事件。
 *
 * <p>这使「CR 应用 → Outbox 事件 → 中继 → 消费侧投影」成为可联调的真实链路；生产 adapter 必须在同一事务内完成 Active 写入 + 事件追加，语义与本夹具一致。
 */
public final class RecordingChangeRequestActivationAdapter implements ChangeRequestActivationPort {

  private static final String HASH = "c".repeat(64); // 64 位小写十六进制，形状等价 HMAC-SHA256 输出

  private final InMemoryOutboxRelayStore outbox;
  private final List<ChangeRequest> activations = new ArrayList<>();

  public RecordingChangeRequestActivationAdapter(InMemoryOutboxRelayStore outbox) {
    this.outbox = outbox;
  }

  @Override
  public void activate(ChangeRequest applied) {
    if (applied.getStatus() != ChangeRequest.Status.APPLIED) {
      throw new IllegalStateException("禁止激活未审批通过的变更请求: " + applied.getStatus());
    }
    MasterDataRecordDispatchedEvent event =
        MasterDataRecordDispatchedEvent.of(
            "EVT-CR-" + applied.getChangeRequestId(),
            applied.getTenantId(),
            applied.getPartyId(),
            applied.getTargetVersion(),
            "CHANGE_REQUEST_APPLIED",
            1,
            applied.getAppliedAt() == null ? Instant.now() : applied.getAppliedAt(),
            HASH);
    outbox.append(event);
    activations.add(applied);
  }

  public int activationCount() {
    return activations.size();
  }

  public void reset() {
    activations.clear();
  }
}
