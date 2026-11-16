package com.bone.masterdata.domain.model.goldenrecord;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.goldenrecord.FieldSurvivorshipRule.Strategy;
import com.bone.masterdata.domain.model.goldenrecord.MergeAudit.MergeDecision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Golden Record 共享服务层的领域内核（平台侧，供所有主数据域复用）。
 *
 * <p>以终为始：本类实现的是终局也存在的「共享实体解析 + survivorship + 字段级 lineage + 误合并整笔回滚」，不是域内临时替代品。业务域只消费 partyId 与
 * General data， 不得另建 GoldenRecordService/DedupService。
 *
 * <p>五条硬不变量（任一失败都不产出可分发记录）：
 *
 * <ol>
 *   <li>跨租户候选源记录：直接 fail-closed 抛异常，不静默丢弃；
 *   <li>统一社会信用代码/税号冲突：转人工，禁止自动合并（防错并主体）；
 *   <li>每个胜出字段必须有完整字段级 lineage，缺一条整笔拒绝；
 *   <li>MANUAL_REVIEW / STEWARD_OVERRIDE 无具名裁决：整笔转人工，不部分应用；
 *   <li>回滚只能恢复到合并前完整快照，且当前版本必须等于审计的 after 版本（防覆盖并发写入）。
 * </ol>
 */
public final class GoldenRecordMerger {

  /** 身份主键类字段：不同值一律禁止自动合并（终局裁决「税号冲突不得自动合并」）。 */
  private static final Set<String> IDENTITY_FIELDS = Set.of("unified_social_credit_code", "tax_id");

  public MergeResult merge(MergeCommand cmd) {
    List<SourceRecord> sources = cmd.getSources();
    if (sources.isEmpty()) {
      return MergeResult.rejected("无候选源记录");
    }
    for (SourceRecord s : sources) {
      if (s.getTenantId() != cmd.getTenantId()) {
        throw new DomainException("跨租户候选源记录禁止合并: " + s.getRecordId());
      }
    }

    GoldenRecord before = cmd.getBefore();
    if (before != null) {
      if (before.getTenantId() != cmd.getTenantId()
          || !before.getPartyId().equals(cmd.getPartyId())) {
        throw new DomainException("合并前快照与目标身份不一致");
      }
      if (cmd.getNextVersion() <= before.getVersion()) {
        throw new DomainException("Golden Record 版本必须单调递增");
      }
    }

    for (String identityField : IDENTITY_FIELDS) {
      Set<String> distinct = new LinkedHashSet<>();
      for (SourceRecord s : sources) {
        s.valueOf(identityField).ifPresent(distinct::add);
      }
      if (before != null && before.getValue(identityField) != null) {
        distinct.add(before.getValue(identityField));
      }
      if (distinct.size() > 1) {
        return MergeResult.manualReview("身份字段冲突，禁止自动合并: " + identityField);
      }
    }

    Set<String> fields = new LinkedHashSet<>();
    for (SourceRecord s : sources) {
      fields.addAll(s.getValues().keySet());
    }
    if (before != null) {
      fields.addAll(before.fieldCodes());
    }

    Map<String, String> values = new LinkedHashMap<>();
    Map<String, FieldProvenance> lineage = new LinkedHashMap<>();
    List<MergeDecision> decisions = new ArrayList<>();

    for (String field : fields) {
      FieldSurvivorshipRule rule = cmd.getPolicy().ruleFor(field);
      if (rule.getStrategy() == Strategy.MANUAL_REVIEW) {
        return MergeResult.manualReview("字段要求人工裁决: " + field);
      }

      SourceRecord winner;
      String stewardAccountId = null;
      if (rule.getStrategy() == Strategy.STEWARD_OVERRIDE) {
        String overrideRecordId = cmd.getStewardOverrides().get(field);
        if (overrideRecordId == null || cmd.getStewardAccountId() == null) {
          return MergeResult.manualReview("具名裁决缺失，字段转人工: " + field);
        }
        winner =
            sources.stream()
                .filter(
                    s -> s.getRecordId().equals(overrideRecordId) && s.valueOf(field).isPresent())
                .findFirst()
                .orElse(null);
        if (winner == null) {
          return MergeResult.manualReview("具名裁决指向的源记录无有效值: " + field);
        }
        stewardAccountId = cmd.getStewardAccountId();
      } else {
        List<SourceRecord> candidates =
            sources.stream().filter(s -> s.valueOf(field).isPresent()).toList();
        winner = choose(candidates, rule.getStrategy());
      }

      if (winner == null) {
        if (before != null && before.getValue(field) != null) {
          FieldProvenance inherited = before.getProvenance(field);
          values.put(field, before.getValue(field));
          lineage.put(field, FieldProvenance.inherit(inherited, field));
          decisions.add(
              MergeDecision.of(field, inherited.getSourceRecordId(), "INHERITED", "无候选值，沿用合并前快照"));
        }
        continue;
      }

      values.put(field, winner.valueOf(field).orElseThrow());
      lineage.put(
          field,
          FieldProvenance.of(
              field,
              winner.getRecordId(),
              winner.getSourceSystem(),
              rule.ruleId(),
              winner.getObservedAt(),
              stewardAccountId));
      decisions.add(
          MergeDecision.of(field, winner.getRecordId(), rule.ruleId(), rule.getStrategy().name()));
    }

    // GoldenRecord.of 会逐字段校验 lineage 完整性，缺任一条即整笔拒绝。
    GoldenRecord after =
        GoldenRecord.of(
            cmd.getTenantId(),
            cmd.getPartyId(),
            cmd.getNextVersion(),
            values,
            lineage,
            cmd.getMergedAt());
    MergeAudit audit =
        MergeAudit.of(
            cmd.getMergeId(),
            cmd.getTenantId(),
            cmd.getPartyId(),
            before,
            after,
            decisions,
            cmd.getMergedAt());
    return MergeResult.applied(after, audit);
  }

