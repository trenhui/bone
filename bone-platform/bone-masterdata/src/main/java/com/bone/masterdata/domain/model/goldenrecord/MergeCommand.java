package com.bone.masterdata.domain.model.goldenrecord;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一次实体解析/合并的完整输入（不可变），便于审计回放与契约测试。 */
public final class MergeCommand {

  private final String mergeId;
  private final long tenantId;
  private final String partyId;
  private final GoldenRecord before;
  private final long nextVersion;
  private final List<SourceRecord> sources;
  private final SurvivorshipPolicy policy;

  /** fieldCode -> 指定胜出的 sourceRecordId（STEWARD_OVERRIDE 用）。 */
  private final Map<String, String> stewardOverrides;

  /** 具名数据管家/操作员，用于 STEWARD_OVERRIDE 的 lineage 留痕。 */
  private final String stewardAccountId;

  private final Instant mergedAt;

  private MergeCommand(
      String mergeId,
      long tenantId,
      String partyId,
      GoldenRecord before,
      long nextVersion,
      List<SourceRecord> sources,
      SurvivorshipPolicy policy,
      Map<String, String> stewardOverrides,
      String stewardAccountId,
      Instant mergedAt) {
    this.mergeId = mergeId;
    this.tenantId = tenantId;
    this.partyId = partyId;
    this.before = before;
    this.nextVersion = nextVersion;
    this.sources = Collections.unmodifiableList(List.copyOf(sources));
    this.policy = policy;
    this.stewardOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(stewardOverrides));
    this.stewardAccountId = stewardAccountId;
    this.mergedAt = mergedAt;
  }

  public static MergeCommand of(
      String mergeId,
      long tenantId,
      String partyId,
      GoldenRecord before,
      long nextVersion,
      List<SourceRecord> sources,
      SurvivorshipPolicy policy,
      Map<String, String> stewardOverrides,
      String stewardAccountId,
      Instant mergedAt) {
    if (mergeId == null
        || mergeId.trim().isEmpty()
        || partyId == null
        || partyId.trim().isEmpty()
        || sources == null
        || policy == null
        || mergedAt == null
        || nextVersion <= 0) {
      throw new DomainException("合并命令缺少必填项");
    }
    return new MergeCommand(
        mergeId,
        tenantId,
        partyId,
        before,
        nextVersion,
        sources,
        policy,
        stewardOverrides == null ? Map.of() : stewardOverrides,
        stewardAccountId,
        mergedAt);
  }

  public String getMergeId() {
    return mergeId;
  }

  public long getTenantId() {
    return tenantId;
  }

  public String getPartyId() {
    return partyId;
  }

  public GoldenRecord getBefore() {
    return before;
  }

  public long getNextVersion() {
    return nextVersion;
  }

  public List<SourceRecord> getSources() {
    return sources;
  }

  public SurvivorshipPolicy getPolicy() {
    return policy;
  }

  public Map<String, String> getStewardOverrides() {
    return stewardOverrides;
  }

  public String getStewardAccountId() {
    return stewardAccountId;
  }

  public Instant getMergedAt() {
    return mergedAt;
  }
}
