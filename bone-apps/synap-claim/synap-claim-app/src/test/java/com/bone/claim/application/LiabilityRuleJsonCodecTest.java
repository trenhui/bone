package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.LiabilityRuleJson;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * `liability_rule_json` 的写入期 Schema（M2 ② Shadow）。
 *
 * <p><b>这组用例守的是"静默失效"这一族缺陷</b>：
 *
 * <ul>
 *   <li>拼错的键被静默忽略 ⇒ 配了等于没配；
 *   <li>未知版本被按旧语义解释 ⇒ 未来数据被算错且无从归因；
 *   <li>超集表达（分段比例）在 ② 被接受 ⇒ 回滚窗口里 {@code DECIMAL} 写不下，回滚即静默少赔；
 *   <li>影子与标量不一致被放行 ⇒ ③ 切换读侧当天按错值赔付。
 * </ul>
 *
 * <p>每条拒绝都断言"消息里写清了原因 / 何时可用"，因为运营与值班看到的只有这句话。
 */
class LiabilityRuleJsonCodecTest {

  /** 全部 9 个键 + schemaVersion 的完整文档：用来证明"能写进去就能读出来"。 */
  private static final String FULL_DOCUMENT =
      """
      {"schemaVersion":1,
       "pay_percent":90.00,
       "liability_deduct":200.00,
       "liability_limit":5000.00,
       "quota_controller":{"accountType":"PERSONAL","mode":"LIABILITY_ONLY"},
       "restrict_scope":{"mode":"WHITELIST","hospitalCodes":["H1","H2"]},
       "restrict_object":{"relation":"SELF","ageRange":{"min":18,"max":60}},
       "restrict_out_insure":{"cause":"DISEASE","exclusions":["X1"]},
       "times_limit":{"perYear":12,"perDay":1},
       "adjustment_rule":{"catalogCode":"NATIONAL_2024","catalogPolicy":"MANDATORY"}}
      """;

  @Test
  void encode_thenDecode_roundTripsAllNineKeys() {
    LiabilityRuleJson json = LiabilityRuleJsonCodec.decode(FULL_DOCUMENT);

    assertThat(json.payPercent()).isEqualByComparingTo("90.00");
    assertThat(json.liabilityDeduct()).isEqualByComparingTo("200.00");
    assertThat(json.liabilityLimit()).isEqualByComparingTo("5000.00");
    assertThat(json.dimensions()).hasSize(6);

    String encoded = LiabilityRuleJsonCodec.encode(json);
    LiabilityRuleJson decodedAgain = LiabilityRuleJsonCodec.decode(encoded);

    assertThat(decodedAgain).isEqualTo(json);
    assertThat(LiabilityRuleJsonCodec.encode(decodedAgain)).isEqualTo(encoded);
  }

  /** 空维度文档也必须有稳定编码：同一份配置两次写入要产出同一个字符串。 */
  @Test
  void encode_isDeterministic() {
    LiabilityRuleJson json =
        LiabilityRuleJson.ofScalars(
            new BigDecimal("90.00"), new BigDecimal("200.00"), new BigDecimal("5000.00"));

    assertThat(LiabilityRuleJsonCodec.encode(json))
        .isEqualTo(
            "{\"schemaVersion\":1,\"pay_percent\":90.00,\"liability_deduct\":200.00,"
                + "\"liability_limit\":5000.00}");
  }

  /**
   * 超集表达在 ② 必须被拒，且消息要说明"④ 之后可用"。
   *
   * <p>只写"不合法"会让运营以为永远不支持；只写"暂不支持"又会让人以为可以硬填。
   */
  @Test
  void arrayShapedAmount_isRejectedWithUnlockHint() {
    String segmented =
        "{\"schemaVersion\":1,\"pay_percent\":[{\"upto\":1000,\"percent\":80},"
            + "{\"percent\":90}],\"liability_deduct\":0,\"liability_limit\":0}";

    assertThatThrownBy(() -> LiabilityRuleJsonCodec.decode(segmented))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("pay_percent")
        .hasMessageContaining("④ Unlock 解锁后才可用");
  }

