package com.bone.masterdata.domain.model.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link TenantReferenceValue} 纯单测（R8 聚合门禁）：overlay 拆分后的租户私有扩展值聚合不变量。 */
public class TenantReferenceValueTest {

  @Test
  public void createAppliesDefaults() {
    TenantReferenceValue value =
        TenantReferenceValue.create(1L, 100L, "CUST_LEVEL_A", "战略客户", null, null);

    assertEquals(1L, value.getId());
    assertEquals(100L, value.getSetId());
    assertEquals("CUST_LEVEL_A", value.getValueCode());
    assertEquals("战略客户", value.getValueName());
    assertNull(value.getExternalCode());
    assertEquals(0, value.getSortOrder());
    assertTrue(value.getEnabled());
    // 租户归属由 SDK 按上下文回填，工厂方法不越权设定
    assertNull(value.getTenantId());
  }

  @Test
  public void updateChangesEditableFieldsOnly() {
    TenantReferenceValue value = TenantReferenceValue.create(1L, 100L, "OLD", "旧名", "EXT", 5);
    value.update("新名", null, null);

    assertEquals("新名", value.getValueName());
    assertNull(value.getExternalCode());
    assertEquals(5, value.getSortOrder());
    assertEquals("OLD", value.getValueCode());
    assertTrue(value.getEnabled());
  }

  @Test
  public void disableThenEnableTogglesEnabled() {
    TenantReferenceValue value = TenantReferenceValue.create(1L, 100L, "C1", "值", null, 0);
    value.disable();
    assertFalse(value.getEnabled());
    value.enable();
    assertTrue(value.getEnabled());
  }
}
