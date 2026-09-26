package com.bone.masterdata.domain.model.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.template.valueobject.DomainTemplateStatus;
import org.junit.jupiter.api.Test;

/** {@link DomainTemplate} 纯单测：DRAFT 起步、发布不可直改、归档后禁发版。 */
class DomainTemplateTest {

  private DomainTemplate create() {
    return DomainTemplate.create(1L, "CUSTOMER", "客户域", "d", "L2", "[]", null, null);
  }

  @Test
  void testCreateStartsAtDraftWithDefaults() {
    DomainTemplate t = create();
    assertEquals(DomainTemplateStatus.DRAFT, t.getStatus());
    assertEquals("1.0.0", t.getCurrentVersion());
    assertEquals("L2", t.getDefaultGovernanceTier());
  }

  @Test
  void testPublishedTemplateRejectsDirectUpdate() {
    DomainTemplate t = create();
    t.publishVersion("1.1.0");
    assertEquals(DomainTemplateStatus.PUBLISHED, t.getStatus());
    assertThrows(DomainException.class, () -> t.update("n", "d", null, null, null, null));
  }

  @Test
  void testArchiveBlocksFurtherVersions() {
    DomainTemplate t = create();
    t.archive();
    assertEquals(DomainTemplateStatus.ARCHIVED, t.getStatus());
    assertThrows(DomainException.class, () -> t.publishVersion("2.0.0"));
    assertTrue(DomainTemplateStatus.ARCHIVED.isInstantiable() == false);
  }
}
