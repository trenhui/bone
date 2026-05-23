package com.bone.integration.application.command.cmd;

import java.util.List;

public record CreateFlowCommand(
        String name, String description, List<FlowNodeCommand> nodes, List<FlowConnectionCommand> connections) {
    public record FlowNodeCommand(
            String name, String type, java.util.Map<String, Object> config, int positionX, int positionY) {}

    public record FlowConnectionCommand(Long sourceNodeId, Long targetNodeId, String condition) {}
}