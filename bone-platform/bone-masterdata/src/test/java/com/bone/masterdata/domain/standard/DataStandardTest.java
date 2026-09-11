package com.bone.masterdata.domain.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.standard.vo.StandardFieldCode;
import com.bone.masterdata.domain.standard.vo.StandardRuleType;
import org.junit.jupiter.api.Test;

/** {@link DataStandard} 纯单测：数据标准创建/更新与字段编码约束（无容器）。 */
class DataStandardTest {

  private DataStandard createStandard() {
    return DataStandard.create(
        1L,
        "CUSTOMER",
        StandardFieldCode.of("gender"),
        StandardRuleType.REFERENCE,
        "M/F",
        "GENDER_CODE",
        "性别标准");
  }

  @Test
  void testCreateSnapshotsStandardProfile() {
    DataStandard standard = createStandard();

    assertEquals("CUSTOMER", standard.getEntityCode());
    assertEquals("gender", standard.getFieldCode().value());
    assertEquals(StandardRuleType.REFERENCE, standard.getRuleType());
    assertEquals("M/F", standard.getPattern());
    assertNotNull(standard.getCreatedAt());
  }

  @Test
  void testUpdateOverridesRuleProfile() {
    DataStandard standard = createStandard();

    standard.update(StandardRuleType.ENCODING, "\\d{6}", null, "改为编码规则");

    assertEquals(StandardRuleType.ENCODING, standard.getRuleType());
    assertEquals("\\d{6}", standard.getPattern());
    assertEquals("改为编码规则", standard.getDescription());
  }

  @Test
  void testFieldCodeRejectsBlankOrOversized() {
    assertThrows(DomainException.class, () -> StandardFieldCode.of(" "));
    assertThrows(DomainException.class, () -> StandardFieldCode.of("x".repeat(129)));
  }
}
