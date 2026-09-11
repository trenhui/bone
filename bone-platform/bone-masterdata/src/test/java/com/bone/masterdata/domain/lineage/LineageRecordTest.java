package com.bone.masterdata.domain.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** {@link LineageRecord} 纯单测：字段级血缘快照创建（无容器）。 */
class LineageRecordTest {

  @Test
  void testCreateSnapshotsLineageEdge() {
    LineageRecord record =
        LineageRecord.create(
            1L, "ic_customer", "mobile", "JOIN", "ic_insurer", "phone", "customer_mart");

    assertEquals("ic_customer", record.getSourceEntity());
    assertEquals("mobile", record.getSourceField());
    assertEquals("JOIN", record.getTransformType());
    assertEquals("ic_insurer", record.getTargetEntity());
    assertEquals("phone", record.getTargetField());
    assertEquals("customer_mart", record.getSchemaName());
    assertNotNull(record.getCreatedAt());
  }
}
