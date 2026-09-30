package com.bone.engine.extension.studio.application.query.dto;

import java.util.List;

/**
 * 插件依赖图视图（详设 v2.5 §12.3）。
 *
 * <p><b>边类型（业界真实语义）</b>：{@code BINDING} 为插件→扩展点的真实绑定边（来自 ext_point_id 外键， 控制面部署/路由的真正依据）；{@code
 * DEPENDENCY} 为插件→插件声明的依赖边（来自 {@code config_json.dependencies}， 按 name 匹配，未解析则 {@code
 * resolved=false}）。扩展点建模为独立节点（id 取负值避免与插件 ID 冲突）。
 */
public record PluginDependencyGraph(List<Node> nodes, List<Edge> edges) {

  /** 节点类型：PLUGIN=插件实现，EXT_POINT=扩展点契约。 */
  public enum NodeType {
    PLUGIN,
    EXT_POINT
  }

  /** 边类型：BINDING=绑定关系（真实外键），DEPENDENCY=声明依赖（config_json）。 */
  public enum EdgeType {
    BINDING,
    DEPENDENCY
  }

  public record Node(
      Long id,
      String name,
      String className,
      Long extPointId,
      boolean enabled,
      String deploymentStatus,
      NodeType type) {

    /** 兼容旧构造（type 默认 PLUGIN）。 */
    public Node(
        Long id,
        String name,
        String className,
        Long extPointId,
        boolean enabled,
        String deploymentStatus) {
      this(id, name, className, extPointId, enabled, deploymentStatus, NodeType.PLUGIN);
    }
  }

  public record Edge(Long fromId, String toName, boolean resolved, EdgeType type) {

    /** 兼容旧构造（type 默认 DEPENDENCY）。 */
    public Edge(Long fromId, String toName, boolean resolved) {
      this(fromId, toName, resolved, EdgeType.DEPENDENCY);
    }
  }
}
