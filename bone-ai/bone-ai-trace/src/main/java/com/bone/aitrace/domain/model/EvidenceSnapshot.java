package com.bone.aitrace.domain.model;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 证据快照：一次 LLM 调用的不可篡改留痕。
 *
 * <p>承载合规「三元组」——输入哈希（{@code inputHash}，防篡改校验）+ 中间表示（{@code payload}，请求/响应 IR）+ 引擎版本（{@code
 * engineVersion}，回放复现）。按租户隔离，软删可恢复（证据不物理消失）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("ai_trace_snapshot")
public class EvidenceSnapshot extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "trace_id")
  private String traceId;

  @Column(name = "evidence_type")
  private String evidenceType;

  @Column(name = "input_hash")
  private String inputHash;

  @Column(name = "engine_version")
  private String engineVersion;

  private String payload;

  @Deleted private Boolean deleted = false;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public static EvidenceSnapshot record(
      Long id,
      Long tenantId,
      String traceId,
      EvidenceType type,
      String inputHash,
      String engineVersion,
      String payload) {
    EvidenceSnapshot snapshot = new EvidenceSnapshot();
    snapshot.id = id;
    snapshot.setTenantId(tenantId);
    snapshot.traceId = traceId;
    snapshot.evidenceType = type.name();
    snapshot.inputHash = inputHash;
    snapshot.engineVersion = engineVersion;
    snapshot.payload = payload;
    LocalDateTime now = LocalDateTime.now();
    snapshot.createdAt = now;
    snapshot.updatedAt = now;
    return snapshot;
  }

  public EvidenceType type() {
    return EvidenceType.valueOf(evidenceType);
  }
}
