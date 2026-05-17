package com.bone.engine.extension.studio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.infrastructure.persistence.InMemoryExtensionStore;
import com.bone.engine.extension.studio.infrastructure.persistence.InMemoryPluginExecutionLogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginExecutionLogCursorTest {

    private PluginExecutionLogService service;

    @BeforeEach
    void setUp() {
        InMemoryPluginExecutionLogStore logStore = new InMemoryPluginExecutionLogStore();
        InMemoryExtensionStore extensionStore = new InMemoryExtensionStore();
        service = new PluginExecutionLogService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "logStore", logStore);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "extensionStore", extensionStore);

        Extension extension = Extension.create(10L, "demo", "test", "com.demo.Ext");
        extension.setId(1L);
        extensionStore.save(extension);

        for (int i = 0; i < 5; i++) {
            PluginExecutionLog log = new PluginExecutionLog();
            log.setPluginId(1L);
            log.setExtensionPointId(10L);
            log.setExecutionId("exec-" + i);
            log.setStatus("SUCCESS");
            logStore.save(log);
        }
    }

    @Test
    void queryByCursor_returnsNextCursor() {
        PageResult<PluginExecutionLog> first = service.queryByCursor(1L, null, null, 2);
        assertEquals(2, first.getRecords().size());
        assertNotNull(first.getNextCursor());

        PageResult<PluginExecutionLog> second = service.queryByCursor(1L, null, first.getNextCursor(), 2);
        assertEquals(2, second.getRecords().size());
        assertTrue(second.getRecords().get(0).getId() < first.getRecords().get(1).getId());
    }
}
