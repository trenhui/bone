package com.bone.masterdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.BizException;
import com.bone.core.tenant.context.TenantContext;
import com.bone.masterdata.application.command.CreateReferenceSetCommand;
import com.bone.masterdata.application.command.CreateReferenceValueCommand;
import com.bone.masterdata.application.query.dto.ReferenceValueView;
import com.bone.masterdata.domain.repository.ReferenceSetRepository;
import com.bone.masterdata.domain.repository.ReferenceValueRepository;
import com.bone.masterdata.domain.repository.TenantReferenceValueRepository;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import java.util.List;
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
 * 参考数据 overlay 拆分回归（2026-09-26 裁决，多租户规范 §8 约束 6）：
 *
 * <ol>
 *   <li>值域是平台全局目录：租户上下文可读；租户写值域被 403 守卫（{@code MD_REF_PLATFORM_SET_IMMUTABLE}）；
 *   <li>平台值（tenant_id=0）对租户可见（overlay 合并读）；
 *   <li>租户建值落私有值表（tenant_id=1001），他租户（999）私有值不可见；
 *   <li>值编码在值域内跨两层全局唯一：租户私有值重码平台值被 409；
 *   <li>租户修改/停用平台值被 403 守卫（{@code MD_REF_PLATFORM_VALUE_IMMUTABLE}）。
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
@Transactional
public class ReferenceDataOverlayTest {

  private static final long TENANT_ID = 1001L;
  private static final long OTHER_TENANT_ID = 999L;
  private static final long PLATFORM_SET_ID = 800000000000000001L;
  private static final long PLATFORM_VALUE_ID = 800000000000000002L;

  @Autowired private ReferenceDataApplicationService referenceService;
  @Autowired private ReferenceSetRepository setRepository;
  @Autowired private ReferenceValueRepository platformValueRepository;
  @Autowired private TenantReferenceValueRepository tenantValueRepository;
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

  private void seedPlatformCatalog() {
    jdbcTemplate.update(
        "INSERT INTO mdm_reference_set (id, tenant_id, set_code, set_name, status) "
            + "VALUES (?, 0, 'INDUSTRY', '行业分类', 'PUBLISHED')",
        PLATFORM_SET_ID);
    jdbcTemplate.update(
        "INSERT INTO mdm_reference_value (id, tenant_id, set_id, value_code, value_name,"
            + " sort_order) VALUES (?, 0, ?, 'MANU', '制造业', 1)",
        PLATFORM_VALUE_ID,
        PLATFORM_SET_ID);
  }

  private CreateReferenceValueCommand valueCmd(String code) {
    CreateReferenceValueCommand cmd = new CreateReferenceValueCommand();
    cmd.setSetId(PLATFORM_SET_ID);
    cmd.setValueCode(code);
    cmd.setValueName("值-" + code);
    return cmd;
  }

  @Test
  public void platformCatalogVisibleAndTenantValueOverlayed() {
    seedPlatformCatalog();

    // 1) 租户读平台值域目录（非租户作用域聚合）
    assertTrue(setRepository.findById(PLATFORM_SET_ID) != null, "租户必须能读到平台值域目录（去租户作用域后不被过滤）");

    // 2) 租户建私有扩展值 → 落租户表、归属当前租户
    Long tenantValueId = referenceService.createValue(valueCmd("TENANT_EXT_1"));
    assertNotNull(tenantValueId);
    var tenantValue = tenantValueRepository.findById(tenantValueId);
    assertNotNull(tenantValue);
    assertEquals(TENANT_ID, tenantValue.getTenantId());

    // 3) 合并读：平台值 + 当前租户私有值，scope 标识来源
    List<ReferenceValueView> merged = referenceService.values(PLATFORM_SET_ID);
    assertEquals(2, merged.size());
    ReferenceValueView platformRow =
        merged.stream().filter(v -> "MANU".equals(v.getValueCode())).findFirst().orElseThrow();
    ReferenceValueView tenantRow =
        merged.stream()
            .filter(v -> "TENANT_EXT_1".equals(v.getValueCode()))
            .findFirst()
            .orElseThrow();
    assertEquals(ReferenceValueView.SCOPE_PLATFORM, platformRow.getScope());
    assertEquals(ReferenceValueView.SCOPE_TENANT, tenantRow.getScope());
  }

  @Test
  public void otherTenantPrivateValueInvisible() {
    seedPlatformCatalog();
    jdbcTemplate.update(
        "INSERT INTO mdm_reference_value_tenant (id, tenant_id, set_id, value_code, value_name,"
            + " sort_order) VALUES (?, ?, ?, 'OTHER_TENANT_VAL', '他租户私有值', 9)",
        800000000000000003L,
        OTHER_TENANT_ID,
        PLATFORM_SET_ID);

    List<ReferenceValueView> merged = referenceService.values(PLATFORM_SET_ID);
    assertEquals(1, merged.size(), "他租户私有值必须不可见，仅平台值可见");
    assertEquals("MANU", merged.get(0).getValueCode());
  }

  @Test
  public void tenantValueCodeMustNotDuplicatePlatformCode() {
    seedPlatformCatalog();

    BizException ex =
        assertThrows(BizException.class, () -> referenceService.createValue(valueCmd("MANU")));
    assertTrue(String.valueOf(ex.getMessage()).contains("MD_REF_VALUE_DUPLICATE"));
  }

  @Test
  public void tenantCannotWritePlatformCatalog() {
    seedPlatformCatalog();

    CreateReferenceSetCommand setCmd = new CreateReferenceSetCommand();
    setCmd.setSetCode("HACK_SET");
    setCmd.setSetName("越权建值域");

    BizException ex = assertThrows(BizException.class, () -> referenceService.createSet(setCmd));
    assertTrue(String.valueOf(ex.getMessage()).contains("MD_REF_PLATFORM_SET_IMMUTABLE"));

    BizException disableEx =
        assertThrows(BizException.class, () -> referenceService.disableValue(PLATFORM_VALUE_ID));
    assertTrue(String.valueOf(disableEx.getMessage()).contains("MD_REF_PLATFORM_VALUE_IMMUTABLE"));
  }

  @Test
  public void platformScopeWritesPlatformTable() {
    seedPlatformCatalog();
    TenantContext.setTenantId(0L);

    // 平台管理员（tenant=0）建值 → 平台值表
    Long platformValueId = referenceService.createValue(valueCmd("AGRI"));
    assertNotNull(platformValueRepository.findById(platformValueId), "平台建值必须落平台值表");

    List<ReferenceValueView> merged = referenceService.values(PLATFORM_SET_ID);
    assertEquals(2, merged.size(), "平台视角合并：原平台值 + 新平台值");
    assertTrue(
        merged.stream().allMatch(v -> ReferenceValueView.SCOPE_PLATFORM.equals(v.getScope())));

    // 平台管理员不可触达租户私有值（不同表，仓储天然隔离）
    Long tenantValueId = 800000000000000009L;
    jdbcTemplate.update(
        "INSERT INTO mdm_reference_value_tenant (id, tenant_id, set_id, value_code, value_name,"
            + " sort_order) VALUES (?, 1001, ?, 'TENANT_ONLY', '租户私有', 9)",
        tenantValueId,
        PLATFORM_SET_ID);
    BizException ex =
        assertThrows(BizException.class, () -> referenceService.disableValue(tenantValueId));
    assertTrue(String.valueOf(ex.getMessage()).contains("MD_REF_SET_NOT_FOUND"));
  }
}
