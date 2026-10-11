package com.bone.metadata.engine.runtime;

/**
 * 状态机「入口状态」解析（转移矩阵中无入边的状态，如报销的 {@code DRAFT}）。
 *
 * <p><b>为什么需要它</b>：受保护状态字段通常是<strong>必填</strong>列（{@code meta_field.is_required=1}）。
 * 若既禁止客户端直写（RC-008），又不给平台注入入口状态，则「创建记录」会陷入死锁—— 不传 status 触发必填校验失败，传了触发 422 保护。
 *
 * <p>把入口状态推导下沉到写路径（而非留在 Controller 里拼 body），是为了让 「状态字段只能由平台按转移矩阵赋值」这条规则在<strong>唯一出口</strong>内成立——
 * 否则任何绕过该 Controller 的调用方都能重新打开「创建即终态」的口子。
 */
@FunctionalInterface
public interface RuntimeStateEntryResolver {

  RuntimeStateEntryResolver NONE = (entityCode, tenantId) -> null;

  /**
   * @return 入口状态值；实体未声明状态机或入口不可推导时返回 {@code null}（此时不注入， 由字段必填规则决定是否报错 —— fail-closed，绝不采信客户端值）。
   */
  String entryState(String entityCode, long tenantId);
}
