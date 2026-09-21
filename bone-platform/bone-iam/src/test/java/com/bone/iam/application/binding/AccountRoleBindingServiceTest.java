package com.bone.iam.application.binding;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.repository.AccountRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountRoleBindingServiceTest {

  @Mock private AccountRoleRepository accountRoleRepository;

  @Mock private AccountAuthorityCache accountAuthorityCache;

  @InjectMocks private AccountRoleBindingService accountRoleBindingService;

  @Test
  void replaceBindingsDelegatesToRepositoryAndEvictsCache() {
    accountRoleBindingService.replaceBindings(100L, 0L, new Long[] {1L, 2L});

    verify(accountRoleRepository).replaceBindingsForAccount(any(), any(), any());
    verify(accountAuthorityCache).evictAccount(100L);
  }

  @Test
  void replaceBindingsSkipsWhenAccountIdNull() {
    accountRoleBindingService.replaceBindings(null, 0L, new Long[] {1L, 2L});

    verify(accountRoleRepository, never()).replaceBindingsForAccount(any(), any(), any());
    verify(accountAuthorityCache, never()).evictAccount(any());
  }
}
