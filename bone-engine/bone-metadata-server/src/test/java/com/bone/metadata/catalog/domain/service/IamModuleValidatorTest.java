package com.bone.metadata.catalog.domain.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.iam.IamAppRoleRef;
import com.bone.metadata.catalog.domain.model.iam.IamModuleRef;
import com.bone.metadata.catalog.domain.repository.IamAppRoleRepository;
import com.bone.metadata.catalog.domain.repository.IamModuleRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IamModuleValidatorTest {

  private static final long CURRENT_TENANT = 1L;

  @Mock private IamModuleRepository iamModuleRepository;
  @Mock private IamAppRoleRepository iamAppRoleRepository;
  @Mock private TenantProvider tenantProvider;
  @InjectMocks private IamModuleValidator validator;

  private static IamModuleRef moduleRef(long tenantId, long appId) {
    IamModuleRef ref = new IamModuleRef();
    ReflectionTestUtils.setField(ref, "tenantId", tenantId);
    ReflectionTestUtils.setField(ref, "appId", appId);
    return ref;
  }

  private static IamAppRoleRef roleRef(String role) {
    IamAppRoleRef ref = new IamAppRoleRef();
    ReflectionTestUtils.setField(ref, "appId", 10L);
    ReflectionTestUtils.setField(ref, "userId", 7L);
    ReflectionTestUtils.setField(ref, "role", role);
    return ref;
  }

  @Test
  void requireExists_shouldPass_whenModuleFound() {
    when(tenantProvider.currentTenantId()).thenReturn(CURRENT_TENANT);
    when(iamModuleRepository.findById(1L)).thenReturn(moduleRef(CURRENT_TENANT, 10L));
    assertDoesNotThrow(() -> validator.requireExists(1L));
  }

  @Test
  void requireExists_shouldThrow_whenModuleMissing() {
    when(iamModuleRepository.findById(999L)).thenReturn(null);
    BizException ex = assertThrows(BizException.class, () -> validator.requireExists(999L));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("所属模块不存在");
  }

  @Test
  void requireExists_shouldThrow_whenModuleIdNull() {
    BizException ex = assertThrows(BizException.class, () -> validator.requireExists(null));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("模块ID不能为空");
  }

  /** 跨租户归属：SDK 读侧虽会自动过滤 tenant_id（ADR-0029），但那只是隐式防线。本用例固化「显式断言」契约——即便有人绕过自动过滤，校验器仍必须拒绝。 */
  @Test
  void requireExists_shouldThrow_whenModuleBelongsToAnotherTenant() {
    when(tenantProvider.currentTenantId()).thenReturn(CURRENT_TENANT);
    when(iamModuleRepository.findById(2L)).thenReturn(moduleRef(99L, 10L));
    BizException ex = assertThrows(BizException.class, () -> validator.requireExists(2L));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage())
        .contains("所属模块不属于当前租户")
        .contains("模块租户=99");
  }

  /** 租户信息缺失（仅可能来自非真实行的桩数据）：不可判定即拒绝，不放行。 */
  @Test
  void requireExists_shouldThrow_whenModuleTenantMissing() {
    when(tenantProvider.currentTenantId()).thenReturn(CURRENT_TENANT);
    when(iamModuleRepository.findById(3L)).thenReturn(new IamModuleRef());
    BizException ex = assertThrows(BizException.class, () -> validator.requireExists(3L));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("所属模块不属于当前租户");
  }

  // ===================== G1②：应用角色校验（requireModelingAllowed） =====================

  @Test
  void requireModelingAllowed_shouldPass_whenUserIsAdmin() {
    when(tenantProvider.currentTenantId()).thenReturn(CURRENT_TENANT);
    when(iamModuleRepository.findById(1L)).thenReturn(moduleRef(CURRENT_TENANT, 10L));
    when(iamAppRoleRepository.findByAppAndUser(10L, 7L)).thenReturn(Optional.of(roleRef("ADMIN")));
    assertDoesNotThrow(() -> validator.requireModelingAllowed(1L, 7L));
  }

  @Test
  void requireModelingAllowed_shouldPass_whenUserIsDeveloper() {
    when(tenantProvider.currentTenantId()).thenReturn(CURRENT_TENANT);
    when(iamModuleRepository.findById(1L)).thenReturn(moduleRef(CURRENT_TENANT, 10L));
    when(iamAppRoleRepository.findByAppAndUser(10L, 7L))
        .thenReturn(Optional.of(roleRef("DEVELOPER")));
    assertDoesNotThrow(() -> validator.requireModelingAllowed(1L, 7L));
  }

  @Test
  void requireModelingAllowed_shouldThrow403_whenUserIsViewer() {
    when(tenantProvider.currentTenantId()).thenReturn(CURRENT_TENANT);
    when(iamModuleRepository.findById(1L)).thenReturn(moduleRef(CURRENT_TENANT, 10L));
    when(iamAppRoleRepository.findByAppAndUser(10L, 7L)).thenReturn(Optional.of(roleRef("VIEWER")));
    BizException ex =
        assertThrows(BizException.class, () -> validator.requireModelingAllowed(1L, 7L));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("ADMIN 或 DEVELOPER");
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("VIEWER");
  }

  @Test
  void requireModelingAllowed_shouldThrow403_whenUserHasNoAppRole() {
    when(tenantProvider.currentTenantId()).thenReturn(CURRENT_TENANT);
    when(iamModuleRepository.findById(1L)).thenReturn(moduleRef(CURRENT_TENANT, 10L));
    when(iamAppRoleRepository.findByAppAndUser(10L, 7L)).thenReturn(Optional.empty());
    BizException ex =
        assertThrows(BizException.class, () -> validator.requireModelingAllowed(1L, 7L));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("未被授予任何应用角色");
  }

  /** 无主体场景（E2E 安全关闭 / API-Key / 定时任务）：按「平台运维通道」豁免角色校验（2a §10.4 裁定）。 */
  @Test
  void requireModelingAllowed_shouldExempt_whenNoPrincipal() {
    when(tenantProvider.currentTenantId()).thenReturn(CURRENT_TENANT);
    when(iamModuleRepository.findById(1L)).thenReturn(moduleRef(CURRENT_TENANT, 10L));
    assertDoesNotThrow(() -> validator.requireModelingAllowed(1L, null));
    org.mockito.Mockito.verifyNoInteractions(iamAppRoleRepository);
  }

  /** 无主体豁免不免除存在性/租户校验：模块不存在仍须拒绝。 */
  @Test
  void requireModelingAllowed_shouldStillReject_whenNoPrincipalAndModuleMissing() {
    when(iamModuleRepository.findById(999L)).thenReturn(null);
    BizException ex =
        assertThrows(BizException.class, () -> validator.requireModelingAllowed(999L, null));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("所属模块不存在");
  }
}
