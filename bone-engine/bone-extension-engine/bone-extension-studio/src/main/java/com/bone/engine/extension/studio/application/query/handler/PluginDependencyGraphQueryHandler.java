package com.bone.engine.extension.studio.application.query.handler;

import com.bone.engine.extension.studio.application.query.dto.PluginDependencyGraph;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginVersion;
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
 * <p>约定：{@link Extension#getConfig() config_json} 中可选 {@code dependencies: ["plugin-name", ...]}；
 * 节点 = 已注册插件，边 = 声明的依赖名（按 name 匹配，未解析则 {@code resolved=false}）。
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PluginDependencyGraphQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(PluginDependencyGraphQueryHandler.class);

    private final ExtensionQueryHandler extensionQueryHandler;
    private final ObjectMapper objectMapper;

    public PluginDependencyGraph load(Long extPointId) {
        List<Extension> plugins = extPointId == null
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
        for (Extension plugin : plugins) {
            String deployStatus = resolveDeploymentStatus(plugin);
            nodes.add(new PluginDependencyGraph.Node(
                    plugin.getId(),
                    plugin.getName(),
                    plugin.getClassName(),
                    plugin.getExtPointId(),
                    plugin.isEnabled(),
                    deployStatus));
            for (String dep : parseDependencies(plugin.getConfig())) {
                edges.add(new PluginDependencyGraph.Edge(plugin.getId(), dep, byName.containsKey(dep)));
            }
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
            deps.forEach(d -> {
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
