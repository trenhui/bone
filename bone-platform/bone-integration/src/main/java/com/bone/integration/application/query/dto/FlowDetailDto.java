package com.bone.integration.application.query.dto;

import java.util.List;
import java.util.Map;

/** 流程详情查询结果 */
public record FlowDetailDto(
    Long id,
    String name,
    String description,
    String status,
    List<FlowNodeDto> nodes,
    List<FlowConnectionDTO> connections) {

  public record FlowNodeDto(
      Long id,
      String name,
      String type,
      Map<String, Object> config,
      int positionX,
      int positionY) {}

  public record FlowConnectionDTO(
      Long id, Long sourceNodeId, Long targetNodeId, String condition) {}
}
