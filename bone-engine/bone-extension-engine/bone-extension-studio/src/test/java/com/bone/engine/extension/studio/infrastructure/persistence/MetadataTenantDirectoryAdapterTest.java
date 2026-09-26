package com.bone.engine.extension.studio.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.studio.domain.gateway.TenantDirectoryPort;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtIamTenantDirectory;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtIamTenantDirectoryRepository;
import com.bone.engine.extension.studio.support.MetadataPersistenceTestApplication;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 5a G2：metadata 模式下租户目录存在性校验（H2 镜像 iam_tenant）。
 *
 * <p>真实场景：登记插件时 tenantCode 必须是 {@code *} 或平台真实租户编码；填错码应被目录判为不存在， 由 ExtensionValidationSupport
 * 拒绝，消除「填错码 → 永不命中 → 靠默认实现掩盖」的静默故障。
 */
@SpringBootTest(classes = MetadataPersistenceTestApplication.class)
@ActiveProfiles("test")
class MetadataTenantDirectoryAdapterTest {

  @Autowired private TenantDirectoryPort tenantDirectoryPort;

  @Autowired private ExtIamTenantDirectoryRepository tenantRepository;

  private void seedTenant(long id, String code) {
    ExtIamTenantDirectory row = new ExtIamTenantDirectory();
    row.setId(id);
    row.setCode(code);
    row.setName("租户-" + code);
    Date now = new Date();
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    row.setDeleted(false);
    tenantRepository.insert(row);
  }

  @Test
  void existsReturnsTrueForSeededTenantAndFalseForMissing() {
    seedTenant(990001L, "T1001");
    seedTenant(990002L, "T1002");

    assertNotNull(tenantDirectoryPort);
    assertTrue(tenantDirectoryPort.exists("T1001"));
    assertTrue(tenantDirectoryPort.exists("T1002"));
    assertFalse(tenantDirectoryPort.exists("NO_SUCH_TENANT"));
  }

  @Test
  void adapterPresentInMetadataMode() {
    // metadata 模式必须装配 TenantDirectoryPort（in-memory 联调模式才降级为格式校验）
    assertTrue(
        tenantDirectoryPort instanceof MetadataTenantDirectoryAdapter,
        "metadata 模式应注入 MetadataTenantDirectoryAdapter");
  }

  @Test
  void blankCodeIsNotTreatedAsExisting() {
    assertFalse(tenantDirectoryPort.exists("  "));
    assertFalse(tenantDirectoryPort.exists(null));
  }
}
