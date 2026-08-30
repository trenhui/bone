package com.bone.core.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 查询类型枚举测试：代码反查与元数据完整性。 */
class QueryTypeEnumTest {

  @Test
  void getByCode_resolvesEveryConstant() {
    for (QueryTypeEnum e : QueryTypeEnum.values()) {
      assertThat(QueryTypeEnum.getByCode(e.getCode())).isEqualTo(e);
    }
  }

  @Test
  void getByCode_unknownOrNullReturnsNull() {
    assertThat(QueryTypeEnum.getByCode("NO_SUCH_TYPE")).isNull();
    // 实现为 e.getCode().equals(code)：参数为 null 时恒 false，遍历结束返回 null（不抛 NPE）
    assertThat(QueryTypeEnum.getByCode(null)).isNull();
  }

  @Test
  void everyConstantHasCodeAndValue() {
    for (QueryTypeEnum e : QueryTypeEnum.values()) {
      assertThat(e.getCode()).isNotBlank();
      assertThat(e.getValue()).isNotBlank();
    }
  }

  /**
   * code 唯一性是反查可靠的前提：与 {@link Operator} 不同（后者多个常量共用 "LIKE" 符号，按符号反查有歧义）， 本枚举的 code 一一对应，可安全用于反查。
   */
  @Test
  void codesAreUnique() {
    Set<String> codes = new HashSet<>();
    for (QueryTypeEnum e : QueryTypeEnum.values()) {
      codes.add(e.getCode());
    }

    assertThat(codes).hasSize(QueryTypeEnum.values().length);
  }
}
