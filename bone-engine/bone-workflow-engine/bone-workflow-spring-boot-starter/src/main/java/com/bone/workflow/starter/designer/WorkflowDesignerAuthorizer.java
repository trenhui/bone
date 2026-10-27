package com.bone.workflow.starter.designer;

/**
 * 设计器授权扩展点（SPI）：让宿主在不被库绑死安全框架的前提下，为设计器端点补上<b>服务端</b>鉴权。
 *
 * <p><b>为什么需要这个端口</b>：前端路由守卫只是 UX 增强（无权限时给个 403 页），真正的授权必须在服务端。但本 starter 被设计成 「不假设宿主的 web /
 * 安全装配」——它不引 Spring Security、也不替宿主开 {@code @EnableMethodSecurity}
 * （在库里开启方法级安全会污染整个应用的调用链）。于是把判权抽成一个单方法端口：宿主有安全上下文就提供自己的实现， 没有就退回缺省放行，语义边界写清楚，不留「看起来有权限其实没有」的假象。
 *
 * <p><b>宿主接入方式</b>（只需一个 bean，自动装配的缺省实现会被 {@code @ConditionalOnMissingBean} 让位）：
 *
 * <pre>{@code
 * @Bean
 * WorkflowDesignerAuthorizer workflowDesignerAuthorizer() {
 *   return (action, defKey, operatorId) -> {
 *     String code = action == WorkflowDesignerAction.READ
 *         ? "workflow:definitions:read"
 *         : "workflow:definitions:write";
 *     if (!SecurityUtils.hasAuthority(code)) {
 *       throw new WorkflowDesignerAccessDeniedException(action, defKey, operatorId);
 *     }
 *   };
 * }
 * }</pre>
 *
 * <p><b>实现约定</b>：
 *
 * <ul>
 *   <li>抛 {@link WorkflowDesignerAccessDeniedException} 表达拒绝——控制器统一映射为 <b>403</b>，业务码与提示直接进信封。
 *   <li>抛其他运行时异常会被视为实现缺陷（500），不要用它表达「无权」。
 *   <li>实现必须是线程安全的：单例 bean 会被并发请求共享。
 * </ul>
 *
 * <p><b>缺省行为是拒绝（fail-closed）</b>：宿主不提供 bean 时，设计器端点整体 403，并给出接线提示。本地联调可用 {@code
 * bone.workflow.designer.allow-anonymous=true} 显式放开。理由见 {@link #denyAll()}。
 */
@FunctionalInterface
public interface WorkflowDesignerAuthorizer {

  /**
   * 判定一次设计器调用是否放行；不允许时抛 {@link WorkflowDesignerAccessDeniedException}。
   *
   * @param action 动作档位（读 / 写草稿 / 发布 / 上下线）
   * @param defKey 目标流程定义 key；只读总览类端点没有单一目标，传 {@code null}
   * @param operatorId 请求头 {@code X-Operator-Id} 声明的操作人；只读端点可为 {@code null}
   */
  void authorize(WorkflowDesignerAction action, String defKey, String operatorId);

  /**
   * 缺省实现：<b>一律拒绝</b>（fail-closed）。
   *
   * <p>为什么缺省是拒绝而不是放行：本 starter 新加的这段 REST 层一旦放行，任何能访问到端点的调用方都能发布 / 下线流程定义——那等于把一个
   * 「改线上审批路径」的开关挂在没有锁的门上。库不掌握宿主身份上下文，因此不知道该放谁进来，此时唯一安全的默认是「谁都不放」， 并让第一次调用就以 403 +
   * 明确文案暴露出「你还没接线」，而不是安静地裸奔。
   *
   * <p>本地联调若不想接线，用配置显式认账：{@code bone.workflow.designer.allow-anonymous=true}。
   */
  static WorkflowDesignerAuthorizer denyAll() {
    return (action, defKey, operatorId) -> {
      throw WorkflowDesignerAccessDeniedException.notWired(action, defKey, operatorId);
    };
  }

  /**
   * 全放行实现：<b>不做任何判断</b>。
   *
   * <p>仅用于「宿主已在网关侧统一鉴权」或本地 / 测试环境。生产环境若没有网关兜底，必须提供自己的实现，否则任何能访问到端点的调用方都能发布流程定义。
   */
  static WorkflowDesignerAuthorizer allowAll() {
    return (action, defKey, operatorId) -> {};
  }
}
