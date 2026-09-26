package com.bone.masterdata.domain.model.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** {@link TemplateVersion} 纯单测：快照字段冻结、approvedAt 仅在审批人存在时填充。 */
class TemplateVersionTest {

  @Test
  void testSnapshotFreezesFields() {
    TemplateVersion v = TemplateVersion.snapshot(1L, 2L, "1.1.0", "变更", "[{}]", null, null);
    assertEquals("1.1.0", v.getVersionNumber());
    assertEquals("变更", v.getChangeLog());
    assertEquals("[{}]", v.getFieldSchema());
    assertNull(v.getCategorySchema());
    assertEquals(2L, v.getTemplateId());
  }
}
