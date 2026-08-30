package com.bone.core.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 查询操作符枚举测试：符号 / 代码两种反查语义，并固化「多枚举共享 LIKE 符号」的既有行为。 */
class OperatorTest {

  @Test
  void getBySymbol_findsUniqueSymbols() {
    assertThat(Operator.getBySymbol("=")).isEqualTo(Operator.EQ);
    assertThat(Operator.getBySymbol("<>")).isEqualTo(Operator.NE);
    assertThat(Operator.getBySymbol(">")).isEqualTo(Operator.GT);
    assertThat(Operator.getBySymbol("<")).isEqualTo(Operator.LT);
    assertThat(Operator.getBySymbol(">=")).isEqualTo(Operator.GTE);
    assertThat(Operator.getBySymbol("<=")).isEqualTo(Operator.LTE);
    assertThat(Operator.getBySymbol("IN")).isEqualTo(Operator.IN);
    assertThat(Operator.getBySymbol("NOT IN")).isEqualTo(Operator.NOT_IN);
    assertThat(Operator.getBySymbol("IS NULL")).isEqualTo(Operator.IS_NULL);
    assertThat(Operator.getBySymbol("IS NOT NULL")).isEqualTo(Operator.IS_NOT_NULL);
    assertThat(Operator.getBySymbol("NOT LIKE")).isEqualTo(Operator.NOT_LIKE);
    assertThat(Operator.getBySymbol("BETWEEN")).isEqualTo(Operator.BETWEEN);
  }

  /**
   * 陷阱固化（非缺陷修复，仅锁定现状）：{@code LIKE} / {@code LIKE_LEFT} / {@code LIKE_RIGHT} / {@code FULL_LIKE}
   * 四者共用 symbol {@code "LIKE"}，而 {@link Operator#getBySymbol} 取首个匹配， 因此**按符号反查永远得到 {@code LIKE}**。
   *
   * <p>需要区分模糊匹配方向（左/右/全）时，必须改用 {@link Operator#fromCode} 或直接使用枚举名， 不能用 getBySymbol。
   */
  @Test
  void getBySymbol_likeSymbolIsAmbiguous() {
    assertThat(Operator.getBySymbol("LIKE")).isEqualTo(Operator.LIKE);
    assertThat(Operator.getBySymbol("LIKE")).isNotEqualTo(Operator.LIKE_LEFT);
    assertThat(Operator.getBySymbol("LIKE")).isNotEqualTo(Operator.FULL_LIKE);
  }

  @Test
  void getBySymbol_unknownOrNullReturnsNull() {
    assertThat(Operator.getBySymbol("~~")).isNull();
    assertThat(Operator.getBySymbol(null)).isNull();
  }

  @Test
  void fromCode_isCaseInsensitive() {
    assertThat(Operator.fromCode("eq")).isEqualTo(Operator.EQ);
    assertThat(Operator.fromCode("EQ")).isEqualTo(Operator.EQ);
    assertThat(Operator.fromCode("gte")).isEqualTo(Operator.GTE);
    assertThat(Operator.fromCode("not_in")).isEqualTo(Operator.NOT_IN);
    // 按代码可精确取到 getBySymbol 取不到的模糊方向枚举
    assertThat(Operator.fromCode("like_left")).isEqualTo(Operator.LIKE_LEFT);
    assertThat(Operator.fromCode("like_right")).isEqualTo(Operator.LIKE_RIGHT);
    assertThat(Operator.fromCode("full_like")).isEqualTo(Operator.FULL_LIKE);
  }

  @Test
  void fromCode_unknownOrNullReturnsNull() {
    assertThat(Operator.fromCode("NO_SUCH_OP")).isNull();
    assertThat(Operator.fromCode(null)).isNull();
  }

  /** 枚举元数据完整性：symbol 与描述均不可为空，供前端/DSL 展示使用。 */
  @Test
  void everyOperatorHasSymbolAndDescription() {
    for (Operator op : Operator.values()) {
      assertThat(op.getSymbol()).isNotBlank();
      assertThat(op.getDescription()).isNotBlank();
    }
  }
}
