package com.bone.integration.application.query.qry;

/**
 * 集成执行记录分页查询。
 *
 * <p><b>为何 page/size 用 Integer 并给默认值</b>：同包 {@link FlowPageQuery} / {@link ConnectorPageQuery}
 * 已是该口径。原实现用原始类型 {@code int}，Spring 在无参请求时按 null 绑定失败 ⇒ 列表端点 裸调返回 400（{@code Failed to convert
 * value of type 'null' to required type 'int'}），与同模块其它列表 端点「不传参即首页」的行为不一致。
 */
public record ExecutionLogListQuery(Integer page, Integer size, Long flowId, String status) {
  public ExecutionLogListQuery {
    if (page == null || page <= 0) page = 1;
    if (size == null || size <= 0) size = 10;
  }
}
