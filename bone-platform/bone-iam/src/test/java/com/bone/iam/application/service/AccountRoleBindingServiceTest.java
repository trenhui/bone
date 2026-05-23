package com.bone.iam.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.iam.domain.repository.AccountRoleRepository;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountRoleBindingServiceTest {

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private AccountAuthorityCache accountAuthorityCache;

    @Mock
    private SqlExecutor sqlExecutor;

    @InjectMocks
    private AccountRoleBindingService accountRoleBindingService;

    @Test
    void replaceBindingsDeletesThenBatchInserts() {
        when(accountRoleRepository.getSqlExecutor()).thenReturn(sqlExecutor);

        accountRoleBindingService.replaceBindings(100L, 0L, new Long[] {1L, 2L});

        verify(sqlExecutor).delete(anyString(), eq(100L));
        verify(accountRoleRepository).batchInsert(any());
        verify(accountAuthorityCache).evictAccount(100L);
    }

    @Test
    void replaceBindingsSkipsInsertWhenRoleIdsEmpty() {
        when(accountRoleRepository.getSqlExecutor()).thenReturn(sqlExecutor);

        accountRoleBindingService.replaceBindings(100L, 0L, new Long[0]);

        verify(sqlExecutor).delete(anyString(), eq(100L));
        verify(accountRoleRepository, never()).batchInsert(any());
        verify(accountAuthorityCache).evictAccount(100L);
    }

}
