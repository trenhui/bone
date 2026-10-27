package com.bone.workflow.starter.designer;

/**
 * 设计器调用被 {@link WorkflowDesignerAuthorizer} 拒绝。宿主映射建议 <b>403</b>。
 *
 * <p>与 {@link WorkflowDefinitionRequestException} 的区别：那是「请求拼错了」，重发一次同样的请求仍然错；本类是「你有权发这个请求，
 * 但不该做这个动作」，换个人做就成立——两者在界面上的呈现完全不同（前者要改输入，后者要找管理员授权）。
 */
public class WorkflowDesignerAccessDeniedException extends RuntimeException {

  private final transient WorkflowDesignerAction action;
  private final String defKey;
  private final String operatorId;

  public WorkflowDesignerAccessDeniedException(
      WorkflowDesignerAction action, String defKey, String operatorId) {
    this(action, defKey, operatorId, buildMessage(action, defKey, operatorId));
  }

  private WorkflowDesignerAccessDeniedException(
      WorkflowDesignerAction action, String defKey, String operatorId, String message) {
    super(message);
    this.action = action;
    this.defKey = defKey;
    this.operatorId = operatorId;
  }

  /**
   * 「宿主没接授权」这一种拒绝：文案必须直接给出接线办法，否则运维只会看到一句「无权」而无从下手。
   *
   * <p>与普通拒绝的区别只在文案——HTTP 语义都是 403（未授权就是未授权，不该用 500 把配置缺失伪装成服务故障）。
   */
  public static WorkflowDesignerAccessDeniedException notWired(
      WorkflowDesignerAction action, String defKey, String operatorId) {
    return new WorkflowDesignerAccessDeniedException(
        action,
        defKey,
        operatorId,
        "设计器调用被拒绝：宿主未提供 WorkflowDesignerAuthorizer（缺省 fail-closed），动作="
            + action
            + "。请提供该 bean 接入自身权限体系；本地联调可显式设置"
            + " bone.workflow.designer.allow-anonymous=true。");
  }

  private static String buildMessage(
      WorkflowDesignerAction action, String defKey, String operatorId) {
    String who = operatorId == null || operatorId.isBlank() ? "未识别操作人" : operatorId;
    String target = defKey == null || defKey.isBlank() ? "流程定义" : "流程定义 " + defKey;
    return "操作人 " + who + " 无权对" + target + "执行 " + action + " 动作";
  }

  public WorkflowDesignerAction action() {
    return action;
  }

  public String defKey() {
    return defKey;
  }

  public String operatorId() {
    return operatorId;
  }
}
