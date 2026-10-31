package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.iam.application.query.dto.AccountOrgHierarchyDTO;
import com.bone.iam.application.query.mapper.AccountDtoMapper;
import com.bone.iam.application.support.AccountRoleBindingSupport;
import com.bone.iam.application.support.PasswordPolicyValidator;
import com.bone.iam.application.support.TenantQuotaEnforcer;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.model.dept.Dept;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.DeptRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link AccountApplicationService#orgHierarchy(Long)} 的契约测试——它是审批引擎 {@code OrgHierarchyProvider}
 * 的唯一取数口。
 *
 * <p><b>为什么这条契约值得单独锁住</b>：三个字段的<b>空值必须如实透出</b>。如果这里为了「好看」 把 null
 * 兜成某个默认人（比如部门负责人退回直属主管、或退回部门内第一个账号），审批就会静默路由到 一个本不该审的人手上——错误不会报，单据照常流转，这是审批系统最危险的故障形态。
 */
class AccountApplicationServiceOrgHierarchyTest {

  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final DeptRepository deptRepository = mock(DeptRepository.class);

  private AccountApplicationService service() {
    return new AccountApplicationService(
        accountRepository,
        mock(PasswordEncoder.class),
        mock(AccountRoleBindingSupport.class),
        mock(PasswordPolicyValidator.class),
        mock(TenantQuotaEnforcer.class),
        mock(AccountDtoMapper.class),
        mock(TenantProvider.class),
        deptRepository);
  }

  private static Account account(long id, Long deptId, Long managerId) {
    Account account =
        Account.create(
            id, Username.of("u" + id), "hash", Email.of("u" + id + "@bone.local"), null, "张三", 0L);
    if (deptId != null) {
      account.changeDept(deptId);
    }
    if (managerId != null) {
      account.changeManager(managerId);
    }
    return account;
  }

  private static Dept dept(long id, Long leaderId) {
    // 部门 id 由存储层生成，内存构造的 Dept 其 id 为 null —— 与既有部门树测试同款做法：mock 出 id。
    Dept dept = mock(Dept.class);
    when(dept.getId()).thenReturn(id);
    when(dept.getLeaderId()).thenReturn(leaderId);
    return dept;
  }

  @Test
  @DisplayName("直属主管与部门负责人分别取自各自的数据源，不会互相顶替")
  void returnsManagerAndDeptHeadFromSeparateSources() {
    when(accountRepository.findById(9104L)).thenReturn(account(9104L, 100L, 9103L));
    Dept dept = dept(100L, 9200L);
    when(deptRepository.findById(100L)).thenReturn(dept);

    Optional<AccountOrgHierarchyDTO> dto = service().orgHierarchy(9104L);

    assertThat(dto).isPresent();
    assertThat(dto.get().getDeptId()).isEqualTo(100L);
    assertThat(dto.get().getLineManagerId()).isEqualTo(9103L);
    assertThat(dto.get().getDeptHeadId()).isEqualTo(9200L);
  }

  @Test
  @DisplayName("没有主管时如实返回 null（引擎据此挂起，不是退回部门负责人）")
  void missingManagerStaysNull() {
    when(accountRepository.findById(9104L)).thenReturn(account(9104L, 100L, null));
    Dept dept = dept(100L, 9200L);
    when(deptRepository.findById(100L)).thenReturn(dept);

    AccountOrgHierarchyDTO dto = service().orgHierarchy(9104L).orElseThrow();

    assertThat(dto.getLineManagerId()).isNull();
    assertThat(dto.getDeptHeadId()).isEqualTo(9200L);
  }

  @Test
  @DisplayName("部门未指定负责人时 deptHead 为 null，且不会拿直属主管顶上")
  void missingDeptHeadStaysNull() {
    when(accountRepository.findById(9104L)).thenReturn(account(9104L, 100L, 9103L));
    Dept dept = dept(100L, null);
    when(deptRepository.findById(100L)).thenReturn(dept);

    AccountOrgHierarchyDTO dto = service().orgHierarchy(9104L).orElseThrow();

    assertThat(dto.getLineManagerId()).isEqualTo(9103L);
    assertThat(dto.getDeptHeadId()).isNull();
  }

  @Test
  @DisplayName("账号不存在返回 empty，且不白查一次部门")
  void unknownAccountReturnsEmpty() {
    when(accountRepository.findById(404L)).thenReturn(null);

    assertThat(service().orgHierarchy(404L)).isEmpty();
    verifyNoInteractions(deptRepository);
  }

  @Test
  @DisplayName("无部门时不查部门表（避免无谓查询，也避免 NPE）")
  void noDeptSkipsDeptLookup() {
    when(accountRepository.findById(9104L)).thenReturn(account(9104L, null, 9103L));

    AccountOrgHierarchyDTO dto = service().orgHierarchy(9104L).orElseThrow();

    assertThat(dto.getDeptId()).isNull();
    assertThat(dto.getDeptHeadId()).isNull();
    verifyNoInteractions(deptRepository);
  }
}
