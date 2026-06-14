package com.bone.iam.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.repository.RolePermissionRepository;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RolePermissionBindingServiceTest {

  @Mock private RolePermissionRepository rolePermissionRepository;

  @Mock private AccountAuthorityCache accountAuthorityCache;

  @Mock private SqlExecutor sqlExecutor;

  @InjectMocks private RolePermissionBindingService rolePermissionBindingService;

  @Test
  void replaceBindingsDeletesInsertsAndEvictsCache() {
    when(rolePermissionRepository.getSqlExecutor()).thenReturn(sqlExecutor);

    rolePermissionBindingService.replaceBindings(1L, new Long[] {10L, 11L});

    verify(sqlExecutor).delete(anyString(), eq(1L));
    verify(rolePermissionRepository).batchInsert(any());
    verify(accountAuthorityCache).evictAccountsForRole(1L);
  }

  @Test
  void replaceBindingsEmptyStillEvictsCache() {
    when(rolePermissionRepository.getSqlExecutor()).thenReturn(sqlExecutor);

    rolePermissionBindingService.replaceBindings(2L, new Long[0]);

    verify(sqlExecutor).delete(anyString(), eq(2L));
    verify(rolePermissionRepository, never()).batchInsert(any());
    verify(accountAuthorityCache).evictAccountsForRole(2L);
  }
}
