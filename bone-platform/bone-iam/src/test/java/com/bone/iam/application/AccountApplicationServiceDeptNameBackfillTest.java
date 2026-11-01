package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.mapper.AccountDtoMapper;
import com.bone.iam.application.query.qry.AccountPageQuery;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 账号列表「所属部门 / 直属主管」展示列的装配契约。
 *
 * <p><b>为什么值得锁住</b>：这两列是审批流「直属主管审批 / 部门负责人审批」节点在界面上的数据源。 历史实现把 deptName
 * 挂在「按部门筛选时才加载的部门树」上，于是不过滤的列表里整列为 null， 前端据此把**明明有 deptId 的账号**渲染成「未归属」——用户看到的是错误信息，而不是数据缺失。
 * （主管姓名早已改为无条件装配；部门名漏修。）
 *
 * <p><b>判别性</b>：第三个用例断言「不再加载整棵部门树」——若有人把实现回退成懒加载，该用例立刻转红。
 */
class AccountApplicationServiceDeptNameBackfillTest {

  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final DeptRepository deptRepository = mock(DeptRepository.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);

  private AccountApplicationService service() {
    return new AccountApplicationService(
        accountRepository,
        mock(PasswordEncoder.class),
        mock(AccountRoleBindingSupport.class),
        mock(PasswordPolicyValidator.class),
        mock(TenantQuotaEnforcer.class),
        mock(AccountDtoMapper.class),
        tenantProvider,
        deptRepository);
  }

  private static Account account(long id, Long deptId, Long managerId, String realName) {
    Account account =
        Account.create(
            id,
            Username.of("user" + id),
            "hash",
            Email.of("user" + id + "@bone.local"),
            null,
            realName,
            0L);
    if (deptId != null) {
      account.changeDept(deptId);
    }
    if (managerId != null) {
      account.changeManager(managerId);
    }
    return account;
  }

  private static Dept dept(long id, String name) {
    Dept dept = mock(Dept.class);
    when(dept.getId()).thenReturn(id);
    when(dept.getName()).thenReturn(name);
    return dept;
  }

  private AccountPageQuery query(Long deptId) {
    AccountPageQuery qry = new AccountPageQuery();
    qry.setPage(1);
    qry.setSize(10);
    qry.setTenantId(0L);
    qry.setDeptId(deptId);
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(0L);
    return qry;
  }

  @Test
  @DisplayName("未按部门筛选时也装配 deptName（否则界面把有部门的账号显示成「未归属」）")
  void backfillsDeptNameWithoutDeptFilter() {
    Account emp = account(1L, 100L, 9103L, "林知远");
    Account manager = account(9103L, null, null, "周慕云");
    Dept frontend = dept(100L, "前端研发组");

    when(accountRepository.findAccountPage(isNull(), isNull(), eq(0L), isNull(), eq(1), eq(10)))
        .thenReturn(PageResult.of(List.of(emp), 1L, 1, 10));
    when(deptRepository.findByIds(List.of(100L))).thenReturn(List.of(frontend));
    when(accountRepository.findByIds(List.of(9103L))).thenReturn(List.of(manager));

    PageResult<AccountDTO> page = service().page(query(null));

    assertThat(page.getRecords()).hasSize(1);
    AccountDTO dto = page.getRecords().get(0);
    assertThat(dto.getDeptId()).isEqualTo(100L);
    assertThat(dto.getDeptName()).isEqualTo("前端研发组");
    assertThat(dto.getManagerName()).isEqualTo("周慕云");
  }

  @Test
  @DisplayName("未归属部门的账号 deptName 为 null，且不会因 get(null) 抛 NPE")
  void keepsDeptNameNullForUnassignedAccount() {
    Account solo = account(2L, null, null, "钱无咎");

    when(accountRepository.findAccountPage(isNull(), isNull(), eq(0L), isNull(), eq(1), eq(10)))
        .thenReturn(PageResult.of(List.of(solo), 1L, 1, 10));

    PageResult<AccountDTO> page = service().page(query(null));

    AccountDTO dto = page.getRecords().get(0);
    assertThat(dto.getDeptId()).isNull();
    assertThat(dto.getDeptName()).isNull();
    assertThat(dto.getManagerName()).isNull();
    verify(deptRepository, never()).findByIds(any());
  }

  @Test
  @DisplayName("不再加载整棵部门树（回退成懒加载会让 deptName 重新变 null）")
  void doesNotLoadWholeDeptTree() {
    Account emp = account(3L, 200L, null, "苏明诚");
    Dept backend = dept(200L, "后端研发组");

    when(accountRepository.findAccountPage(isNull(), isNull(), eq(0L), isNull(), eq(1), eq(10)))
        .thenReturn(PageResult.of(List.of(emp), 1L, 1, 10));
    when(deptRepository.findByIds(List.of(200L))).thenReturn(List.of(backend));

    service().page(query(null));

    verify(deptRepository, never()).listAll();
    verify(deptRepository).findByIds(List.of(200L));
  }

  @Test
  @DisplayName("按部门筛选时 deptName 同样装配（筛选路径不回退）")
  void backfillsDeptNameWithDeptFilter() {
    Account emp = account(4L, 300L, null, "何家怡");
    Dept tech = dept(300L, "技术中心");

    when(accountRepository.findAccountPage(isNull(), isNull(), eq(0L), any(), eq(1), eq(10)))
        .thenReturn(PageResult.of(List.of(emp), 1L, 1, 10));
    when(deptRepository.findByIds(List.of(300L))).thenReturn(List.of(tech));

    PageResult<AccountDTO> page = service().page(query(300L));

    assertThat(page.getRecords().get(0).getDeptName()).isEqualTo("技术中心");
  }
}
