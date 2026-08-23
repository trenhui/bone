package com.bone.iam.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.repository.AccountRoleRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
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
  void replaceBindingsDeletesThenBatchInserts() {
    accountRoleBindingService.replaceBindings(100L, 0L, new Long[] {1L, 2L});

    verify(accountRoleRepository).deleteByCriteria(any(Criteria.class));
    verify(accountRoleRepository).batchInsert(any());
    verify(accountAuthorityCache).evictAccount(100L);
  }

  @Test
  void replaceBindingsSkipsInsertWhenRoleIdsEmpty() {
    accountRoleBindingService.replaceBindings(100L, 0L, new Long[0]);

    verify(accountRoleRepository).deleteByCriteria(any(Criteria.class));
    verify(accountRoleRepository, never()).batchInsert(any());
    verify(accountAuthorityCache).evictAccount(100L);
  }
}
