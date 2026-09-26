package com.bone.masterdata.domain.model.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.Test;

/** {@link MasterDataCategory} 纯单测：根/子层级推导、防自挂父。 */
class MasterDataCategoryTest {

  @Test
  void testRootStartsAtLevelZero() {
    MasterDataCategory root = MasterDataCategory.create(1L, 10L, "C1", "一类", null, null, null, 0);
    assertEquals(0, root.getLevel());
    assertNull(root.getParentCategoryId());
  }

  @Test
  void testChildLevelIsParentPlusOne() {
    MasterDataCategory child = MasterDataCategory.create(2L, 10L, "C2", "子类", null, 1L, 0, 1);
    assertEquals(1, child.getLevel());
  }

  @Test
  void testRejectsSelfParent() {
    MasterDataCategory c = MasterDataCategory.create(3L, 10L, "C3", "x", null, null, null, 0);
    assertThrows(DomainException.class, () -> c.ensureNotSelfParent(3L));
  }
}
