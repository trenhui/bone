package com.bone.iam.application.command.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.NotFoundException;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisableTenantCommandHandlerTest {

  @Mock TenantRepository tenantRepository;

  DisableTenantCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DisableTenantCommandHandler(tenantRepository);
  }

  @Test
  void disableTenantSuccessfully() {
    Tenant tenant = Tenant.create(100L, "Acme", "ACME", 0, "");
    when(tenantRepository.findById(100L)).thenReturn(tenant);

    handler.handle(100L);

    verify(tenantRepository, times(1)).save(tenant);
  }

  @Test
  void disableNonExistentTenantThrowsNotFound() {
    when(tenantRepository.findById(999L)).thenReturn(null);

    assertThatThrownBy(() -> handler.handle(999L)).isInstanceOf(NotFoundException.class);
  }
}
