package com.bone.system.adapter.web.converter;

import com.bone.system.adapter.web.dto.request.CreateDictReq;
import com.bone.system.adapter.web.dto.request.UpdateDictReq;
import com.bone.system.adapter.web.dto.response.DictResp;
import com.bone.system.application.command.cmd.CreateDictCommand;
import com.bone.system.application.command.cmd.UpdateDictCommand;
import com.bone.system.application.query.dto.DictDTO;
import org.springframework.stereotype.Component;

@Component
public class DictWebConverter {

  public CreateDictCommand toCommand(CreateDictReq req) {
    CreateDictCommand cmd = new CreateDictCommand();
    cmd.setType(req.getType());
    cmd.setTypeName(req.getTypeName());
    cmd.setCode(req.getCode());
    cmd.setLabel(req.getLabel());
    cmd.setValue(req.getValue());
    cmd.setSort(req.getSort());
    cmd.setStatus(req.getStatus());
    return cmd;
  }

  public UpdateDictCommand toCommand(Long id, UpdateDictReq req) {
    UpdateDictCommand cmd = new UpdateDictCommand();
    cmd.setId(id);
    cmd.setTypeName(req.getTypeName());
    cmd.setLabel(req.getLabel());
    cmd.setValue(req.getValue());
    cmd.setSort(req.getSort());
    cmd.setStatus(req.getStatus());
    return cmd;
  }

  public DictResp toResp(DictDTO dto) {
    return DictResp.builder()
        .id(dto.getId())
        .type(dto.getType())
        .typeName(dto.getTypeName())
        .code(dto.getCode())
        .label(dto.getLabel())
        .value(dto.getValue())
        .sort(dto.getSort())
        .status(dto.getStatus())
        .createdAt(dto.getCreatedAt())
        .updatedAt(dto.getUpdatedAt())
        .build();
  }
}
