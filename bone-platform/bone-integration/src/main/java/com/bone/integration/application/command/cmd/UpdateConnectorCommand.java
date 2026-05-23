package com.bone.integration.application.command.cmd;

import java.util.Map;

public record UpdateConnectorCommand(Long id, String name, String type, Map<String, Object> config) {
}