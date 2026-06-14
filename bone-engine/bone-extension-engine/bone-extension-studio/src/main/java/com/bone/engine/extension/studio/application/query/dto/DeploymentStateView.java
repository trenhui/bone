package com.bone.engine.extension.studio.application.query.dto;

import java.util.List;

/** 插件部署状态视图（详设 v2.5 §3.3）。 */
public record DeploymentStateView(
    Long pluginId,
    String activeVersion,
    String currentStatus,
    List<String> allStates,
    List<Transition> transitions,
    List<VersionState> versionStates) {

  public record Transition(String from, String to) {}

  public record VersionState(String version, String status, boolean active) {}
}
