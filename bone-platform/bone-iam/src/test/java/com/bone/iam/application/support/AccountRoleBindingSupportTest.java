package com.bone.iam.application.support;

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
class AccountRoleBindingSupportTest {

  @Mock private AccountRoleRepository accountRoleRepository;

  @Mock private AccountAuthorityCache accountAuthorityCache;

  @InjectMocks private AccountRoleBindingSupport accountRoleBindingSupport;

  @Test
  void replaceBindingsDelegatesToRepositoryAndEvictsCache() {
    accountRoleBindingSupport.replaceBindings(100L, 0L, new Long[] {1L, 2L});

    verify(accountRoleRepository).replaceBindingsForAccount(any(), any(), any());
    verify(accountAuthorityCache).evictAccount(100L);
  }

  @Test
  void replaceBindingsSkipsWhenAccountIdNull() {
    accountRoleBindingSupport.replaceBindings(null, 0L, new Long[] {1L, 2L});

    verify(accountRoleRepository, never()).replaceBindingsForAccount(any(), any(), any());
    verify(accountAuthorityCache, never()).evictAccount(any());
  }
}
