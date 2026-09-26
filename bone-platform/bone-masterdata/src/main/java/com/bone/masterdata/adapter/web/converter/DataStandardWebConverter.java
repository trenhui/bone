package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.request.CreateDataStandardReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateDataStandardReq;
import com.bone.masterdata.adapter.web.dto.response.DataStandardResp;
import com.bone.masterdata.application.command.CreateDataStandardCommand;
import com.bone.masterdata.application.command.UpdateDataStandardCommand;
import com.bone.masterdata.application.query.dto.DataStandardDTO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class DataStandardWebConverter {

  public CreateDataStandardCommand toCommand(CreateDataStandardReq req) {
    CreateDataStandardCommand cmd = new CreateDataStandardCommand();
    cmd.setEntityCode(req.getEntityCode());
    cmd.setFieldCode(req.getFieldCode());
    cmd.setRuleType(req.getRuleType());
    cmd.setPattern(req.getPattern());
    cmd.setRefCode(req.getRefCode());
    cmd.setDescription(req.getDescription());
    return cmd;
  }

  public UpdateDataStandardCommand toCommand(Long id, UpdateDataStandardReq req) {
    UpdateDataStandardCommand cmd = new UpdateDataStandardCommand();
    cmd.setId(id);
    cmd.setRuleType(req.getRuleType());
    cmd.setPattern(req.getPattern());
    cmd.setRefCode(req.getRefCode());
    cmd.setDescription(req.getDescription());
    return cmd;
  }

  public DataStandardResp toResp(DataStandardDTO dto) {
    return DataStandardResp.builder()
        .id(dto.getId())
        .entityCode(dto.getEntityCode())
        .fieldCode(dto.getFieldCode())
        .ruleType(dto.getRuleType())
        .pattern(dto.getPattern())
        .refCode(dto.getRefCode())
        .description(dto.getDescription())
        .createdAt(toInstant(dto.getCreatedAt()))
        .updatedAt(toInstant(dto.getUpdatedAt()))
        .build();
  }

  private static Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }
}
