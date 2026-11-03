package com.bone.claim.domain.model.mapping;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 收单未命中队列（{@code t_claim_mapping_miss}）。
 *
 * <p><b>一码一行、状态流转</b>：唯一键是 {@code (租户, 保司, 域, 原始码)}，<b>不含 status</b>。若把 status
 * 放进唯一键，OPEN/RESOLVED/IGNORED 各占一行后，第三次未命中就撞键，队列再也记不下新的 miss—— 这是"队列静默停摆"的典型成因（已在 MySQL 8 实测复现）。
 *
 * <p><b>{@code reopenCount} 比新增未命中更危险</b>：它意味着"以为修好了，其实没修好"（映射被停用、 或标准码被合并后映射没跟上）。因此它单独告警，而不是混在
 * occurCount 里被平均掉。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_mapping_miss")
public class MappingMiss extends TenantAggregateRoot<Long> {

  @Column(name = "insurer_code")
  private String insurerCode;

  @Column(name = "domain")
  private String domain;

  @Column(name = "raw_code")
  private String rawCode;

  @Column(name = "raw_name")
  private String rawName = "";

  @Column(name = "name_normalized")
  private String nameNormalized = "";

  @Column(name = "occur_count")
  private Integer occurCount = 1;

  @Column(name = "reopen_count")
  private Integer reopenCount = 0;

  @Column(name = "last_case_no")
  private String lastCaseNo = "";

  @Column(name = "first_seen_at")
  private Instant firstSeenAt;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Column(name = "status")
  private String status = MissStatus.OPEN.code();

  @Column(name = "ignore_reason")
  private String ignoreReason = "";

  @Column(name = "resolved_mapping_id")
  private Long resolvedMappingId;

  /** 处置操作人（RESOLVED/IGNORED 时留痕；OPEN 为空串）。 */
  @Column(name = "resolved_by")
  private String resolvedBy = "";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static MappingMiss firstSeen(
      Long id,
      Long tenantId,
      String insurerCode,
      MasterDataDomain domain,
      String rawCode,
      String rawName,
      String caseNo,
      Instant now) {
    MappingMiss e = new MappingMiss();
    e.setId(id);
    e.setTenantId(tenantId);
    e.insurerCode = insurerCode;
    e.domain = domain.code();
    e.rawCode = rawCode;
    e.rawName = rawName == null ? "" : rawName.trim();
    e.nameNormalized = NameNormalizer.normalize(rawName);
    e.occurCount = 1;
    e.reopenCount = 0;
    e.lastCaseNo = caseNo == null ? "" : caseNo;
    e.firstSeenAt = now;
    e.lastSeenAt = now;
    e.status = MissStatus.OPEN.code();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  /**
   * 再次未命中。
   *
   * <p>三态语义刻意不同：
   *
   * <ul>
   *   <li>OPEN：只累加出现次数（同一问题持续发生，不需要新信号）。
   *   <li>RESOLVED：<b>转回 OPEN 且 reopenCount+1</b>——已修好的映射又坏了，这是最需要告警的状态。
   *   <li>IGNORED：只累加次数、保持 IGNORED——运营已明确判定"这个码不用映射"，再次出现不构成回归。
   * </ul>
   */
  public void recordOccurrence(Instant now, String caseNo) {
    this.occurCount = (occurCount == null ? 0 : occurCount) + 1;
    this.lastSeenAt = now;
    if (caseNo != null && !caseNo.isBlank()) {
      this.lastCaseNo = caseNo;
    }
    if (MissStatus.RESOLVED.code().equals(status)) {
      this.status = MissStatus.OPEN.code();
      this.reopenCount = (reopenCount == null ? 0 : reopenCount) + 1;
      this.resolvedMappingId = null;
    }
    this.updatedAt = now;
  }

  public void resolve(Long mappingId, String operatorId, Instant now) {
    if (mappingId == null) {
      throw new IllegalArgumentException("解决未命中必须指向具体映射");
    }
    this.status = MissStatus.RESOLVED.code();
    this.resolvedMappingId = mappingId;
    this.resolvedBy = operatorId == null ? "" : operatorId.trim();
    this.ignoreReason = "";
    this.updatedAt = now;
  }

  public void ignore(String reason, String operatorId, Instant now) {
    this.status = MissStatus.IGNORED.code();
    this.ignoreReason = reason == null ? "" : reason.trim();
    this.resolvedBy = operatorId == null ? "" : operatorId.trim();
    this.updatedAt = now;
  }

  public boolean isOpen() {
    return MissStatus.OPEN.code().equals(status);
  }
}
