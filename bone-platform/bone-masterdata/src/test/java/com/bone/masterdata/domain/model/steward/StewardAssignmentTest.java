package com.bone.masterdata.domain.model.steward;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link StewardAssignment} 纯单测：指派字段落位。 */
class StewardAssignmentTest {

  @Test
  void testAssignCarriesRoleAndAccount() {
    StewardAssignment a = StewardAssignment.assign(1L, 10L, 20L, "STEWARD");
    assertEquals(20L, a.getAccountId());
    assertEquals("STEWARD", a.getRoleType());
    assertEquals(10L, a.getMasterDataEntityId());
  }
}
