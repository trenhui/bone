package com.bone.iam.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.repository.RolePermissionRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
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
  void replaceBindingsDeletesInsertsAndEvictsCache() {
    rolePermissionBindingService.replaceBindings(1L, new Long[] {10L, 11L});

    verify(rolePermissionRepository).deleteByCriteria(any(Criteria.class));
    verify(rolePermissionRepository).batchInsert(any());
    verify(accountAuthorityCache).evictAccountsForRole(1L);
  }

  @Test
  void replaceBindingsEmptyStillEvictsCache() {
    rolePermissionBindingService.replaceBindings(2L, new Long[0]);

    verify(rolePermissionRepository).deleteByCriteria(any(Criteria.class));
    verify(rolePermissionRepository, never()).batchInsert(any());
    verify(accountAuthorityCache).evictAccountsForRole(2L);
  }
}
