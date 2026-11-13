package com.bone.masterdata.domain.model.goldenrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.goldenrecord.FieldSurvivorshipRule.Strategy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Golden Record 共享服务层领域内核纯单测。
 *
 * <p>负向用例是本门禁的重点：税号冲突自动合并、跨租户合并、缺 lineage 出记录、 覆盖式回滚、橡皮图章式人工裁决，都必须被领域不变量拦住。
 */
class GoldenRecordMergerTest {

  private static final long TENANT = 7L;
  private static final Instant T = Instant.parse("2026-11-13T00:00:00Z");
  private final GoldenRecordMerger merger = new GoldenRecordMerger();

  private static Map<String, String> vals(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(kv[i], kv[i + 1]);
    }
    return m;
  }

  private static SourceRecord src(
      String id, String system, long tenant, int rank, Instant at, Map<String, String> values) {
    return SourceRecord.of(id, system, tenant, rank, at, values);
  }

  private static SourceRecord src(
      String id, String system, int rank, Instant at, Map<String, String> values) {
    return src(id, system, TENANT, rank, at, values);
  }

  private static MergeCommand cmd(
      String mergeId,
      GoldenRecord before,
      long version,
      List<SourceRecord> sources,
      SurvivorshipPolicy policy,
      Map<String, String> overrides,
      String steward) {
    return MergeCommand.of(
        mergeId, TENANT, "P-1", before, version, sources, policy, overrides, steward, T);
  }

  private static SurvivorshipPolicy policy(String field, Strategy strategy) {
    return SurvivorshipPolicy.of(Map.of(field, FieldSurvivorshipRule.of(field, strategy)));
  }

  @Test
  void mergeBySourcePriorityProducesCompleteFieldLineage() {
    SourceRecord erp =
        src(
            "R-1",
            "ERP",
            1,
            T,
            vals("legal_name", "甲公司", "unified_social_credit_code", "91310000A"));
    SourceRecord srm =
        src("R-2", "SRM", 5, T, vals("legal_name", "甲公司(旧)", "contact_email", "a@b.com"));

    MergeResult r =
        merger.merge(
            cmd(
                "M-1",
                null,
                1,
                List.of(erp, srm),
                SurvivorshipPolicy.of(Map.of()),
                Map.of(),
                null));

    assertTrue(r.isApplied());
    GoldenRecord g = r.getGolden();
    assertEquals("甲公司", g.getValue("legal_name"));
    assertEquals("R-1", g.getProvenance("legal_name").getSourceRecordId());
    assertEquals("ERP", g.getProvenance("legal_name").getSourceSystem());
    assertEquals("a@b.com", g.getValue("contact_email"));
    assertEquals("R-2", g.getProvenance("contact_email").getSourceRecordId());
    assertEquals(3, g.fieldCodes().size());
    assertEquals(3, r.getAudit().getDecisions().size());
    for (String f : g.fieldCodes()) {
      assertNotNull(g.getProvenance(f), "每个胜出字段必须有 lineage: " + f);
    }
  }

  @Test
  void recencyStrategyPicksNewestObservation() {
    SourceRecord older = src("R-1", "ERP", 1, T.minusSeconds(10), vals("legal_name", "旧值"));
    SourceRecord newer = src("R-2", "SRM", 9, T, vals("legal_name", "新值"));

    MergeResult r =
        merger.merge(
            cmd(
                "M-2",
                null,
                1,
                List.of(older, newer),
                policy("legal_name", Strategy.RECENCY),
                Map.of(),
                null));

    assertEquals("新值", r.getGolden().getValue("legal_name"));
    assertEquals("R-2", r.getGolden().getProvenance("legal_name").getSourceRecordId());
  }

  @Test
  void stewardOverrideWinsAndRecordsNamedOperator() {
    SourceRecord erp = src("R-1", "ERP", 1, T, vals("legal_name", "ERP 值"));
    SourceRecord srm = src("R-2", "SRM", 9, T, vals("legal_name", "管家指定值"));

    MergeResult r =
        merger.merge(
            cmd(
                "M-3",
                null,
                1,
                List.of(erp, srm),
                policy("legal_name", Strategy.STEWARD_OVERRIDE),
                Map.of("legal_name", "R-2"),
                "steward-01"));

    assertEquals("管家指定值", r.getGolden().getValue("legal_name"));
    assertEquals("steward-01", r.getGolden().getProvenance("legal_name").getStewardAccountId());
  }

  @Test
  void blankCandidateDoesNotOverrideExistingValueAndInheritsLineage() {
    GoldenRecord before =
        merger
            .merge(
                cmd(
                    "M-0",
                    null,
                    1,
                    List.of(src("R-1", "ERP", 1, T, vals("legal_name", "原值"))),
                    SurvivorshipPolicy.of(Map.of()),
                    Map.of(),
                    null))
            .getGolden();
    SourceRecord blank = src("R-9", "ERP", 1, T.plusSeconds(1), vals("legal_name", "   "));

    MergeResult r =
        merger.merge(
            cmd("M-1", before, 2, List.of(blank), SurvivorshipPolicy.of(Map.of()), Map.of(), null));

    assertEquals("原值", r.getGolden().getValue("legal_name"));
    assertEquals("R-1", r.getGolden().getProvenance("legal_name").getSourceRecordId());
    assertEquals(
        "INHERITED",
        r.getAudit().getDecisions().stream()
            .filter(d -> d.getFieldCode().equals("legal_name"))
            .findFirst()
            .orElseThrow()
            .getRuleId());
  }

  @Test
  void tieBreakIsDeterministic() {
    SourceRecord a = src("R-A", "ERP", 1, T, vals("legal_name", "A"));
    SourceRecord b = src("R-B", "SRM", 1, T, vals("legal_name", "B"));

    MergeResult r =
        merger.merge(
            cmd("M-4", null, 1, List.of(b, a), SurvivorshipPolicy.of(Map.of()), Map.of(), null));

    assertEquals("R-A", r.getGolden().getProvenance("legal_name").getSourceRecordId());
  }

  @Test
  void crossTenantSourceFailsClosed() {
    SourceRecord foreign = src("R-X", "ERP", 8L, 1, T, vals("legal_name", "别家租户"));

    assertThrows(
        DomainException.class,
        () ->
            merger.merge(
                cmd(
                    "M-5",
                    null,
                    1,
                    List.of(foreign),
                    SurvivorshipPolicy.of(Map.of()),
                    Map.of(),
                    null)));
  }

  @Test
  void conflictingSocialCreditCodeNeverAutoMerges() {
    SourceRecord a = src("R-1", "ERP", 1, T, vals("unified_social_credit_code", "91310000A"));
    SourceRecord b = src("R-2", "SRM", 2, T, vals("unified_social_credit_code", "91310000B"));

    MergeResult r =
        merger.merge(
            cmd("M-6", null, 1, List.of(a, b), SurvivorshipPolicy.of(Map.of()), Map.of(), null));

    assertEquals(MergeOutcome.MANUAL_REVIEW_REQUIRED, r.getOutcome());
    assertNull(r.getGolden(), "身份冲突不得产出任何可分发记录");
    assertNull(r.getAudit());
    assertTrue(r.getReason().contains("unified_social_credit_code"));
  }

  @Test
  void conflictingTaxIdNeverAutoMerges() {
    SourceRecord a = src("R-1", "ERP", 1, T, vals("tax_id", "A"));
    SourceRecord b = src("R-2", "SRM", 2, T, vals("tax_id", "B"));

    MergeResult r =
        merger.merge(
            cmd("M-7", null, 1, List.of(a, b), SurvivorshipPolicy.of(Map.of()), Map.of(), null));

    assertEquals(MergeOutcome.MANUAL_REVIEW_REQUIRED, r.getOutcome());
    assertNull(r.getGolden());
  }

  @Test
  void manualReviewFieldBlocksTheWholeMerge() {
    SourceRecord a = src("R-1", "ERP", 1, T, vals("legal_name", "甲公司"));

    MergeResult r =
        merger.merge(
            cmd(
                "M-8",
                null,
                1,
                List.of(a),
                policy("legal_name", Strategy.MANUAL_REVIEW),
                Map.of(),
                null));

    assertEquals(MergeOutcome.MANUAL_REVIEW_REQUIRED, r.getOutcome());
    assertNull(r.getGolden(), "不允许部分字段自动应用");
  }

  @Test
  void stewardOverrideWithoutNamedOperatorGoesToManualReview() {
    SourceRecord a = src("R-1", "ERP", 1, T, vals("legal_name", "甲公司"));

    MergeResult r =
        merger.merge(
            cmd(
                "M-9",
                null,
                1,
                List.of(a),
                policy("legal_name", Strategy.STEWARD_OVERRIDE),
                Map.of("legal_name", "R-1"),
                null));

    assertEquals(MergeOutcome.MANUAL_REVIEW_REQUIRED, r.getOutcome());
    assertNull(r.getGolden());
  }

  @Test
  void goldenRecordRejectsMissingFieldLineage() {
    assertThrows(
        DomainException.class,
        () -> GoldenRecord.of(TENANT, "P-1", 1, vals("legal_name", "甲公司"), Map.of(), T));
  }

  @Test
  void incompleteLineageIsRejectedAtConstruction() {
    assertThrows(
        DomainException.class, () -> FieldProvenance.of("legal_name", "R-1", "ERP", null, T, null));
  }

  @Test
  void beforeSnapshotOfDifferentPartyFailsClosed() {
    GoldenRecord foreign =
        GoldenRecord.of(
            8L,
            "P-2",
            1,
            vals("legal_name", "别家"),
            Map.of(
                "legal_name",
                FieldProvenance.of(
                    "legal_name", "R-1", "ERP", "SOURCE_PRIORITY:legal_name", T, null)),
            T);

    assertThrows(
        DomainException.class,
        () ->
            merger.merge(
                cmd(
                    "M-10",
                    foreign,
                    2,
                    List.of(src("R-1", "ERP", 1, T, vals("legal_name", "甲公司"))),
                    SurvivorshipPolicy.of(Map.of()),
                    Map.of(),
                    null)));
  }

  @Test
  void versionMustIncreaseMonotonically() {
    GoldenRecord before =
        merger
            .merge(
                cmd(
                    "M-0",
                    null,
                    1,
                    List.of(src("R-1", "ERP", 1, T, vals("legal_name", "甲公司"))),
                    SurvivorshipPolicy.of(Map.of()),
                    Map.of(),
                    null))
            .getGolden();

    assertThrows(
        DomainException.class,
        () ->
            merger.merge(
                cmd(
                    "M-11",
                    before,
                    1,
                    List.of(src("R-2", "SRM", 1, T.plusSeconds(1), vals("legal_name", "乙公司"))),
                    SurvivorshipPolicy.of(Map.of()),
                    Map.of(),
                    null)));
  }

  @Test
  void rollbackRestoresCompleteSnapshotIncludingLineage() {
    GoldenRecord before =
        merger
            .merge(
                cmd(
                    "M-0",
                    null,
                    1,
                    List.of(src("R-1", "ERP", 1, T, vals("legal_name", "甲公司"))),
                    SurvivorshipPolicy.of(Map.of()),
                    Map.of(),
                    null))
            .getGolden();
    MergeResult second =
        merger.merge(
            cmd(
                "M-12",
                before,
                2,
                List.of(src("R-2", "SRM", 1, T.plusSeconds(1), vals("legal_name", "误合并值"))),
                SurvivorshipPolicy.of(Map.of()),
                Map.of(),
                null));

    Optional<GoldenRecord> restored =
        merger.rollback(second.getAudit(), second.getGolden(), "误合并", "ops-01", T.plusSeconds(2));

    assertTrue(restored.isPresent());
    assertEquals("甲公司", restored.get().getValue("legal_name"));
    assertEquals("R-1", restored.get().getProvenance("legal_name").getSourceRecordId());
    assertEquals("ops-01", second.getAudit().getRolledBackBy());
    assertTrue(second.getAudit().isRolledBack());
  }

  @Test
  void rollbackRejectsConcurrentOverwrite() {
    MergeResult first =
        merger.merge(
            cmd(
                "M-13",
                null,
                1,
                List.of(src("R-1", "ERP", 1, T, vals("legal_name", "甲公司"))),
                SurvivorshipPolicy.of(Map.of()),
                Map.of(),
                null));

    assertThrows(
        DomainException.class, () -> merger.rollback(null, first.getGolden(), "误合并", "ops-01", T));

    assertThrows(
        DomainException.class,
        () ->
            merger.rollback(
                first.getAudit(),
                GoldenRecord.of(
                    TENANT,
                    "P-1",
                    99,
                    vals("legal_name", "并发写入"),
                    Map.of(
                        "legal_name",
                        FieldProvenance.of("legal_name", "R-9", "ERP", "RULE", T, null)),
                    T),
                "误合并",
                "ops-01",
                T.plusSeconds(2)));
  }

  @Test
  void rollbackTwiceIsRejected() {
    MergeResult r =
        merger.merge(
            cmd(
                "M-14",
                null,
                1,
                List.of(src("R-1", "ERP", 1, T, vals("legal_name", "甲公司"))),
                SurvivorshipPolicy.of(Map.of()),
                Map.of(),
                null));

    Optional<GoldenRecord> restored =
        merger.rollback(r.getAudit(), r.getGolden(), "撤销首建", "ops-01", T.plusSeconds(1));
    assertFalse(restored.isPresent(), "首次建记录撤销后回到「无」");
    assertThrows(
        DomainException.class,
        () -> merger.rollback(r.getAudit(), r.getGolden(), "重复", "ops-01", T.plusSeconds(2)));
  }
}
