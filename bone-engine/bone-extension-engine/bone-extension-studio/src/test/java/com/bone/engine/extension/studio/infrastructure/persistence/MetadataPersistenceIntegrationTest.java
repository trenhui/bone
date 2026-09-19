package com.bone.engine.extension.studio.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.engine.extension.studio.domain.gateway.ExtPointReadPort;
import com.bone.engine.extension.studio.domain.gateway.ExtensionReadPort;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.StudioAuditEntry;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import com.bone.engine.extension.studio.domain.repository.StudioAuditRepository;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioAuditLog;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioAuditLogRepository;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioExtensionImplRepository;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioExtensionPointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = com.bone.engine.extension.studio.support.MetadataPersistenceTestApplication.class)
@ActiveProfiles("test")
class MetadataPersistenceIntegrationTest {

  @Autowired private ExtPointRepository extPointPort;

  @Autowired private ExtPointReadPort extPointReadPort;

  @Autowired private ExtensionRepository extensionPort;

  @Autowired private ExtensionReadPort extensionReadPort;

  @Autowired private ExtStudioExtensionPointRepository extStudioExtPointRepository;

  @Autowired private ExtStudioExtensionImplRepository extStudioExtensionRepository;

  @Autowired private StudioAuditRepository auditRepository;

  @Autowired private ExtStudioAuditLogRepository auditLogRepository;

  @BeforeEach
  void setupTenant() {
    // 扩展点 / 审计表为租户表；测试无运行期租户上下文，模拟 JWT 提供的租户（否则 SDK 失败关闭）
    TenantContext.setTenantId(1L);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void contextLoadsRepositoriesAndStores() {
    assertNotNull(extPointPort);
    assertNotNull(extensionPort);
    assertNotNull(extStudioExtPointRepository);
    assertNotNull(extStudioExtensionRepository);
  }

  @Test
  void saveAndQueryRoundTripThroughMetadataSdk() {
    ExtPoint point = new ExtPoint();
    point.setName("集成测试扩展点");
    point.setInterfaceName("com.bone.test.IntegrationExtPoint");
    point.setDomain("test");
    point.setEnabled(true);
    extPointPort.save(point);
    assertNotNull(point.getId());

    Extension extension =
        Extension.create(point.getId(), "集成实现", "metadata 集成测试", "com.bone.test.IntegrationImpl");
    extension.setBizCode("TEST");
    extension.setConfig("{\"traffic\":80,\"condition\":\"#data != null\"}");
    extensionPort.save(extension);

    assertFalse(extPointReadPort.findAll().isEmpty());
    assertEquals(1, extensionReadPort.findByExtPointId(point.getId()).size());

    ExtPoint loaded = extPointPort.findByInterfaceName("com.bone.test.IntegrationExtPoint");
    assertNotNull(loaded);
    assertEquals("集成测试扩展点", loaded.getName());

    extension.setEnabled(false);
    extensionPort.save(extension);
    Extension reloaded = extensionPort.findById(extension.getId());
    assertNotNull(reloaded);
    assertFalse(reloaded.isEnabled());
    assertTrue(reloaded.getConfig().contains("traffic"));
  }

  @Test
  void auditLogPersistedThroughMetadataSdk() {
    StudioAuditEntry entry = new StudioAuditEntry();
    entry.setTraceId("meta-test-trace");
    entry.setAction("plugin.deploy");
    entry.setResourceType("plugin");
    entry.setResourceId("99");
    entry.setResult("SUCCESS");
    auditRepository.save(entry);
    assertNotNull(entry.getId());

    ExtStudioAuditLog row = auditLogRepository.findById(entry.getId());
    assertNotNull(row);
    assertEquals("plugin.deploy", row.getAction());
    assertEquals("meta-test-trace", row.getTraceId());
  }
}
