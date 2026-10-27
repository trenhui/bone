package com.bone.workflow.starter.designer;

/**
 * 设计器动作粒度，供 {@link WorkflowDesignerAuthorizer} 按动作判权。
 *
 * <p><b>为什么分四档而不是「读 / 写」两档</b>：这四个动作的爆炸半径不同——草稿是私有暂存（只有设计者自己看），发布把版本变成租户内
 * 生效定义（影响后续所有单据的流转路径），上/下线决定某个已发布版本是否受理新实例（直接改变线上行为）。业界做法（Camunda 的 {@code
 * create/read/update/delete/deploy}、Flowable 的 {@code deploy} 独立于 {@code write}）都是把「部署/发布」单独成一档，
 * 便于用职责分离（SoD）覆盖「能改草稿的人不该能直接发上线」这条审计要求。
 *
 * <p>本枚举只表达<b>动作语义</b>，不绑定具体权限码——映射由宿主实现决定。缺省实现（{@link
 * WorkflowDesignerAuthorizer#allowAll()}）不做任何判断，此时契约退化为「调用方自行保证鉴权」，仅供无安全上下文的宿主 / 测试使用；生产必须提供自己的
 * {@link WorkflowDesignerAuthorizer} bean。
 */
public enum WorkflowDesignerAction {

  /** 只读：定义总览、草稿、版本、校验、试运行。 */
  READ,

  /** 写草稿：保存 / 丢弃（不影响任何已发布版本）。 */
  WRITE,

  /** 发布：把草稿固化为不可变版本（fail-closed 校验后仍可能被本动作拦下）。 */
  PUBLISH,

  /** 上 / 下线某个已发布版本（直接改变线上受理行为）。 */
  ENABLE
}
