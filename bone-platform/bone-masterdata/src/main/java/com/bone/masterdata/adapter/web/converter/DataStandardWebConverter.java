package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.req.CreateDataStandardReq;
import com.bone.masterdata.adapter.web.dto.req.UpdateDataStandardReq;
import com.bone.masterdata.adapter.web.dto.resp.DataStandardResp;
import com.bone.masterdata.application.command.cmd.CreateDataStandardCommand;
import com.bone.masterdata.application.command.cmd.UpdateDataStandardCommand;
import com.bone.masterdata.application.query.dto.DataStandardDTO;
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
        .createdAt(dto.getCreatedAt())
        .updatedAt(dto.getUpdatedAt())
        .build();
  }
}
