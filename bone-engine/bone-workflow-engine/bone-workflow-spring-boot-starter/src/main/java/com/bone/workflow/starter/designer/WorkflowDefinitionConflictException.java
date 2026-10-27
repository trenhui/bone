package com.bone.workflow.starter.designer;

/**
 * 设计态并发冲突（revision 过期、并发首存、版本号撞车）。宿主映射建议 <b>409</b>。
 *
 * <p>为什么这类冲突必须<b>显式失败</b>而不是「后写覆盖」：两个运营同时改一条审批流，静默覆盖等于让其中一个人的修改凭空消失，
 * 而审批流是「改错了也没人看得出」的配置——覆盖者与被覆盖者都不会收到任何提示。给 409 加一句「当前 revision 是几」， 用户刷新即可看到差异并重做。
 */
public class WorkflowDefinitionConflictException extends RuntimeException {

  public WorkflowDefinitionConflictException(String message) {
    super(message);
  }

  public WorkflowDefinitionConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