  @Test
  void missingSchemaVersion_isRejected() {
    assertThatThrownBy(
            () ->
                LiabilityRuleJsonCodec.decode(
                    "{\"pay_percent\":90,\"liability_deduct\":0,\"liability_limit\":0}"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("缺少 schemaVersion");
  }

  @Test
  void futureSchemaVersion_isRejected() {
    assertThatThrownBy(() -> LiabilityRuleJsonCodec.decode("{\"schemaVersion\":2}"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("未知的 schemaVersion=2");
  }

  @Test
  void unknownTopLevelKey_isRejected() {
    assertThatThrownBy(
            () ->
                LiabilityRuleJsonCodec.decode(
                    "{\"schemaVersion\":1,\"pay_percent\":90,\"pay_percnt\":80,"
                        + "\"liability_deduct\":0,\"liability_limit\":0}"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("未知键「pay_percnt」");
  }

  @Test
  void missingAmountKey_isRejected() {
    assertThatThrownBy(
            () ->
                LiabilityRuleJsonCodec.decode(
                    "{\"schemaVersion\":1,\"pay_percent\":90,\"liability_limit\":0}"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("缺少必需计算参数 liability_deduct");
  }

  @Test
  void scalarShapedDimension_isRejected() {
    assertThatThrownBy(
            () ->
                LiabilityRuleJsonCodec.decode(
                    "{\"schemaVersion\":1,\"pay_percent\":90,\"liability_deduct\":0,"
                        + "\"liability_limit\":0,\"times_limit\":3}"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("times_limit")
        .hasMessageContaining("必须是 JSON 对象");
  }

  @Test
  void blankDocument_isRejected() {
    assertThatThrownBy(() -> LiabilityRuleJsonCodec.decode("  "))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("历史行可以为 NULL");
  }

  // ===================== 写入后回读比对 =====================

  @Test
  void assertShadowConsistent_passesWhenProjectionMatchesScalars() {
    AdjLiability entity = liability(new BigDecimal("90.00"), new BigDecimal("200.00"));
    entity.attachRuleJsonShadow(
        LiabilityRuleJsonCodec.encode(
            LiabilityRuleJson.ofScalars(
                new BigDecimal("90.00"), new BigDecimal("200.00"), new BigDecimal("5000.00"))));

    assertThatCode(() -> LiabilityRuleJsonCodec.assertShadowConsistent(entity, 1L))
        .doesNotThrowAnyException();
  }

  /** 影子与标量不一致 ⇒ 拒绝提交。这是 ② 唯一真正要防的事故。 */
  @Test
  void assertShadowConsistent_rejectsMismatch() {
    AdjLiability entity = liability(new BigDecimal("80.00"), new BigDecimal("200.00"));
    entity.attachRuleJsonShadow(
        LiabilityRuleJsonCodec.encode(
            LiabilityRuleJson.ofScalars(
                new BigDecimal("90.00"), new BigDecimal("200.00"), new BigDecimal("5000.00"))));

    assertThatThrownBy(() -> LiabilityRuleJsonCodec.assertShadowConsistent(entity, 7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("id=7")
        .hasMessageContaining("pay_percent");
  }

  /** 影子没落库（实体漏 @Column / 模板漏列）⇒ 不许静默通过。 */
  @Test
  void assertShadowConsistent_rejectsMissingShadow() {
    AdjLiability entity = liability(new BigDecimal("90.00"), new BigDecimal("200.00"));

    assertThatThrownBy(() -> LiabilityRuleJsonCodec.assertShadowConsistent(entity, 8L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("影子未落库");
  }

  @Test
  void assertShadowConsistent_rejectsUnreadableRow() {
    assertThatThrownBy(() -> LiabilityRuleJsonCodec.assertShadowConsistent(null, 9L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("回读不到");
  }

  private static AdjLiability liability(BigDecimal payPercent, BigDecimal deduct) {
    return AdjLiability.of(
        1L,
        1L,
        "uuid-1",
        "v1",
        1001L,
        "R01",
        "REIMBURSEMENT",
        payPercent,
        deduct,
        new BigDecimal("5000.00"),
        1,
        0,
        "DRAFT");
  }
}
