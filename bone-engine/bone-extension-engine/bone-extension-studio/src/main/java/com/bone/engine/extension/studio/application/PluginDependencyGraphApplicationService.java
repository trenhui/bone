package com.bone.engine.extension.studio.application;

import com.bone.engine.extension.studio.application.query.dto.PluginDependencyGraph;
import com.bone.engine.extension.studio.application.query.dto.PluginDependencyGraph.EdgeType;
import com.bone.engine.extension.studio.application.query.dto.PluginDependencyGraph.NodeType;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.model.plugin.PluginVersion;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 依赖图读侧（详设 §12.3）。
 *
 * <p><b>两类真实边</b>：① BINDING——插件→扩展点绑定（{@code ext_point_id} 外键，控制面部署/路由的真正依据）， 扩展点建模为独立节点（id
 * 取负值避免与插件 ID 冲突）；② DEPENDENCY——插件→插件声明依赖（{@code config_json.dependencies}，按 name 匹配，未解析则 {@code
 * resolved=false}）。
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PluginDependencyGraphApplicationService {

  private static final Logger log =
      LoggerFactory.getLogger(PluginDependencyGraphApplicationService.class);

  private final ExtensionQueryApplicationService extensionQueryHandler;
  private final ExtPointRepository extPointRepository;
  private final ObjectMapper objectMapper;

  public PluginDependencyGraph load(Long extPointId) {
    List<Extension> plugins =
        extPointId == null
            ? extensionQueryHandler.findAllExtensions()
            : extensionQueryHandler.findExtensionsByExtPointId(extPointId);

    Map<String, Extension> byName = new HashMap<>();
    for (Extension plugin : plugins) {
      if (plugin.getName() != null) {
        byName.put(plugin.getName(), plugin);
      }
    }

    List<PluginDependencyGraph.Node> nodes = new ArrayList<>();
    List<PluginDependencyGraph.Edge> edges = new ArrayList<>();
    Map<Long, ExtPoint> extPoints = new HashMap<>();
    for (Extension plugin : plugins) {
      String deployStatus = resolveDeploymentStatus(plugin);
      nodes.add(
          new PluginDependencyGraph.Node(
              plugin.getId(),
              plugin.getName(),
              plugin.getClassName(),
              plugin.getExtPointId(),
              plugin.isEnabled(),
              deployStatus,
              NodeType.PLUGIN));
      // 真实绑定边：插件 → 所属扩展点（ext_point_id 外键）
      if (plugin.getExtPointId() != null) {
        ExtPoint point =
            extPoints.computeIfAbsent(
                plugin.getExtPointId(), id -> extPointRepository.findById(id));
        if (point != null && point.getName() != null) {
          edges.add(
              new PluginDependencyGraph.Edge(
                  plugin.getId(), point.getName(), true, EdgeType.BINDING));
        }
      }
      for (String dep : parseDependencies(plugin.getConfig())) {
        edges.add(
            new PluginDependencyGraph.Edge(
                plugin.getId(), dep, byName.containsKey(dep), EdgeType.DEPENDENCY));
      }
    }
    // 扩展点节点：id 取负值避免与插件 ID 冲突；部署状态无意义（不参与版本状态机）
    for (ExtPoint point : extPoints.values()) {
      if (point == null || point.getName() == null) {
        continue;
      }
      nodes.add(
          new PluginDependencyGraph.Node(
              -point.getId(),
              point.getName(),
              point.getInterfaceName(),
              point.getId(),
              point.isEnabled(),
              null,
              NodeType.EXT_POINT));
    }
    return new PluginDependencyGraph(nodes, edges);
  }

  private String resolveDeploymentStatus(Extension plugin) {
    try {
      List<PluginVersion> versions = extensionQueryHandler.listPluginVersions(plugin.getId());
      return versions.stream()
          .filter(PluginVersion::isActive)
          .map(PluginVersion::getDeploymentStatus)
          .findFirst()
          .orElse(null);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private List<String> parseDependencies(String configJson) {
    if (configJson == null || configJson.isBlank()) {
      return List.of();
    }
    try {
      JsonNode node = objectMapper.readTree(configJson);
      JsonNode deps = node.get("dependencies");
      if (deps == null || !deps.isArray()) {
        return List.of();
      }
      List<String> result = new ArrayList<>();
      deps.forEach(
          d -> {
            String text = d.asText(null);
            if (text != null && !text.isBlank()) {
              result.add(text);
            }
          });
      return result;
    } catch (Exception ex) {
      log.debug("解析 dependencies 失败 pluginId={} : {}", null, ex.getMessage());
      return List.of();
    }
  }
}
