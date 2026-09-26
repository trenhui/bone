package com.bone.iam.adapter.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bone.iam.domain.gateway.TenantProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 平台域访问侧护栏：仅平台租户（tenantId=0）放行，租户用户与无上下文请求一律拒绝。 */
@ExtendWith(MockitoExtension.class)
class PlatformAccessGuardTest {

  @Mock private TenantProvider tenantProvider;

  @Test
  void platformTenantIsAllowed() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(0L);
    assertThat(new PlatformAccessGuard(tenantProvider).isPlatformAdmin()).isTrue();
  }

  @Test
  void tenantUserIsRejected() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(1001L);
    assertThat(new PlatformAccessGuard(tenantProvider).isPlatformAdmin()).isFalse();
  }

  /** fail-closed：租户上下文缺失不得放行，未认证请求不应触达平台域数据。 */
  @Test
  void missingTenantContextIsRejected() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(null);
    assertThat(new PlatformAccessGuard(tenantProvider).isPlatformAdmin()).isFalse();
  }
}
