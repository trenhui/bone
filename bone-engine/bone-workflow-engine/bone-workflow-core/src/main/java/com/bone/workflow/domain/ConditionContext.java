package com.bone.workflow.domain;

/**
 * 条件求值上下文：把「当前操作者」等主体信息喂给条件求值。
 *
 * <p>与平台 UI 协议规则的 {@code Ctx} 同形（{@code $me} / {@code $myteam} / {@code $all}），保证「审批人不得为申请人
 * 本人」这类主体条件在界面侧与服务端侧结论一致。
 */
public record ConditionContext(String me, java.util.List<String> myTeam, String all) {

  /** 空上下文。 */
  public static ConditionContext empty() {
    return new ConditionContext(null, java.util.List.of(), null);
  }

  /** 以当前操作者构造。 */
  public static ConditionContext of(String operatorId) {
    return new ConditionContext(operatorId, java.util.List.of(), null);
  }
}
