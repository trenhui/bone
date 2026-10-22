package com.bone.metadata.sdk.sql.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SmartRowMapper 枚举列「code 串」读取的回归测试。
 *
 * <p><b>为什么必须有这个测试</b>：写路径 {@code SqlUtil.toJdbcParameter} 对带 {@code getCode()} 的枚举按 <b>code</b>
 * 落库（varchar 列存 "1"/"2"），而读路径原先只认 {@code Enum.valueOf}（按枚举名）—— 「写 code、读按名」不对称 ⇒ 任何 code 型枚举 +
 * varchar 列都是「建得进、读不出」 （线上实证：{@code md_standard.rule_type} 创建成功，随后分页查询 400 No enum constant）。
 * 修复后数字串先走 {@code of(int)}/{@code byCode(int)} 工厂；本测试在修复前必然失败。
 *
 * <p>同时护栏两类不能破坏的既有行为：①纯名字枚举按名读取；②无工厂的枚举遇数字串仍报错（不静默错映射）。
 */
class SmartRowMapperEnumCodeTest {

  /** 复刻 StandardRuleType 形态：带 code + of(int) 工厂的枚举。 */
  enum CodedSeverity {
    LOW(1),
    HIGH(2);

    private final int code;

    CodedSeverity(int code) {
      this.code = code;
    }

    public int getCode() {
      return code;
    }

    public static CodedSeverity of(int code) {
      for (CodedSeverity t : values()) {
        if (t.code == code) {
          return t;
        }
      }
      throw new IllegalArgumentException("未知严重级别: " + code);
    }
  }

  /** 纯名字枚举（无任何工厂）——回归基线。 */
  enum PlainState {
    ACTIVE,
    INACTIVE
  }

  static class Holder {
    // 列 label 与字段名同形，FieldCache 按字段名索引命中
    CodedSeverity severity;
    PlainState state;
  }

  private final SmartRowMapper<Holder> mapper = new SmartRowMapper<>(Holder.class);

  private Holder mapOne(String column, Object value) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    ResultSetMetaData md = mock(ResultSetMetaData.class);
    when(rs.getMetaData()).thenReturn(md);
    when(md.getColumnCount()).thenReturn(1);
    when(md.getColumnLabel(1)).thenReturn(column);
    when(rs.getObject(1)).thenReturn(value);
    return mapper.mapRow(rs, 1);
  }

  @Test
  @DisplayName("code 串按 of(int) 工厂映射（修复主断言）：\"1\"→LOW、\"2\"→HIGH")
  void numericStringMapsByCodeFactory() throws SQLException {
    assertThat(mapOne("severity", "1").severity).isEqualTo(CodedSeverity.LOW);
    assertThat(mapOne("severity", "2").severity).isEqualTo(CodedSeverity.HIGH);
  }

  @Test
  @DisplayName("回归：code 型枚举的存量名值仍可按名读取（\"HIGH\"→HIGH）")
  void nameStringStillMapsByNameForCodedEnum() throws SQLException {
    assertThat(mapOne("severity", "HIGH").severity).isEqualTo(CodedSeverity.HIGH);
  }

  @Test
  @DisplayName("回归：纯名字枚举按名读取不受影响（\"ACTIVE\"→ACTIVE）")
  void plainEnumByNameUnchanged() throws SQLException {
    assertThat(mapOne("state", "ACTIVE").state).isEqualTo(PlainState.ACTIVE);
  }

  @Test
  @DisplayName("回归护栏：无工厂枚举遇数字串仍然抛错，不静默错映射")
  void numericStringWithoutFactoryStillFails() {
    assertThatThrownBy(() -> mapOne("state", "1")).isInstanceOf(IllegalArgumentException.class);
  }
}
