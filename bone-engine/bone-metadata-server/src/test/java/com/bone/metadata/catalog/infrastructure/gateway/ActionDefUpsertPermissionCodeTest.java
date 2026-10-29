package com.bone.metadata.catalog.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.metadata.catalog.domain.gateway.ActionDefSpec;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 动作定义单条 UPSERT 的<b>权限码刷新</b>契约。
 *
 * <p><b>为什么值得单独钉住</b>：动作级细码形状是 {@code runtime:{entityCode}:{actionCode}}。实体 code 变更后（0052 就把 {@code
 * expense_claim} 改成了 {@code expense_reimbursement}），若 UPSERT 的 {@code ON DUPLICATE KEY UPDATE} 漏掉
 * {@code permission_code}，存量行会永久停留在旧码上：按新码授权「配了却不生效」，而超管走 {@code RuntimeAccessGuard}
 * 的动作级早退分支，任何用超管的联调都测不出来——这类缺陷只能靠断言守。
 *
 * <p><b>本测试的能力边界（诚实标注）</b>：它断言的是「传给 JDBC 的 SQL 形状 + 绑定参数」，不是真实数据库执行结果。 它能拦住「有人删掉刷新子句 /
 * 改错绑定位置」这两类回归，但拦不住数据库方言层面的差异。真实执行证据见联调记录： 对既有 {@code expense_reimbursement} 动作行再次 UPSERT 后，{@code
 * permission_code} 由 {@code runtime:expense_claim:submit} 刷新为 {@code
 * runtime:expense_reimbursement:submit}。
 */
class ActionDefUpsertPermissionCodeTest {

  @Test
  @DisplayName("已存在动作行再次 upsert 时，ON DUPLICATE KEY 必须刷新 permission_code")
  void upsertOneRefreshesPermissionCodeOnDuplicate() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    JdbcActionDefMaterializeAdapter adapter = new JdbcActionDefMaterializeAdapter(jdbc);

    adapter.upsert(
        0L,
        "expense_reimbursement",
        new ActionDefSpec(
            "submit",
            "提交",
            "DRAFT",
            "SUBMITTED",
            "status",
            true,
            "expenseWorkflowSubmitHandler",
            null,
            true,
            10,
            false));

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), args.capture());

    String onDuplicateClause = sql.getValue().substring(sql.getValue().indexOf("ON DUPLICATE KEY"));
    assertThat(onDuplicateClause)
        .as("缺 permission_code 刷新 ⇒ 改过 code 的实体永远命中旧细码，授权静默失效")
        .contains("permission_code = VALUES(permission_code)");

    // 用 Arrays.stream 而不是 List.of(...)：绑定参数里有可空列（paramsSchema / handlerBean），
    // List.of(Object[]) 展开时遇到 null 直接抛 NPE，会让断言变成「测试环境报错」而不是「契约被违反」。
    List<Object> bound =
        Arrays.stream(args.getValue()).filter(Objects::nonNull).collect(Collectors.toList());
    assertThat(bound)
        .as("细码必须按「新 entityCode + actionCode」派生，而不是沿用入参里可能过期的值")
        .contains("runtime:expense_reimbursement:submit");
  }
}
