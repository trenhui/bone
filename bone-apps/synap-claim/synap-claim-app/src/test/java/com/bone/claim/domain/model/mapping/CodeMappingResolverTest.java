package com.bone.claim.domain.model.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 解析内核的验收用例（对应 Comet Design Doc §验收判据 A1–A5）。
 *
 * <p>每条都配了"反向钉子"：只断言"未命中要拒绝"会诱导后人把实现改成"永远拒绝"也全绿， 所以必须同时有"存在且唯一时必须命中"的对照。
 */
class CodeMappingResolverTest {

  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
  private static final Long TENANT = 1L;

  private static CodeMapping mapping(String rawCode, String key, String standardCode, String id) {
    return CodeMapping.of(
        Long.parseLong(id),
        TENANT,
        "YCBX",
        MasterDataDomain.HOSPITAL,
        rawCode,
        key,
        standardCode,
        MappingType.EXACT,
        MatchRule.MANUAL,
        null,
        null,
        MappingSource.MANUAL,
        "R1",
        NOW,
        NOW);
  }

  private static StdCodeAlias alias(String from, String to) {
    return StdCodeAlias.of(
        Long.parseLong("9" + Math.abs(from.hashCode() % 1000)),
        TENANT,
        MasterDataDomain.HOSPITAL,
        from,
        to,
        AliasRelation.MERGE,
        NOW);
  }

