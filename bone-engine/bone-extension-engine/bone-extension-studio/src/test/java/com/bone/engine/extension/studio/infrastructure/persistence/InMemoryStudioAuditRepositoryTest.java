package com.bone.engine.extension.studio.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.domain.model.audit.StudioAuditEntry;
import org.junit.jupiter.api.Test;

class InMemoryStudioAuditRepositoryTest {

  private final InMemoryStudioAuditRepository store = new InMemoryStudioAuditRepository();

  @Test
  void queryByCursor_filtersByAction() {
    StudioAuditEntry deploy = new StudioAuditEntry();
    deploy.setAction("plugin.deploy");
    deploy.setResourceType("plugin");
    deploy.setResourceId("1");
    deploy.setResult("SUCCESS");
    store.save(deploy);

    StudioAuditEntry other = new StudioAuditEntry();
    other.setAction("ext_point.create");
    other.setResourceType("ext_point");
    other.setResourceId("2");
    other.setResult("SUCCESS");
    store.save(other);

    PageResult<StudioAuditEntry> page = store.queryByCursor("plugin.deploy", null, null, 10);
    assertEquals(1, page.getRecords().size());
    assertEquals("plugin.deploy", page.getRecords().get(0).getAction());
    assertFalse(page.getRecords().stream().anyMatch(e -> "ext_point.create".equals(e.getAction())));
  }
}
