package com.bone.metadata.runtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link RuntimeRecordApplicationService} 单元测试（P2-1 横切收敛回归防线）。
 *
 * <p>每个用例自问「删掉实现哪一行会红」：
 *
 * <ul>
 *   <li>删掉 {@code currentOperator} 的 details 优先分支 → numericDetails 用例红；
 *   <li>删掉 {@code rowOwnerId} 的 platformAdmin 豁免 → 管理员豁免用例红；
 *   <li>删掉 {@code rowOwnerId} 的非数值判 null → 非数值主体用例红；
 *   <li>{@code create}/{@code update} 去掉 statusGuard 调用或把顺序调到 service 之后 → InOrder 用例红；
 *   <li>page/create/delete 漏传 tenantId / rowOwner / operator → verify 实参用例红。
 * </ul>
 */
class RuntimeRecordApplicationServiceTest {

  private JdbcRuntimeRecordService recordService;
  private RuntimeStatusGuard statusGuard;
  private RuntimeAccessGuard accessGuard;
  private com.bone.metadata.runtime.domain.CurrentRoleProvider currentRoleProvider;
  private RuntimeRecordApplicationService service;

  @BeforeEach
  void setUp() {
    // mock 非 final class（Mockito inline mock maker，spring-boot-starter-test 默认携带）
    recordService = mock(JdbcRuntimeRecordService.class);
    statusGuard = mock(RuntimeStatusGuard.class);
    accessGuard = new RuntimeAccessGuard(); // 无参 @Component，真实判据（读 SecurityContext）
    // 字段级角色裁剪的身份来源：默认「已认证无角色」（空集合 = fail-closed 口径）
    currentRoleProvider = () -> List.of();
    TenantProvider tenantProvider = () -> 42L;
    service =
        new RuntimeRecordApplicationService(
            recordService, tenantProvider, statusGuard, accessGuard, currentRoleProvider);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // —— 横切真源：currentOperator ——

  @Test
  void currentOperator_numericDetailsWins() {
    authenticate("admin", null);
    ((org.springframework.security.authentication.AbstractAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication())
        .setDetails(777L);
    assertEquals("777", service.currentOperator());
  }

  @Test
  void currentOperator_stringNumericDetails() {
    authenticate("admin", null);
    ((org.springframework.security.authentication.AbstractAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication())
        .setDetails("888");
    assertEquals("888", service.currentOperator());
  }

  @Test
  void currentOperator_numericNameFallback() {
    authenticate("999", null);
    assertEquals("999", service.currentOperator());
  }

  @Test
  void currentOperator_nonNumericNameReturnedForAuditSkip() {
    // 非数值主体名仍返回（username），交由下层按数值校验跳过 BIGINT 审计列——删掉该分支会红
    authenticate("admin", null);
    assertEquals("admin", service.currentOperator());
  }

  @Test
  void currentOperator_anonymousReturnsNull() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    assertNull(service.currentOperator());
  }

  @Test
  void currentOperator_unauthenticatedReturnsNull() {
    var token = new UsernamePasswordAuthenticationToken("admin", null, List.of());
    token.setAuthenticated(false);
    SecurityContextHolder.getContext().setAuthentication(token);
    assertNull(service.currentOperator());
  }

  // —— 横切真源：rowOwnerId ——

  @Test
  void rowOwnerId_platformAdminExempt() {
    authenticate("admin", RuntimeAccessGuard.PLATFORM_ADMIN);
    ((org.springframework.security.authentication.AbstractAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication())
        .setDetails(777L);
    // 管理员豁免：即使有数值 operator 也传 null，数据面不做本人过滤
    assertNull(service.rowOwnerId());
  }

  @Test
  void rowOwnerId_numericOperatorParsed() {
    authenticate("user", "runtime:exp:read");
    ((org.springframework.security.authentication.AbstractAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication())
        .setDetails(777L);
    assertEquals(777L, service.rowOwnerId());
  }

  @Test
  void rowOwnerId_nonNumericOperatorNull() {
    authenticate("admin", "runtime:exp:read");
    // 非数值主体与 BIGINT created_by 无可比性 → null（不启用行级过滤）
    assertNull(service.rowOwnerId());
  }

  @Test
  void rowOwnerId_noAuthenticationNull() {
    SecurityContextHolder.clearContext();
    assertNull(service.rowOwnerId());
  }

  // —— 编排透传（verify 实参）——

  @Test
  void page_passesTenantAndRowOwner() {
    authenticate("user", "runtime:exp:read");
    ((org.springframework.security.authentication.AbstractAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication())
        .setDetails(777L);
    PageResult<Map<String, Object>> apiPage =
        PageResult.of(List.<Map<String, Object>>of(Map.of("id", "1")), 1L, 1, 20);
    when(recordService.page(eq("exp"), eq(42L), anyInt(), anyInt(), any(), eq(777L), eq(List.of())))
        .thenReturn(apiPage);

    PageResult<Map<String, Object>> result = service.page("exp", 1, 20, null);
    assertEquals(1, result.getTotal());
    // 角色码来自 CurrentRoleProvider（默认空集合 = 已认证无角色 ⇒ fail-closed 口径）
    verify(recordService).page("exp", 42L, 1, 20, null, 777L, List.of());
  }

  @Test
  void page_passesRoleCodesFromProvider() {
    authenticate("user", "runtime:exp:read");
    ((org.springframework.security.authentication.AbstractAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication())
        .setDetails(777L);
    // provider 在 service 构造时被捕获——必须重建 service 才能换角色码来源
    currentRoleProvider = () -> List.of("hr", "employee");
    service =
        new RuntimeRecordApplicationService(
            recordService, () -> 42L, statusGuard, accessGuard, currentRoleProvider);
    when(recordService.page(eq("exp"), eq(42L), anyInt(), anyInt(), any(), any(), any()))
        .thenReturn(PageResult.of(List.<Map<String, Object>>of(), 0L, 1, 20));

    service.page("exp", 1, 20, null);
    // 接线证明：provider 的角色码必须原样到达 engine（少一环 = 字段级裁剪对后端不生效）
    verify(recordService).page("exp", 42L, 1, 20, null, 777L, List.of("hr", "employee"));
  }

  @Test
  void getById_passesRowOwner() {
    authenticate("user", "runtime:exp:read");
    ((org.springframework.security.authentication.AbstractAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication())
        .setDetails(777L);
    when(recordService.getById("exp", 42L, "1", 777L, List.of()))
        .thenReturn(new LinkedHashMap<>(Map.of("id", "1", "version", 3)));
    Map<String, Object> row = service.getById("exp", "1");
    assertEquals("1", row.get("id"));
    verify(recordService).getById("exp", 42L, "1", 777L, List.of());
  }

  @Test
  void create_appliesStatusGuardBeforeService() {
    authenticate("user", null);
    when(recordService.create(anyString(), eq(42L), any(), anyLong(), eq("user"), eq(List.of())))
        .thenReturn(new LinkedHashMap<>(Map.of("id", "1")));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", "t");

    Map<String, Object> created = service.create("exp", body);
    assertNotNull(created.get("id"));
    // 守卫必须先于持久化：客户端携带的状态字段要在写库前被拒（422），而不是写库后补救
    InOrder order = inOrder(statusGuard, recordService);
    order.verify(statusGuard).applyCreateGuard(42L, "exp", body);
    order
        .verify(recordService)
        .create(eq("exp"), eq(42L), eq(body), anyLong(), eq("user"), eq(List.of()));
  }

  @Test
  void update_appliesStatusGuardAndPassesVersion() {
    authenticate("user", null);
    when(recordService.update(
            anyString(), eq(42L), eq("1"), any(), eq(3), eq("user"), eq(List.of())))
        .thenReturn(new LinkedHashMap<>(Map.of("id", "1", "version", 4)));
    Map<String, Object> row = service.update("exp", "1", Map.of("title", "t2"), 3);
    assertEquals(4, row.get("version"));
    InOrder order = inOrder(statusGuard, recordService);
    order.verify(statusGuard).applyUpdateGuard(42L, "exp", Map.of("title", "t2"));
    order
        .verify(recordService)
        .update("exp", 42L, "1", Map.of("title", "t2"), 3, "user", List.of());
  }

  @Test
  void delete_passesVersionAndOperator() {
    authenticate("user", null);
    service.delete("exp", "1", 5);
    verify(recordService).delete("exp", 42L, "1", 5, "user");
  }

  @Test
  void restore_delegatesWithOperator() {
    authenticate("user", null);
    when(recordService.restore("exp", 42L, "1", "user", List.of())).thenReturn(Map.of("id", "1"));
    assertEquals("1", service.restore("exp", "1").get("id"));
    verify(recordService).restore("exp", 42L, "1", "user", List.of());
  }

  @Test
  void assertRowOwner_delegatesWithCurrentOperator() {
    authenticate("777", null);
    service.assertRowOwner(null, 42L, "1");
    verify(recordService).assertRowOwner(null, 42L, "1", "777");
  }

  @Test
  void newRecordId_positiveAndUnique() {
    long first = service.newRecordId();
    long second = service.newRecordId();
    assertTrue(first > 0, "雪花 ID 必须为正");
    assertTrue(second != first, "连续生成必须不同");
  }

  private static void authenticate(String name, String authority) {
    var token =
        new UsernamePasswordAuthenticationToken(
            name,
            null,
            authority == null ? List.of() : AuthorityUtils.createAuthorityList(authority));
    SecurityContextHolder.getContext().setAuthentication(token);
  }
}
