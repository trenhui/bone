package com.bone.integration.application.command.cmd;

import java.util.List;

public record CreateFlowCmd(String name, String description, List<FlowNodeCmd> nodes, List<FlowConnectionCmd> connections) {
    public record FlowNodeCmd(String name, String type, java.util.Map<String, Object> config, int positionX, int positionY) {
    }
    public record FlowConnectionCmd(Long sourceNodeId, Long targetNodeId, String condition) {
    }
}