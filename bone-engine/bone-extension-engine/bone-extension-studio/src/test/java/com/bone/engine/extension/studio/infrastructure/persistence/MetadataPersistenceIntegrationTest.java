package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.store.ExtPointStore;
import com.bone.engine.extension.studio.domain.store.ExtensionStore;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioExtensionImplRepository;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioExtensionPointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = com.bone.engine.extension.studio.support.MetadataPersistenceTestApplication.class)
@ActiveProfiles("test")
class MetadataPersistenceIntegrationTest {

    @Autowired
    private ExtPointStore extPointStore;

    @Autowired
    private ExtensionStore extensionStore;

    @Autowired
    private ExtStudioExtensionPointRepository extPointRepository;

    @Autowired
    private ExtStudioExtensionImplRepository extensionRepository;

    @Test
    void contextLoadsRepositoriesAndStores() {
        assertNotNull(extPointStore);
        assertNotNull(extensionStore);
        assertNotNull(extPointRepository);
        assertNotNull(extensionRepository);
    }

    @Test
    void saveAndQueryRoundTripThroughMetadataSdk() {
        ExtPoint point = new ExtPoint();
        point.setName("集成测试扩展点");
        point.setInterfaceName("com.bone.test.IntegrationExtPoint");
        point.setDomain("test");
        point.setEnabled(true);
        extPointStore.save(point);
        assertNotNull(point.getId());

        Extension extension =
                Extension.create(
                        point.getId(),
                        "集成实现",
                        "metadata 集成测试",
                        "com.bone.test.IntegrationImpl");
        extension.setBizCode("TEST");
        extension.setConfig("{\"traffic\":80,\"condition\":\"#data != null\"}");
        extensionStore.save(extension);

        assertFalse(extPointStore.findAll().isEmpty());
        assertEquals(1, extensionStore.findByExtPointId(point.getId()).size());

        ExtPoint loaded = extPointStore.findByInterfaceName("com.bone.test.IntegrationExtPoint");
        assertNotNull(loaded);
        assertEquals("集成测试扩展点", loaded.getName());

        extension.setEnabled(false);
        extensionStore.update(extension);
        Extension reloaded = extensionStore.findById(extension.getId());
        assertNotNull(reloaded);
        assertFalse(reloaded.isEnabled());
        assertTrue(reloaded.getConfig().contains("traffic"));
    }
}