  @Test
  @DisplayName("A1 映射存在且唯一 ⇒ 命中并返回标准码")
  void uniqueMappingShouldResolve() {
    MappingResolution result =
        CodeMappingResolver.resolve(
            "H001", null, List.of(mapping("H001", "", "STD-H-1", "1")), List.of(), false);

    assertThat(result.status()).isEqualTo(ResolutionStatus.RESOLVED);
    assertThat(result.standardCode()).isEqualTo("STD-H-1");
    assertThat(result.mappingId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("A2 无映射 ⇒ MISSING，且不得回填原始码或空串")
  void missingMappingShouldNotFallback() {
    MappingResolution result =
        CodeMappingResolver.resolve("H404", null, List.of(), List.of(), false);

    assertThat(result.status()).isEqualTo(ResolutionStatus.MISSING);
    assertThat(result.standardCode()).isNull();
    assertThat(result.reasonCode()).isEqualTo(MappingResolution.NO_ACTIVE_MAPPING);
  }

  @Test
  @DisplayName("A2b 空码 ⇒ MISSING（空串永远不是合法编码）")
  void blankCodeShouldMiss() {
    MappingResolution result = CodeMappingResolver.resolve("  ", null, List.of(), List.of(), false);

    assertThat(result.status()).isEqualTo(ResolutionStatus.MISSING);
    assertThat(result.reasonCode()).isEqualTo(MappingResolution.RAW_CODE_BLANK);
  }

  @Test
  @DisplayName("A3 同码两条 ACTIVE：键匹配则命中，键不匹配则拒绝，未给键则判多义")
  void disambiguationKeyShouldDecide() {
    List<CodeMapping> candidates =
        List.of(
            mapping("D001", "剂型=注射剂", "STD-INJ", "11"), mapping("D001", "剂型=片剂", "STD-TAB", "12"));

    MappingResolution byKey =
        CodeMappingResolver.resolve("D001", "剂型=片剂", candidates, List.of(), false);
    assertThat(byKey.status()).isEqualTo(ResolutionStatus.RESOLVED);
    assertThat(byKey.standardCode()).isEqualTo("STD-TAB");

    MappingResolution noKey =
        CodeMappingResolver.resolve("D001", null, candidates, List.of(), false);
    assertThat(noKey.status()).isEqualTo(ResolutionStatus.AMBIGUOUS);
    assertThat(noKey.standardCode()).isNull();

    MappingResolution wrongKey =
        CodeMappingResolver.resolve("D001", "剂型=胶囊", candidates, List.of(), false);
    assertThat(wrongKey.status()).isEqualTo(ResolutionStatus.MISSING);
    assertThat(wrongKey.reasonCode()).isEqualTo(MappingResolution.DISAMBIGUATION_NOT_MATCHED);
  }

  @Test
  @DisplayName("A4 标准码被 MERGE 后，旧映射仍解析到新码（多跳）")
  void mergedStandardCodeShouldRedirect() {
    List<StdCodeAlias> aliases = List.of(alias("STD-H-1", "STD-H-2"), alias("STD-H-2", "STD-H-3"));

    MappingResolution result =
        CodeMappingResolver.resolve(
            "H001", null, List.of(mapping("H001", "", "STD-H-1", "1")), aliases, false);

    assertThat(result.status()).isEqualTo(ResolutionStatus.RESOLVED);
    assertThat(result.standardCode()).isEqualTo("STD-H-3");
  }

  @Test
  @DisplayName("A4b 别名链成环 ⇒ ALIAS_CYCLE，不返回任何码")
  void aliasCycleShouldFailClosed() {
    List<StdCodeAlias> aliases = List.of(alias("STD-A", "STD-B"), alias("STD-B", "STD-A"));

    MappingResolution result =
        CodeMappingResolver.resolve(
            "H001", null, List.of(mapping("H001", "", "STD-A", "1")), aliases, false);

    assertThat(result.status()).isEqualTo(ResolutionStatus.ALIAS_CYCLE);
    assertThat(result.standardCode()).isNull();
  }

  @Test
  @DisplayName("A5 命中拆分父码 ⇒ SPLIT_REQUIRES_MANUAL，不得任选子码")
  void splitParentShouldRequireManual() {
    MappingResolution result =
        CodeMappingResolver.resolve(
            "H001", null, List.of(mapping("H001", "", "STD-PARENT", "1")), List.of(), true);

    assertThat(result.status()).isEqualTo(ResolutionStatus.SPLIT_REQUIRES_MANUAL);
    assertThat(result.standardCode()).isNull();
  }

  @Test
  @DisplayName("已关闭的映射不参与解析（停用即失效）")
  void closedMappingShouldBeIgnored() {
    CodeMapping closed = mapping("H001", "", "STD-H-1", "1");
    closed.close(NOW, "INSURER_CHANGED_CODE");

    MappingResolution result =
        CodeMappingResolver.resolve("H001", null, List.of(closed), List.of(), false);

    assertThat(result.status()).isEqualTo(ResolutionStatus.MISSING);
  }

  @Test
  @DisplayName("N:1 归并：两条保司码都解析到同一标准码")
  void manyToOneMergeShouldResolve() {
    List<CodeMapping> candidates =
        List.of(mapping("H-A", "", "STD-SAME", "21"), mapping("H-B", "", "STD-SAME", "22"));

    assertThat(
            CodeMappingResolver.resolve("H-A", null, candidates, List.of(), false).standardCode())
        .isEqualTo("STD-SAME");
    assertThat(
            CodeMappingResolver.resolve("H-B", null, candidates, List.of(), false).standardCode())
        .isEqualTo("STD-SAME");
  }

  @Test
  @DisplayName("构造期约束：RESOLVED 必须带码，非 RESOLVED 不得带码")
  void resolutionInvariants() {
    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () -> new MappingResolution(ResolutionStatus.MISSING, "", null, "X")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () -> new MappingResolution(ResolutionStatus.RESOLVED, null, 1L, "")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("匹配得分不影响裁决：分值只是证据，不是闸门")
  void scoreShouldNotOverrideKeyMatch() {
    CodeMapping withScore =
        CodeMapping.of(
            31L,
            TENANT,
            "YCBX",
            MasterDataDomain.HOSPITAL,
            "H001",
            "",
            "STD-H-1",
            MappingType.EXACT,
            MatchRule.FUZZY,
            new BigDecimal("61.00"),
            null,
            MappingSource.SUGGEST_CONFIRMED,
            "R1",
            NOW,
            NOW);

    assertThat(
            CodeMappingResolver.resolve("H001", null, List.of(withScore), List.of(), false)
                .standardCode())
        .isEqualTo("STD-H-1");
  }
}
