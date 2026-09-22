package com.bone.engine.extension.studio.domain.model.plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制品部署状态机（详设 v2.5 §3.3）。
 *
 * <p>UPLOADED → VALIDATED → STAGED → ACTIVE → DEPRECATED；REJECTED 为旁路终态。
 */
public final class DeploymentStateMachine {

  private static final Map<DeploymentStatus, List<DeploymentStatus>> TRANSITIONS =
      buildTransitions();

  private DeploymentStateMachine() {}

  public static List<DeploymentStatus> allStates() {
    return List.of(
        DeploymentStatus.UPLOADED,
        DeploymentStatus.VALIDATED,
        DeploymentStatus.REJECTED,
        DeploymentStatus.STAGED,
        DeploymentStatus.ACTIVE,
        DeploymentStatus.DEPRECATED);
  }

  public static List<DeploymentStatus> nextStates(DeploymentStatus current) {
    if (current == null) {
      return List.of(DeploymentStatus.UPLOADED);
    }
    return TRANSITIONS.getOrDefault(current, List.of());
  }

  public static boolean canTransit(DeploymentStatus from, DeploymentStatus to) {
    return nextStates(from).contains(to);
  }

  private static Map<DeploymentStatus, List<DeploymentStatus>> buildTransitions() {
    Map<DeploymentStatus, List<DeploymentStatus>> m = new LinkedHashMap<>();
    m.put(
        DeploymentStatus.UPLOADED, List.of(DeploymentStatus.VALIDATED, DeploymentStatus.REJECTED));
    m.put(DeploymentStatus.VALIDATED, List.of(DeploymentStatus.STAGED, DeploymentStatus.REJECTED));
    m.put(DeploymentStatus.STAGED, List.of(DeploymentStatus.ACTIVE, DeploymentStatus.DEPRECATED));
    m.put(DeploymentStatus.ACTIVE, List.of(DeploymentStatus.DEPRECATED));
    m.put(DeploymentStatus.DEPRECATED, List.of(DeploymentStatus.STAGED));
    m.put(DeploymentStatus.REJECTED, List.of());
    return m;
  }
}
