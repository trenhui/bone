package com.bone.boot;

import com.bone.workflow.starter.designer.WorkflowDesignerAccessDeniedException;
import com.bone.workflow.starter.designer.WorkflowDesignerAction;
import com.bone.workflow.starter.designer.WorkflowDesignerAuthorizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 工作流设计器的<b>宿主侧鉴权接线</b>（组合根职责）。
 *
 * <p><b>为什么必须有这个类</b>：starter 里的 {@code WorkflowDesignerAuthorizer} 缺省实现是 {@code
 * denyAll()}（fail-closed）， 宿主不提供 bean 时设计器<b>全部端点 403</b>。这不是"还没做完"的占位，而是刻意的安全默认：能发布流程定义等于能改线上审批路径，
 * 库不知道该放谁进来时只能谁都不放。但"接线"这件事总得有人做——就是组合根，因为它同时看得见平台的安全上下文（{@code metadata-server} 的 {@code
 * JwtAuthenticationFilter} 已把权限码写进 {@code SecurityContext}）和业务侧的审批流装配。
 *
 * <p><b>判权口径</b>（与 {@code scripts/migration/0067_seed_workflow_designer_permission_menu.sql} 保持一致）：
 *
 * <table border="1">
 *   <caption>动作档位 → 权限码</caption>
 *   <tr><th>动作</th><th>所需权限码</th></tr>
 *   <tr><td>{@code READ}（总览/草稿/版本/校验/试运行）</td><td>{@code workflow:definitions:read}</td></tr>
 *   <tr><td>{@code WRITE}（保存/丢弃草稿）</td><td>{@code workflow:definitions:write}</td></tr>
 *   <tr><td>{@code PUBLISH}（发布不可变版本）</td><td>{@code workflow:definitions:write}</td></tr>
 *   <tr><td>{@code ENABLE}（上/下线版本）</td><td>{@code workflow:definitions:write}</td></tr>
 * </table>
 *
 * <p><b>为什么 PUBLISH / ENABLE 不单独发码</b>：动作档位本身已按爆炸半径拆开，是否要"职责分离（改草稿的人不许直接发上线）"是<b>租户的治理选择</b>，
 * 不该由代码替它决定。真要 SoD 时改本类两三行映射即可（换成 {@code
 * workflow:definitions:publish}），不需要动前端、动数据库、发新权限码——码是运维面资产，拆码成本远高于拆一行映射。这与 0067 种子注释的口径一致。
 *
 * <p><b>为什么认 {@code allow-anonymous}</b>：starter 的缺省 bean 会读这个配置，本类一旦接管就必须保持同一语义，否则"配置了却不生效"
 * 会让本地联调的人以为开关坏了。开关打开时启动日志打 WARN——它是显式认账，不是静默降级。
 */
@Configuration(proxyBeanMethods = false)
public class WorkflowDesignerAuthorizationConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(WorkflowDesignerAuthorizationConfiguration.class);

  /** 读面权限码：定义总览 / 草稿 / 版本 / 校验 / 试运行。 */
  static final String PERMISSION_READ = "workflow:definitions:read";

  /** 写面权限码：保存草稿 / 丢弃草稿 / 发布 / 上下线。 */
  static final String PERMISSION_WRITE = "workflow:definitions:write";

  @Bean
  WorkflowDesignerAuthorizer workflowDesignerAuthorizer(
      @Value("${bone.workflow.designer.allow-anonymous:false}") boolean allowAnonymous) {
    if (allowAnonymous) {
      log.warn(
          "[workflow-designer] bone.workflow.designer.allow-anonymous=true ⇒ 设计器端点不做任何鉴权；"
              + "仅限本地联调，生产必须关闭并依赖 JWT scopes 判权");
      return WorkflowDesignerAuthorizer.allowAll();
    }
    return (action, defKey, operatorId) -> {
      String required = requiredPermission(action);
      if (!hasAuthority(required)) {
        throw new WorkflowDesignerAccessDeniedException(action, defKey, operatorId);
      }
    };
  }

  /** 动作档位 → 权限码。四档全部有映射，不留 default 分支让新档位静默放行。 */
  static String requiredPermission(WorkflowDesignerAction action) {
    return switch (action) {
      case READ -> PERMISSION_READ;
      case WRITE, PUBLISH, ENABLE -> PERMISSION_WRITE;
    };
  }

  /**
   * 从当前安全上下文取权限码判定。
   *
   * <p><b>无认证主体一律判否</b>（fail-closed）：{@code SecurityContext} 没有 Authentication 时返回 false，由调用方抛 403；
   * 绝不把"解析不出身份"当成"有权限"。
   */
  private static boolean hasAuthority(String code) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }
    for (GrantedAuthority granted : auth.getAuthorities()) {
      if (code.equals(granted.getAuthority())) {
        return true;
      }
    }
    return false;
  }
}
