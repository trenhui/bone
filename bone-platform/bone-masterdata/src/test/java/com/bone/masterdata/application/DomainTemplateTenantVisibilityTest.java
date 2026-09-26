package com.bone.masterdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bone.core.tenant.context.TenantContext;
import com.bone.masterdata.application.command.InstantiateFromTemplateCommand;
import com.bone.masterdata.application.query.dto.DomainTemplateDTO;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.repository.DomainTemplateRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台模板跨租户可见性回归（2026-09-26 实测裁决）：
 *
 * <p>{@code mdm_domain_template} / {@code mdm_template_version} 是「平台只写、多方只读」的全局目录， 聚合不映射
 * tenant_id（非租户作用域）。本测试守护两点：
 *
 * <ol>
 *   <li>租户上下文（tenant_id=1001）可读 tenant_id=0 的平台模板行（详情 + 实例化不再 404）—— 若有人把聚合改回 {@code
 *       TenantAggregateRoot}，SDK Criteria 通道将注入严格租户过滤，本测试第一个断言即失败；
 *   <li>租户作用域聚合的隔离未被破坏（他租户 mdm_entity 行仍不可见）。
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
@Transactional
public class DomainTemplateTenantVisibilityTest {

  private static final long TENANT_ID = 1001L;
  private static final long OTHER_TENANT_ID = 999L;
  private static final long PLATFORM_TEMPLATE_ID = 758734067280642048L;

  @Autowired private DomainTemplateApplicationService templateService;
  @Autowired private DomainTemplateRepository templateRepository;
  @Autowired private MasterDataEntityRepository entityRepository;
  @Autowired private MasterDataFieldRepository fieldRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** 租户表写入依赖 TenantContext（HTTP 入口由 WebTenantConfiguration 注入），单测需自行装配。 */
  @BeforeEach
  void bindTenant() {
    TenantContext.setTenantId(TENANT_ID);
  }

  @AfterEach
  void unbindTenant() {
    TenantContext.setTenantId((Long) null);
  }

  private void seedPlatformTemplate() {
    jdbcTemplate.update(
        "INSERT INTO mdm_domain_template (id, tenant_id, domain_code, domain_name, "
            + "current_version, default_governance_tier, field_schema, status) "
            + "VALUES (?, 0, 'CUSTOMER', '客户域', '1.0.0', 'L2', ?, 'PUBLISHED')",
        PLATFORM_TEMPLATE_ID,
        "[{\"code\":\"cust_name\",\"name\":\"客户名称\",\"type\":\"STRING\",\"length\":64,\"required\":true},"
            + "{\"code\":\"cust_level\",\"name\":\"客户等级\",\"type\":\"STRING\",\"length\":16}]");
  }

  @Test
  public void tenantCanReadPlatformTemplateAndInstantiate() {
    seedPlatformTemplate();

    // 1) 仓储读：租户上下文下 findById 平台行不再被租户过滤拦掉（回归主断言）
    assertNotNull(templateRepository.findById(PLATFORM_TEMPLATE_ID), "租户必须能读到 tenant_id=0 平台模板行");

    // 2) 应用服务读
    DomainTemplateDTO dto = templateService.detail(PLATFORM_TEMPLATE_ID);
    assertEquals("CUSTOMER", dto.getDomainCode());
    assertEquals("PUBLISHED", dto.getStatus());

    // 3) 实例化：产物实体必须落到当前租户（tenant_id=1001）
    InstantiateFromTemplateCommand cmd = new InstantiateFromTemplateCommand();
    cmd.setTemplateId(PLATFORM_TEMPLATE_ID);
    cmd.setName("客户主数据");
    cmd.setEntityCode("CUST_FROM_TPL");
    Long entityId = templateService.instantiate(cmd);
    assertNotNull(entityId);

    MasterDataEntity entity = entityRepository.findById(entityId);
    assertNotNull(entity);
    assertEquals(TENANT_ID, entity.getTenantId());
    assertEquals("CUSTOMER", entity.getDomainCode());
    assertEquals(PLATFORM_TEMPLATE_ID, entity.getTemplateId());
    assertEquals(2, fieldRepository.findByMasterDataEntityId(entityId).size());
  }

  @Test
  public void tenantScopedAggregatesRemainIsolated() {
    // 对照组：租户作用域聚合（mdm_entity）的他租户行必须仍被过滤（Criteria 通道注入未回退）
    jdbcTemplate.update(
        "INSERT INTO mdm_entity (id, tenant_id, entity_code, entity_name, status) "
            + "VALUES (?, ?, 'CUST_OTHER_TENANT', '他租户实体', 'DRAFT')",
        900000000000000001L,
        OTHER_TENANT_ID);

    assertNull(entityRepository.findById(900000000000000001L), "他租户实体行对当前租户必须不可见");
  }
}
