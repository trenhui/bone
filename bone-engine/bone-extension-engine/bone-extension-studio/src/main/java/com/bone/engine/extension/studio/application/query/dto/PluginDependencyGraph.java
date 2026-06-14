package com.bone.engine.extension.studio.application.query.dto;

import java.util.List;

/** 插件依赖图视图（详设 v2.5 §12.3）。 */
public record PluginDependencyGraph(List<Node> nodes, List<Edge> edges) {

  public record Node(
      Long id,
      String name,
      String className,
      Long extPointId,
      boolean enabled,
      String deploymentStatus) {}

  public record Edge(Long fromId, String toName, boolean resolved) {}
}
