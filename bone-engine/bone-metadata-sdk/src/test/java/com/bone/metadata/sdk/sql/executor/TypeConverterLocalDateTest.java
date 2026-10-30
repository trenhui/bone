package com.bone.metadata.sdk.sql.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TypeConverter} 对 {@code LocalDate} 的收敛行为。
 *
 * <p><b>为什么值得单独一个用例</b>：日期类列（如理赔的发票开票日期）读侧一旦抛 {@code Unsupported LocalDate
 * conversion}，整张表都读不出来，错误信息只指向"某一行"， 与列值无关，现场极难定位。 驱动在 {@code getObject()} 上返回 {@code
 * java.sql.Date} 还是 {@code LocalDate} 依版本而变， 故两种输入都必须被接受。
 */
class TypeConverterLocalDateTest {

  @Test
  @DisplayName("java.sql.Date → LocalDate：驱动返回旧类型时必须可读")
  void fromSqlDate() {
    assertThat(TypeConverter.convert(java.sql.Date.valueOf("2026-03-10"), LocalDate.class))
        .isEqualTo(LocalDate.of(2026, 3, 10));
  }

  @Test
  @DisplayName("LocalDate → LocalDate：驱动直接返回 java.time 类型时不得抛异常")
  void fromLocalDateIdentity() {
    assertThat(TypeConverter.convert(LocalDate.of(2026, 3, 10), LocalDate.class))
        .isEqualTo(LocalDate.of(2026, 3, 10));
  }

  @Test
  @DisplayName("LocalDateTime → LocalDate：TIMESTAMP 列映射到 DATE 字段时取日期部分")
  void fromLocalDateTime() {
    assertThat(TypeConverter.convert(LocalDateTime.of(2026, 3, 10, 18, 30), LocalDate.class))
        .isEqualTo(LocalDate.of(2026, 3, 10));
  }

  @Test
  @DisplayName("字符串：ISO 与两种常见日期时间格式都要能解析")
  void fromString() {
    assertThat(TypeConverter.convert("2026-03-10", LocalDate.class))
        .isEqualTo(LocalDate.of(2026, 3, 10));
    assertThat(TypeConverter.convert("2026-03-10 18:30:00", LocalDate.class))
        .isEqualTo(LocalDate.of(2026, 3, 10));
  }
}
