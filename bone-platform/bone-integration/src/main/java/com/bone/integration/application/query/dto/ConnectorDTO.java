package com.bone.integration.application.query.dto;

import java.util.Map;

public record ConnectorDTO(Long id, String name, String type, Map<String, Object> config, String status) {
}