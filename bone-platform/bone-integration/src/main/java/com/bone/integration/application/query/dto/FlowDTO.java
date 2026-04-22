package com.bone.integration.application.query.dto;

import java.util.List;
import java.util.Map;

public record FlowDTO(Long id, String name, String description, String status, List<FlowNodeDTO> nodes, List<FlowConnectionDTO> connections) {
    public record FlowNodeDTO(Long id, String name, String type, Map<String, Object> config, int positionX, int positionY) {
    }
    public record FlowConnectionDTO(Long id, Long sourceNodeId, Long targetNodeId, String condition) {
    }
}