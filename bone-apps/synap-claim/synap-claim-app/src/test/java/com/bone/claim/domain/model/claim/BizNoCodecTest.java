package com.bone.claim.domain.model.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 业务号编解码内核单测。
 *
 * <p>与 {@link ClaimNoGeneratorTest} 的分工：那里验「赔案号这一套号」的格式与并发，这里验<b>两个命名空间共用同一内核时必须成立的性质</b>——
 * 尤其是「校验位只覆盖日期+载荷、不含前缀」这条，它是存量 {@code CL→RP} 回填能在纯 SQL 里完成的前提。 若哪天有人「顺手」把前缀纳入校验，
 * 回填逻辑会静默失效（号码存进去但读出来校验不过），本类会先红。
 */
class BizNoCodecTest {

  private static final String CL = ClaimNoGenerator.PREFIX;
  private static final String RP = ReportNoGenerator.PREFIX;
  private static final LocalDate FIXED_DATE = LocalDate.of(2026, 11, 22);

  private static final long SAMPLE_ID = 76102367333449728L;

  @Nested
  @DisplayName("格式契约")
  class Format {

    @Test
    @DisplayName("两套号同构：都是 24 字符，前缀不同、其余位结构相同")
    void sameShapeDifferentPrefix() {
      String claimNo = BizNoCodec.generate(CL, SAMPLE_ID, FIXED_DATE);
      String reportNo = BizNoCodec.generate(RP, SAMPLE_ID, FIXED_DATE);

      assertThat(claimNo).hasSize(BizNoCodec.TOTAL_LEN).startsWith(CL);
      assertThat(reportNo).hasSize(BizNoCodec.TOTAL_LEN).startsWith(RP);
      assertThat(BizNoCodec.TOTAL_LEN).isEqualTo(24);
      assertThat(claimNo.substring(2)).as("除前缀外两串必须完全一致").isEqualTo(reportNo.substring(2));
    }

    @Test
    @DisplayName("前缀不参与校验位：同一雪花 ID 在两前缀下的校验位相同")
    void checksumIgnoresPrefix() {
      String claimNo = BizNoCodec.generate(CL, SAMPLE_ID, FIXED_DATE);
      String reportNo = BizNoCodec.generate(RP, SAMPLE_ID, FIXED_DATE);

      assertThat(claimNo.charAt(claimNo.length() - 1))
          .as("校验位相同是 CL→RP 前缀改写后仍合法的充要条件")
          .isEqualTo(reportNo.charAt(reportNo.length() - 1));
    }

    @Test
    @DisplayName("字符集为 Crockford Base32：不含易混字符 I/L/O/U")
    void alphabetExcludesConfusables() {
      String reportNo = BizNoCodec.generate(RP, SAMPLE_ID, FIXED_DATE);

      assertThat(reportNo).matches("RP[0-9]{8}[0-9ABCDEFGHJKMNPQRSTVWXYZ]{14}");
      assertThat(reportNo.substring(2)).as("生成端永不产出 I/L/O/U").doesNotContain("I", "L", "O", "U");
    }
  }

  @Nested
  @DisplayName("双射与唯一性")
  class Bijection {

    @Test
    @DisplayName("round-trip：报案号 → 雪花 ID 必须还原（含边界值，10000 样本）")
    void roundTrip() {
      long[] samples =
          LongStream.concat(
                  LongStream.of(1L, 4095L, 4096L, Long.MAX_VALUE),
                  LongStream.rangeClosed(1, 9996).map(i -> i * 7919L))
              .toArray();

      for (long id : samples) {
        String reportNo = BizNoCodec.generate(RP, id, FIXED_DATE);
        assertThat(BizNoCodec.extractSnowflakeId(RP, reportNo))
            .as("id=%d 经报案号 round-trip 必须还原", id)
            .isEqualTo(id);
      }
    }

    @Test
    @DisplayName("零碰撞：连续 100000 个 ID 产出 100000 个不同报案号")
    void noCollision() {
      Set<String> numbers = new HashSet<>();
      for (long id = 1; id <= 100_000; id++) {
        numbers.add(BizNoCodec.generate(RP, id, FIXED_DATE));
      }
      assertThat(numbers).hasSize(100_000);
    }

    @Test
    @DisplayName("两个前缀共用载荷：同一 ID 的 CL 号与 RP 号互不相等但可各自解析回同一 ID")
    void twoNamespacesSharePayload() {
      String claimNo = BizNoCodec.generate(CL, SAMPLE_ID, FIXED_DATE);
      String reportNo = BizNoCodec.generate(RP, SAMPLE_ID, FIXED_DATE);

      assertThat(claimNo).isNotEqualTo(reportNo);
      assertThat(BizNoCodec.extractSnowflakeId(CL, claimNo)).isEqualTo(SAMPLE_ID);
      assertThat(BizNoCodec.extractSnowflakeId(RP, reportNo)).isEqualTo(SAMPLE_ID);
    }
  }