  /** 整笔撤销误合并。返回合并前完整快照（含 lineage）；首次建记录时返回 empty，表示撤销到「无」。 当前版本与审计 after 不一致时拒绝回滚，避免覆盖并发写入。 */
  public Optional<GoldenRecord> rollback(
      MergeAudit audit, GoldenRecord current, String reason, String operator, Instant at) {
    if (audit == null || current == null) {
      throw new DomainException("回滚需要合并审计与当前快照");
    }
    GoldenRecord after = audit.getAfter();
    if (after.getVersion() != current.getVersion()
        || !after.getPartyId().equals(current.getPartyId())
        || after.getTenantId() != current.getTenantId()) {
      throw new DomainException("当前 Golden Record 已变更，禁止覆盖式回滚");
    }
    if (at == null) {
      throw new DomainException("回滚必须记录时间");
    }
    return audit.rollback(reason, operator, at);
  }

  /** 确定性选源：策略主键 + 可信度 + 观测时间 + recordId 字典序兜底，避免同分随机。 */
  private SourceRecord choose(List<SourceRecord> candidates, Strategy strategy) {
    if (candidates.isEmpty()) {
      return null;
    }
    return candidates.stream()
        .min(
            (a, b) -> {
              if (strategy == Strategy.RECENCY) {
                int byTime = b.getObservedAt().compareTo(a.getObservedAt());
                if (byTime != 0) {
                  return byTime;
                }
              }
              int byTrust = Integer.compare(a.getTrustRank(), b.getTrustRank());
              if (byTrust != 0) {
                return byTrust;
              }
              int byObserved = b.getObservedAt().compareTo(a.getObservedAt());
              if (byObserved != 0) {
                return byObserved;
              }
              return a.getRecordId().compareTo(b.getRecordId());
            })
        .orElse(null);
  }
}
