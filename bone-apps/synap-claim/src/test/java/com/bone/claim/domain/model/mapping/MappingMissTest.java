package com.bone.claim.domain.model.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 未命中队列的记账规则（对应 Design Doc 判据 A6–A7）。
 *
 * <p>队列最容易出的两个缺陷都不是"写不进去"，而是<b>记账语义错</b>：把 status 放进唯一键导致第三次 未命中撞键停摆；或已解决的码再次未命中时只累加次数、不标记
 * reopen。两者都不会报错，只会让运营 看到一份"看起来处理完了"的队列。
 */
class MappingMissTest {

  private static final Instant T0 = Instant.parse("2026-11-03T00:00:00Z");
  private static final Instant T1 = Instant.parse("2026-11-03T01:00:00Z");
  private static final Instant T2 = Instant.parse("2026-11-03T02:00:00Z");

  private static MappingMiss first() {
    return MappingMiss.firstSeen(
        1L, 1L, "YCBX", MasterDataDomain.HOSPITAL, "H404", "某某医院", "C-001", T0);
  }

  @Test
  @DisplayName("A6 重复未命中：同一行累加 occurCount，不新增行")
  void repeatedMissShouldAccumulate() {
    MappingMiss miss = first();
    miss.recordOccurrence(T1, "C-002");
    miss.recordOccurrence(T2, "C-003");

    assertThat(miss.getOccurCount()).isEqualTo(3);
    assertThat(miss.getStatus()).isEqualTo(MissStatus.OPEN.code());
    assertThat(miss.getLastCaseNo()).isEqualTo("C-003");
    assertThat(miss.getLastSeenAt()).isEqualTo(T2);
    assertThat(miss.getReopenCount()).isZero();
  }

  @Test
  @DisplayName("A7 已解决后再次未命中：回 OPEN、reopenCount+1、清空解决指向")
  void resolvedThenMissShouldReopen() {
    MappingMiss miss = first();
    miss.resolve(88L, T1);
    assertThat(miss.getStatus()).isEqualTo(MissStatus.RESOLVED.code());
    assertThat(miss.getResolvedMappingId()).isEqualTo(88L);

    miss.recordOccurrence(T2, "C-009");

    assertThat(miss.getStatus()).isEqualTo(MissStatus.OPEN.code());
    assertThat(miss.getReopenCount()).isEqualTo(1);
    assertThat(miss.getResolvedMappingId()).isNull();
    assertThat(miss.getOccurCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("A7b 已忽略的码再次出现：只累加次数，保持 IGNORED（不制造假回归）")
  void ignoredThenMissShouldStayIgnored() {
    MappingMiss miss = first();
    miss.ignore("HIS_LEGACY_CODE", T1);

    miss.recordOccurrence(T2, "C-010");

    assertThat(miss.getStatus()).isEqualTo(MissStatus.IGNORED.code());
    assertThat(miss.getOccurCount()).isEqualTo(2);
    assertThat(miss.getReopenCount()).isZero();
  }

  @Test
  @DisplayName("名称归一化只服务候选匹配：去空白/全半角，不做同义词替换")
  void nameNormalizationShouldBeConservative() {
    assertThat(NameNormalizer.normalize(" 北京协和医院（东院） ")).isEqualTo("北京协和医院(东院)");
    assertThat(NameNormalizer.normalize("ＡＢＣ　 Clinic")).isEqualTo("abcclinic");
    assertThat(NameNormalizer.normalize(null)).isEmpty();
  }

  @Test
  @DisplayName("拒绝把空串当标准码：映射构造期即失败（未命中只能走队列）")
  void blankStandardCodeShouldBeRejected() {
    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () ->
                    CodeMapping.of(
                        1L,
                        1L,
                        "YCBX",
                        MasterDataDomain.HOSPITAL,
                        "H001",
                        "",
                        "  ",
                        MappingType.EXACT,
                        MatchRule.MANUAL,
                        null,
                        null,
                        MappingSource.MANUAL,
                        "R1",
                        T0,
                        T0)))
        .isInstanceOf(com.bone.core.exception.DomainException.class);
  }

  @Test
  @DisplayName("候选取不到时解析返回 MISSING 而非异常（调用方据状态入队，不靠捕获异常）")
  void nullCandidatesShouldMissGracefully() {
    assertThat(CodeMappingResolver.resolve("H1", null, null, null, false).status())
        .isEqualTo(ResolutionStatus.MISSING);
    assertThat(CodeMappingResolver.resolve("H1", null, List.of(), List.of(), false).standardCode())
        .isNull();
  }
}