  @Nested
  @DisplayName("命名空间隔离（拿错号必须报错，不能静默命中）")
  class NamespaceIsolation {

    @Test
    @DisplayName("CL 号用 RP 前缀校验 → false")
    void claimNoIsNotValidReportNo() {
      String claimNo = BizNoCodec.generate(CL, SAMPLE_ID, FIXED_DATE);

      assertThat(BizNoCodec.isValid(RP, claimNo)).isFalse();
    }

    @Test
    @DisplayName("CL 号用 RP 前缀解析 → 抛错且文案点名前缀（不返回一个错的 ID）")
    void crossPrefixExtractThrows() {
      String claimNo = BizNoCodec.generate(CL, SAMPLE_ID, FIXED_DATE);

      assertThatThrownBy(() -> BizNoCodec.extractSnowflakeId(RP, claimNo))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(RP);
    }

    @Test
    @DisplayName("存量回填的关键前提：CL 号改成 RP 前缀后，按 RP 校验与解析都成立")
    void prefixRewriteStaysValid() {
      String claimNo = BizNoCodec.generate(CL, SAMPLE_ID, FIXED_DATE);
      // 与 0139 迁移里的 CONCAT('RP', SUBSTRING(claim_no, 3)) 完全等价
      String rewritten = RP + claimNo.substring(CL.length());

      assertThat(BizNoCodec.isValid(RP, rewritten))
          .as("迁移只改前缀不重算载荷，号码必须依然合法——这是纯 SQL 回填的充要条件")
          .isTrue();
      assertThat(BizNoCodec.extractSnowflakeId(RP, rewritten)).isEqualTo(SAMPLE_ID);
    }
  }

  @Nested
  @DisplayName("非法输入")
  class Rejects {

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("雪花 ID 非正 → 抛错")
    void rejectsNonPositiveId(long id) {
      assertThatThrownBy(() -> BizNoCodec.generate(RP, id, FIXED_DATE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("雪花");
    }

    @Test
    @DisplayName("前缀长度不是 2 → 抛错")
    void rejectsBadPrefixOnGenerate() {
      assertThatThrownBy(() -> BizNoCodec.generate("RPT", SAMPLE_ID, FIXED_DATE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("前缀");
    }

    @Test
    @DisplayName("日期为空 → NPE（不允许隐式取今天，否则同一 ID 会发出两个号）")
    void rejectsNullDate() {
      assertThatThrownBy(() -> BizNoCodec.generate(RP, SAMPLE_ID, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("长度不足 / 超长 / 空 / null → isValid 一律 false（不抛错，供入参校验直接取反）")
    void invalidShapesAreFalse() {
      String good = BizNoCodec.generate(RP, SAMPLE_ID, FIXED_DATE);

      assertThat(BizNoCodec.isValid(RP, null)).isFalse();
      assertThat(BizNoCodec.isValid(RP, "")).isFalse();
      assertThat(BizNoCodec.isValid(RP, good.substring(0, good.length() - 1))).isFalse();
      assertThat(BizNoCodec.isValid(RP, good + "0")).isFalse();
      assertThat(BizNoCodec.isValid(null, good)).isFalse();
      assertThat(BizNoCodec.isValid("", good)).isFalse();
    }

    @Test
    @DisplayName("校验位被改 → false（手抄错一位必须能被发现）")
    void corruptedChecksumIsFalse() {
      String good = BizNoCodec.generate(RP, SAMPLE_ID, FIXED_DATE);
      char last = good.charAt(good.length() - 1);
      char flipped = last == '0' ? '1' : '0';
      String bad = good.substring(0, good.length() - 1) + flipped;

      assertThat(BizNoCodec.isValid(RP, bad)).isFalse();
    }

    @Test
    @DisplayName("载荷含非法字符 → false（不抛异常，避免入参校验变成 500）")
    void illegalPayloadCharIsFalse() {
      String good = BizNoCodec.generate(RP, SAMPLE_ID, FIXED_DATE);
      // 把载荷首字符换成 Crockford 之外的符号；长度不变，只有校验位能拦
      String bad = good.substring(0, 10) + "#" + good.substring(11);

      assertThat(BizNoCodec.isValid(RP, bad)).isFalse();
    }
  }
}
