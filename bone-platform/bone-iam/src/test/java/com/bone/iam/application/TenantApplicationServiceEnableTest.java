package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.TenantDeletionGateway;
import com.bone.iam.domain.model.tenant.Tenant;
import com.bone.iam.domain.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TenantApplicationService#enable 单元测试（原 EnableTenantCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceEnableTest {

  @Mock TenantRepository tenantRepository;

  @Mock TenantDeletionGateway tenantDeletionGateway;

  @InjectMocks TenantApplicationService tenantApplicationService;

  @Test
  void enableTenantSuccessfully() {
    Tenant tenant = Tenant.create(100L, "Acme", "ACME", 0, "");
    when(tenantRepository.findById(100L)).thenReturn(tenant);

    tenantApplicationService.enable(100L);

    verify(tenantRepository, times(1)).save(tenant);
  }

  @Test
  void enableNonExistentTenantThrowsNotFound() {
    when(tenantRepository.findById(999L)).thenReturn(null);

    assertThatThrownBy(() -> tenantApplicationService.enable(999L))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessageContaining(IamErrorCodes.TENANT_NOT_FOUND);
  }
}
