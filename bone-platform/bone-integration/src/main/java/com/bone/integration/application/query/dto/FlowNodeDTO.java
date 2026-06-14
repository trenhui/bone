package com.bone.integration.application.query.dto;

import java.util.Map;

public record FlowNodeDTO(
    Long id, String name, String type, Map<String, Object> config, int positionX, int positionY) {}
