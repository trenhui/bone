package com.bone.blueprint.domain.extension.channel;

import java.time.Instant;
import java.util.List;

/**
 * 渠道物流轨迹查询结果。
 *
 * @param success 是否查询成功
 * @param nodes 轨迹节点（按时间正序）
 * @param errorCode 失败码
 * @param message 说明
 */
public record ChannelTraceResult(
    boolean success, List<TraceNode> nodes, String errorCode, String message) {

  public ChannelTraceResult {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
  }

  /** 轨迹节点。 */
  public record TraceNode(Instant time, String status, String description) {}

  public static ChannelTraceResult ok(List<TraceNode> nodes) {
    return new ChannelTraceResult(true, nodes, null, null);
  }

  public static ChannelTraceResult fail(String errorCode, String message) {
    return new ChannelTraceResult(false, List.of(), errorCode, message);
  }
}
