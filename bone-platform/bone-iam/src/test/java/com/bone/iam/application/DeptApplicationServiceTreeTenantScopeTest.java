package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.iam.application.query.dto.DeptTreeDTO;
import com.bone.iam.application.query.qry.DeptTreeQuery;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.dept.Dept;
import com.bone.iam.domain.repository.DeptRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 锁死 {@link DeptApplicationService} 的 {@code tree(DeptTreeQuery)} 租户过滤接线（诊断 #15 / #12）。
 *
 * <p>生产侧口径由 {@code TenantScopeResolver.resolve} 决定（已由其单测 {@code TenantScopeResolverTest}
 * 锁死）：非平台租户(&gt;0) 强制按其过滤（隔离不可被查询参数绕过）；平台租户(0)/无上下文回退查询参数， 未传则不过滤（平台管理员跨租户视图，设计内有意例外，非越权缺陷）。
 *
 * <p>本类锁的是「resolver 的结果 ⇒ {@code tree()} 真的按 {@code tenantId} 过滤 {@code Dept}」 这一段接线，防止将来有人在 {@code
 * tree()} 里误改成不过滤——那正是 E2E 的 {@code depts/tree} 隔离断言（{@code verify_tenant_isolation_e2e.py}
 * 4.3）退化为「随并发会话漂移的脆弱断言」的源头。
 */
class DeptApplicationServiceTreeTenantScopeTest {

  private final DeptRepository deptRepository = mock(DeptRepository.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);
  private final DeptApplicationService service =
      new DeptApplicationService(deptRepository, tenantProvider);

  private static Dept dept(String name, Long tenantId) {
    return Dept.create(name, null, 1, 1, tenantId);
  }

  @Test
  @DisplayName("非平台租户上下文：tree() 仅返回本租户部门（隔离不可被绕过）")
  void nonPlatformTenant_seesOnlyOwnDepts() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(9001L);
    when(deptRepository.listAll()).thenReturn(List.of(dept("A1", 9001L), dept("B1", 9002L)));

    List<DeptTreeDTO> tree = service.tree(new DeptTreeQuery());

    assertThat(tree).extracting(DeptTreeDTO::getName).containsExactly("A1");
  }

  @Test
  @DisplayName("平台租户(0) + 显式 tenantId：tree() 仅返回该租户部门")
  void platformTenant_withExplicitTenantId_seesOnlyThatTenant() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(0L);
    when(deptRepository.listAll()).thenReturn(List.of(dept("A1", 9001L), dept("B1", 9002L)));
    DeptTreeQuery qry = new DeptTreeQuery();
    qry.setTenantId(9002L);

    List<DeptTreeDTO> tree = service.tree(qry);

    assertThat(tree).extracting(DeptTreeDTO::getName).containsExactly("B1");
  }

  @Test
  @DisplayName("平台租户(0) 且无 tenantId：tree() 返回全量（平台管理员跨租户视图，设计内有意例外）")
  void platformTenant_withoutTenantId_seesAll() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(0L);
    when(deptRepository.listAll()).thenReturn(List.of(dept("A1", 9001L), dept("B1", 9002L)));
    DeptTreeQuery qry = new DeptTreeQuery(); // tenantId 默认 null

    List<DeptTreeDTO> tree = service.tree(qry);

    assertThat(tree)
        .as("平台租户无 tenantId 返回全量是有意例外（管理员跨租户视图），本断言固化该语义，防止被误改成过滤")
        .extracting(DeptTreeDTO::getName)
        .containsExactlyInAnyOrder("A1", "B1");
  }
}
