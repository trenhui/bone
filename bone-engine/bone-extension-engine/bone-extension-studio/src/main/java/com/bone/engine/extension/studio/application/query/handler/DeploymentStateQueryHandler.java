package com.bone.engine.extension.studio.application.query.handler;

import com.bone.engine.extension.studio.application.query.dto.DeploymentStateView;
import com.bone.engine.extension.studio.domain.model.DeploymentStateMachine;
import com.bone.engine.extension.studio.domain.model.DeploymentStatus;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginVersion;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 插件部署状态读侧（详设 §3.3 / §4.2）。 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeploymentStateQueryHandler {

    private final ExtensionQueryHandler extensionQueryHandler;

    public DeploymentStateView load(Long pluginId) {
        Extension plugin = extensionQueryHandler.findExtensionById(pluginId);
        if (plugin == null) {
            throw new IllegalArgumentException("插件不存在: " + pluginId);
        }
        List<PluginVersion> versions = extensionQueryHandler.listPluginVersions(pluginId);
        PluginVersion active = versions.stream().filter(PluginVersion::isActive).findFirst().orElse(null);
        DeploymentStatus current = parseStatus(active != null ? active.getDeploymentStatus() : null);

        return new DeploymentStateView(
                pluginId,
                active != null ? active.getVersion() : null,
                current != null ? current.name() : (plugin.isEnabled() ? "ACTIVE" : "UPLOADED"),
                DeploymentStateMachine.allStates().stream().map(Enum::name).toList(),
                buildTransitions(),
                versions.stream()
                        .map(v -> new DeploymentStateView.VersionState(
                                v.getVersion(), v.getDeploymentStatus(), v.isActive()))
                        .toList());
    }

    private static List<DeploymentStateView.Transition> buildTransitions() {
        return DeploymentStateMachine.allStates().stream()
                .flatMap(state -> DeploymentStateMachine.nextStates(state).stream()
                        .map(next -> new DeploymentStateView.Transition(state.name(), next.name())))
                .toList();
    }

    private static DeploymentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DeploymentStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
