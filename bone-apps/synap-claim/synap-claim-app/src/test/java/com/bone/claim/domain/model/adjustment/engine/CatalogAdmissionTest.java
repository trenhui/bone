package com.bone.claim.domain.model.adjustment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 目录准入结论的<b>装载期纪律</b>（缺口 ① / 适用限定「药品·诊疗」）。
 *
 * <p>这里守的是一类特定的错误：<b>配了不生效</b>。它比"没配"危险得多——没配至少写在缺口清单里，
 * 而"配了不生效"在配置页、评审和代码里都显示"已支持"，只有出险赔钱时才暴露，且方向是超赔。
 */
@DisplayName("目录准入：半配置一律拒绝，判不出来一律 UNKNOWN")
class CatalogAdmissionTest {

  private static final LocalDate DAY = LocalDate.parse("2026-10-01");

  @Test
  @DisplayName("配了目录编码但策略 NONE：拒绝（编码永远不会被查询）")
  void codeWithoutPolicyRejected() {
    assertThatThrownBy(
            () -> new CatalogAdmission(CatalogAdmission.Mode.NONE, "NATIONAL_2024", null, Map.of()))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("INVALID_INPUT")
        .hasMessageContaining("永远不会被查询");
  }

  @Test
  @DisplayName("开了策略但没配目录编码：拒绝（不知道该查哪一版，查任何一版都是猜）")
  void policyWithoutCodeRejected() {
    assertThatThrownBy(
            () ->
                CatalogAdmission.of(
                    "MANDATORY", "", DAY, Map.of(1L, CatalogAdmission.Verdict.COVERED)))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("INVALID_INPUT")
        .hasMessageContaining("未配目录编码");
  }

  @Test
  @DisplayName("开了策略但判不出基准日：CATALOG_UNDETERMINED（目录有生效期，不能用今天的版本算历史票）")
  void missingAsOfRejected() {
    assertThatThrownBy(
            () ->
                CatalogAdmission.of(
                    "MANDATORY",
                    "NATIONAL_2024",
                    null,
                    Map.of(1L, CatalogAdmission.Verdict.COVERED)))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("CATALOG_UNDETERMINED");
  }

  @Test
  @DisplayName("开了策略但一条结论都没有：CATALOG_UNRESOLVED（端口未接线 / 该版本为空表）")
  void emptyVerdictsRejected() {
    assertThatThrownBy(() -> CatalogAdmission.of("MANDATORY", "NATIONAL_2024", DAY, Map.of()))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("CATALOG_UNRESOLVED");
  }

  @Test
  @DisplayName("无法识别的策略：fail-closed，不回落成 NONE（写错一个字母就让准入静默失效）")
  void unknownPolicyRejected() {
    assertThatThrownBy(() -> CatalogAdmission.Mode.fromCode("MANDATROY"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("INVALID_INPUT");
  }

  @Test
  @DisplayName("策略 NONE 时结论被清空：不留「传了就该生效」的隐式路径")
  void inactiveClearsVerdicts() {
    CatalogAdmission admission =
        new CatalogAdmission(
            CatalogAdmission.Mode.NONE, "", null, Map.of(1L, CatalogAdmission.Verdict.COVERED));

    assertThat(admission.active()).isFalse();
    assertThat(admission.verdicts()).isEmpty();
    assertThat(admission.verdictOf(1L)).isEqualTo(CatalogAdmission.Verdict.UNKNOWN);
  }

  @Test
  @DisplayName("缺条目一律 UNKNOWN：没有结论不等于在目录里")
  void missingEntryIsUnknown() {
    CatalogAdmission admission =
        new CatalogAdmission(
            CatalogAdmission.Mode.MANDATORY,
            "NATIONAL_2024",
            DAY,
            Map.of(1L, CatalogAdmission.Verdict.COVERED));

    assertThat(admission.verdictOf(2L)).isEqualTo(CatalogAdmission.Verdict.UNKNOWN);
    assertThat(admission.verdictOf(null)).isEqualTo(CatalogAdmission.Verdict.UNKNOWN);
    assertThat(admission.excludedCount()).isZero();
  }

  @Test
  @DisplayName("策略为空串按 NONE 处理（缺省即不查目录，与历史行为一致）")
  void blankPolicyIsNone() {
    assertThat(CatalogAdmission.Mode.fromCode(" ")).isEqualTo(CatalogAdmission.Mode.NONE);
    assertThat(CatalogAdmission.Mode.fromCode(null)).isEqualTo(CatalogAdmission.Mode.NONE);
  }

  @Test
  @DisplayName("策略大小写不敏感：配置岗录成 mandatory 也要认，不能因大小写整条失效")
  void policyIsCaseInsensitive() {
    assertThat(CatalogAdmission.Mode.fromCode("mandatory"))
        .isEqualTo(CatalogAdmission.Mode.MANDATORY);
  }
}
