package com.bone.claim.domain.model.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.evidence.EvidenceDateParser.TimelineVerdict;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 时间轴日期解析与矛盾判定（R-FRAUD-03 的前置）。
 *
 * <p>重点不在「能解析」，而在「**不该解析的不解析**」：白名单之外一律返回空，由调用方转人工。 因此每个负例都是一枚钉子，防止后续为了「提高解析率」把宽松格式悄悄放进来——那会把正常案件
 * 判成「药先开票」。
 */
class EvidenceDateParserTest {

  private static final LocalDate VISIT = LocalDate.of(2026, 3, 10);

  @Test
  @DisplayName("白名单格式：yyyy-MM-dd 与 yyyy/MM/dd 可解析，前后空白被裁剪")
  void parsesWhitelistedFormats() {
    assertThat(EvidenceDateParser.parse("2026-03-10")).contains(VISIT);
    assertThat(EvidenceDateParser.parse("2026/03/10")).contains(VISIT);
    assertThat(EvidenceDateParser.parse("  2026-03-10  ")).contains(VISIT);
  }

  @Test
  @DisplayName("非白名单格式一律不解析：不补零 / 纯数字 / 日月年 / 中文 / 空")
  void refusesAmbiguousOrUnlistedFormats() {
    assertThat(EvidenceDateParser.parse("2026-3-10")).isEmpty();
    assertThat(EvidenceDateParser.parse("20260310")).isEmpty();
    assertThat(EvidenceDateParser.parse("10/03/2026")).isEmpty();
    assertThat(EvidenceDateParser.parse("二〇二六年三月十日")).isEmpty();
    assertThat(EvidenceDateParser.parse("2026-03-10 12:00")).isEmpty();
    assertThat(EvidenceDateParser.parse("")).isEmpty();
    assertThat(EvidenceDateParser.parse("   ")).isEmpty();
    assertThat(EvidenceDateParser.parse(null)).isEmpty();
  }

  @Test
  @DisplayName("发票日期早于就诊日 ⇒ 药先开票（不设容忍天数）")
  void invoiceBeforeVisitIsConflict() {
    assertThat(EvidenceDateParser.evaluate(VISIT.minusDays(1), VISIT, 30))
        .isEqualTo(TimelineVerdict.INVOICE_BEFORE_VISIT);
    // 即使只早 1 天也要报：真实业务里「先缴费后就诊」与就诊同日，不存在提前
    assertThat(EvidenceDateParser.evaluate(VISIT.minusYears(1), VISIT, 30))
        .isEqualTo(TimelineVerdict.INVOICE_BEFORE_VISIT);
  }

  @Test
  @DisplayName("同日与容忍窗口内的晚开票 ⇒ 一致；超出窗口 ⇒ 事后补票")
  void lateInvoiceWithinToleranceIsConsistent() {
    assertThat(EvidenceDateParser.evaluate(VISIT, VISIT, 30)).isEqualTo(TimelineVerdict.CONSISTENT);
    assertThat(EvidenceDateParser.evaluate(VISIT.plusDays(30), VISIT, 30))
        .isEqualTo(TimelineVerdict.CONSISTENT);
    assertThat(EvidenceDateParser.evaluate(VISIT.plusDays(31), VISIT, 30))
        .isEqualTo(TimelineVerdict.INVOICE_TOO_LATE);
  }

  @Test
  @DisplayName("容忍天数传负数按 0 处理：要求同日，而不是把「晚一天」当一致")
  void negativeToleranceIsClampedToZero() {
    assertThat(EvidenceDateParser.evaluate(VISIT.plusDays(1), VISIT, -5))
        .isEqualTo(TimelineVerdict.INVOICE_TOO_LATE);
  }

  @Test
  @DisplayName("任一日期缺失 ⇒ UNRESOLVED（转人工，不猜）")
  void missingDateIsUnresolved() {
    assertThat(EvidenceDateParser.evaluate(null, VISIT, 30)).isEqualTo(TimelineVerdict.UNRESOLVED);
    assertThat(EvidenceDateParser.evaluate(VISIT, null, 30)).isEqualTo(TimelineVerdict.UNRESOLVED);
    assertThat(EvidenceDateParser.evaluate(null, null, 30)).isEqualTo(TimelineVerdict.UNRESOLVED);
  }
}
