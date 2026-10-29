package com.bone.boot;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.workflow.starter.designer.WorkflowDesignerAccessDeniedException;
import com.bone.workflow.starter.designer.WorkflowDesignerAction;
import com.bone.workflow.starter.designer.WorkflowDesignerAuthorizer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 设计器鉴权接线的判别力测试。
 *
 * <p><b>为什么这些断言值得写</b>：这条接线失败的表现有两种，且方向相反——接成"永远放行"是安全问题（任何人都能发布线上审批路径）， 接成"永远拒绝"是可用性问题（设计器整页
 * 403，且错误只出现在浏览器里）。两者都不会让应用启动失败，只靠"跑得起来"完全无法区分， 因此必须用断言把每个动作档位钉死。
 */
class WorkflowDesignerAuthorizationConfigurationTest {

  private final WorkflowDesignerAuthorizationConfiguration configuration =
      new WorkflowDesignerAuthorizationConfiguration();

  private final WorkflowDesignerAuthorizer authorizer =
      configuration.workflowDesignerAuthorizer(false);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  /** 以指定权限码模拟一个已认证主体（形态与 JwtAuthenticationFilter 写入的一致）。 */
  private static void authenticateWith(String... codes) {
    var authorities = List.of(codes).stream().map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, authorities));
  }

  @Test
  @DisplayName("读动作只需读码：只有 read 的主体可读")
  void readActionRequiresReadCode() {
    authenticateWith(WorkflowDesignerAuthorizationConfiguration.PERMISSION_READ);
    assertThatCode(() -> authorizer.authorize(WorkflowDesignerAction.READ, null, "admin"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("只有读码的主体不能写草稿（否则读权限可越权改定义）")
  void readCodeAloneCannotWrite() {
    authenticateWith(WorkflowDesignerAuthorizationConfiguration.PERMISSION_READ);
    assertThatThrownBy(() -> authorizer.authorize(WorkflowDesignerAction.WRITE, "d1", "admin"))
        .isInstanceOf(WorkflowDesignerAccessDeniedException.class);
  }

  @Test
  @DisplayName("写码覆盖 WRITE / PUBLISH / ENABLE 三档（发布与上下线不另发码，见类注释）")
  void writeCodeCoversAllWriteBearingActions() {
    authenticateWith(WorkflowDesignerAuthorizationConfiguration.PERMISSION_WRITE);
    for (WorkflowDesignerAction action :
        List.of(
            WorkflowDesignerAction.WRITE,
            WorkflowDesignerAction.PUBLISH,
            WorkflowDesignerAction.ENABLE)) {
      assertThatCode(() -> authorizer.authorize(action, "d1", "admin"))
          .as("动作 %s 应被写码放行", action)
          .doesNotThrowAnyException();
    }
  }

  @Test
  @DisplayName("无认证主体一律拒绝，绝不把「解析不出身份」当成「有权限」")
  void anonymousIsDenied() {
    for (WorkflowDesignerAction action : WorkflowDesignerAction.values()) {
      assertThatThrownBy(() -> authorizer.authorize(action, "d1", null))
          .as("动作 %s 在无认证主体时必须拒绝", action)
          .isInstanceOf(WorkflowDesignerAccessDeniedException.class);
    }
  }

  @Test
  @DisplayName("无关权限码不放行（防「有任意权限即可设计流程」的越权）")
  void unrelatedAuthorityIsDenied() {
    authenticateWith("metadata:write", "runtime:*:write");
    assertThatThrownBy(() -> authorizer.authorize(WorkflowDesignerAction.READ, null, "admin"))
        .isInstanceOf(WorkflowDesignerAccessDeniedException.class);
  }

  @Test
  @DisplayName("allow-anonymous=true 时全放行（本地联调显式认账口子仍可用）")
  void allowAnonymousBypassesAll() {
    SecurityContextHolder.clearContext();
    WorkflowDesignerAuthorizer permissive = configuration.workflowDesignerAuthorizer(true);
    for (WorkflowDesignerAction action : WorkflowDesignerAction.values()) {
      assertThatCode(() -> permissive.authorize(action, "d1", null)).doesNotThrowAnyException();
    }
  }
}
