package com.bone.masterdata.domain.model.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** {@link MasterDataRecordVersion} 纯单测：快照冻结与审批人回填。 */
class MasterDataRecordVersionTest {

  @Test
  void testSnapshotWithApprover() {
    MasterDataRecordVersion v =
        MasterDataRecordVersion.snapshot(1L, 2L, 3, "{\"a\":1}", "APPROVED", "备注", 9L, 8L);
    assertEquals(3, v.getVersionNumber());
    assertEquals("APPROVED", v.getStatus());
    assertEquals(9L, v.getApprovedBy());
    assertEquals(2L, v.getRecordId());
  }

  @Test
  void testSnapshotWithoutApproverLeavesApprovedAtNull() {
    MasterDataRecordVersion v =
        MasterDataRecordVersion.snapshot(1L, 2L, 1, "{}", "PUBLISHED", null, null, 8L);
    assertEquals("PUBLISHED", v.getStatus());
    assertNull(v.getApprovedAt());
  }
}
