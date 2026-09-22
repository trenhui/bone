package com.bone.engine.extension.studio.domain.model.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeploymentStateMachineTest {

  @Test
  @DisplayName("UPLOADED 仅可迁移到 VALIDATED 或 REJECTED")
  void uploadedTransitions() {
    List<DeploymentStatus> next = DeploymentStateMachine.nextStates(DeploymentStatus.UPLOADED);
    assertEquals(2, next.size());
    assertTrue(next.contains(DeploymentStatus.VALIDATED));
    assertTrue(next.contains(DeploymentStatus.REJECTED));
  }

  @Test
  @DisplayName("REJECTED 是终态")
  void rejectedIsTerminal() {
    assertTrue(DeploymentStateMachine.nextStates(DeploymentStatus.REJECTED).isEmpty());
  }

  @Test
  @DisplayName("canTransit 正反验证")
  void canTransit() {
    assertTrue(DeploymentStateMachine.canTransit(DeploymentStatus.STAGED, DeploymentStatus.ACTIVE));
    assertFalse(
        DeploymentStateMachine.canTransit(DeploymentStatus.UPLOADED, DeploymentStatus.ACTIVE));
  }

  @Test
  @DisplayName("空当前状态返回 UPLOADED 作为起点")
  void nullCurrentReturnsStart() {
    assertEquals(List.of(DeploymentStatus.UPLOADED), DeploymentStateMachine.nextStates(null));
  }
}
