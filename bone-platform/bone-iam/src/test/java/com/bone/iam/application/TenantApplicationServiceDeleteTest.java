package com.bone.iam.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.domain.gateway.TenantDeletionGateway;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TenantApplicationService#delete 单元测试（原 DeleteTenantCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceDeleteTest {

  @Mock private TenantRepository tenantRepository;

  @Mock private TenantDeletionGateway tenantDeletionGateway;

  @InjectMocks private TenantApplicationService tenantApplicationService;

  @Test
  void deletesTenantAfterPurge() {
    when(tenantRepository.findById(10L)).thenReturn(Tenant.create(10L, "t", "T10", 1, "a@t.com"));

    tenantApplicationService.delete(10L);

    verify(tenantDeletionGateway).purgeTenantData(10L);
    verify(tenantRepository).deleteById(10L);
  }

  @Test
  void rejectsPlatformTenantDelete() {
    assertThrows(BizException.class, () -> tenantApplicationService.delete(0L));
    verify(tenantDeletionGateway, never()).purgeTenantData(0L);
  }
}
