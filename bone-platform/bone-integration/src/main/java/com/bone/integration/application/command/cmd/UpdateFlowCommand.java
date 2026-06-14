package com.bone.integration.application.command.cmd;

import java.util.List;

public record UpdateFlowCommand(
    Long id,
    String name,
    String description,
    List<FlowNodeCommand> nodes,
    List<FlowConnectionCommand> connections) {
  public record FlowNodeCommand(
      Long id,
      String name,
      String type,
      java.util.Map<String, Object> config,
      int positionX,
      int positionY) {}

  public record FlowConnectionCommand(
      Long id, Long sourceNodeId, Long targetNodeId, String condition) {}
}
