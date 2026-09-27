package com.bone.engine.extension.studio.infrastructure.persistence.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.engine.extension.studio.domain.model.audit.StudioAuditEntry;
import com.bone.engine.extension.studio.domain.model.execution.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.model.plugin.PluginVersion;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioAuditLog;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionImpl;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudioPersistenceConverterTest {

  @Test
  void roundTripExtPoint() {
    ExtPoint domain = new ExtPoint();
    domain.setId(1L);
    domain.setName("支付扩展点");
    domain.setInterfaceName("com.bone.PaymentExtPoint");
    domain.setDomain("payment");
    domain.setEnabled(true);

    ExtStudioExtensionPoint row = StudioPersistenceConverter.toEntity(domain);
    assertEquals("com.bone.PaymentExtPoint", row.getPointCode());

    ExtPoint back = StudioPersistenceConverter.toDomain(row);
    assertEquals(domain.getName(), back.getName());
    assertTrue(back.isEnabled());
  }

  @Test
  void roundTripExtensionWithConfig() {
    Extension domain = Extension.create(10L, "VIP", "desc", "com.bone.VipExtension");
    domain.setId(2L);
    domain.setConfig("{\"traffic\":50,\"defaultImpl\":true}");
    domain.setPriority(10);

    ExtStudioExtensionImpl row = StudioPersistenceConverter.toEntity(domain);
    assertEquals(50, row.getRolloutPercent());
    assertTrue(row.getIsDefault());

    Extension back = StudioPersistenceConverter.toDomain(row);
    assertEquals("VIP", back.getName());
    assertEquals(10, back.getPriority());
  }

  @Test
  void roundTripAuditEntry() {
    StudioAuditEntry entry = new StudioAuditEntry();
    entry.setTraceId("trace-1");
    entry.setTenantId(0L);
    entry.setUserId("u1");
    entry.setAction("plugin.deploy");
    entry.setResourceType("plugin");
    entry.setResourceId("1");
    entry.setResult("SUCCESS");

    ExtStudioAuditLog row = StudioPersistenceConverter.toAuditLogEntity(entry);
    assertEquals("plugin.deploy", row.getAction());

    StudioAuditEntry back = StudioPersistenceConverter.toAuditDomain(row);
    assertEquals("trace-1", back.getTraceId());
    assertEquals("SUCCESS", back.getResult());
  }

  @Test
  @DisplayName("X-1：插件版本行必须带当前租户（曾恒写 tenant_id=0，被 SDK 租户过滤挡掉）")
  void pluginVersionRowCarriesCurrentTenant() {
    TenantContext.setTenantId(1001L);
    try {
      PluginVersion domain = new PluginVersion();
      domain.setId(9L);
      domain.setPluginId(1L);
      domain.setVersion("1.0.0");

      assertEquals(1001L, StudioPersistenceConverter.toPluginVersionEntity(domain).getTenantId());
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  @DisplayName("X-1：执行日志 / 审计日志同样落当前租户")
  void executionLogAndAuditLogCarryCurrentTenant() {
    TenantContext.setTenantId(1002L);
    try {
      PluginExecutionLog log = new PluginExecutionLog();
      log.setId(1L);
      log.setPluginId(2L);
      assertEquals(1002L, StudioPersistenceConverter.toExecutionLogEntity(log).getTenantId());

      StudioAuditEntry entry = new StudioAuditEntry();
      entry.setAction("plugin.deploy");
      assertEquals(1002L, StudioPersistenceConverter.toAuditLogEntity(entry).getTenantId());
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  @DisplayName("X-1：显式携带的租户不被上下文覆盖（平台侧代租户写 / 日志自带来源租户）")
  void explicitTenantIsPreserved() {
    TenantContext.setTenantId(1001L);
    try {
      PluginExecutionLog log = new PluginExecutionLog();
      log.setTenantId(2002L);
      assertEquals(2002L, StudioPersistenceConverter.toExecutionLogEntity(log).getTenantId());
    } finally {
      TenantContext.clear();
    }
  }
}
