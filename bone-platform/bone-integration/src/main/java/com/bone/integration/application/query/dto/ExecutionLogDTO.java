package com.bone.integration.application.query.dto;

import java.time.LocalDateTime;

public record ExecutionLogDTO(Long id, Long flowId, String status, LocalDateTime startTime, LocalDateTime endTime, String inputData, String outputData, String errorMessage) {
}