package com.bone.iam.application.binding;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.repository.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RolePermissionBindingServiceTest {

  @Mock private RolePermissionRepository rolePermissionRepository;

  @Mock private AccountAuthorityCache accountAuthorityCache;

  @InjectMocks private RolePermissionBindingService rolePermissionBindingService;

  @Test
  void replaceBindingsDelegatesToRepositoryAndEvictsCache() {
    rolePermissionBindingService.replaceBindings(1L, new Long[] {10L, 11L});

    verify(rolePermissionRepository).replaceBindingsForRole(any(), any());
    verify(accountAuthorityCache).evictAccountsForRole(1L);
  }

  @Test
  void replaceBindingsSkipsWhenRoleIdNull() {
    rolePermissionBindingService.replaceBindings(null, new Long[] {10L, 11L});

    verify(rolePermissionRepository, never()).replaceBindingsForRole(any(), any());
    verify(accountAuthorityCache, never()).evictAccountsForRole(any());
  }
}
