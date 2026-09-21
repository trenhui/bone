package com.bone.engine.extension.studio.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.application.query.handler.PluginExecutionLogQueryApplicationService;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginExecutionLogCursorTest {

  private PluginExecutionLogQueryApplicationService handler;

  @BeforeEach
  void setUp() {
    InMemoryStudioPluginExecutionLogRepository logReadPort =
        new InMemoryStudioPluginExecutionLogRepository();
    InMemoryStudioExtensionRepository extensionRepository = new InMemoryStudioExtensionRepository();
    handler = new PluginExecutionLogQueryApplicationService(logReadPort);

    Extension extension = Extension.create(10L, "demo", "test", "com.demo.Ext");
    extension.setId(1L);
    extensionRepository.save(extension);

    for (int i = 0; i < 5; i++) {
      PluginExecutionLog log = new PluginExecutionLog();
      log.setPluginId(1L);
      log.setExtensionPointId(10L);
      log.setExecutionId("exec-" + i);
      log.setStatus("SUCCESS");
      logReadPort.save(log);
    }
  }

  @Test
  void queryByCursor_returnsNextCursor() {
    PageResult<PluginExecutionLog> first = handler.queryByCursor(1L, null, null, 2);
    assertEquals(2, first.getRecords().size());
    assertNotNull(first.getNextCursor());

    PageResult<PluginExecutionLog> second =
        handler.queryByCursor(1L, null, first.getNextCursor(), 2);
    assertEquals(2, second.getRecords().size());
    assertTrue(second.getRecords().get(0).getId() < first.getRecords().get(1).getId());
  }
}
