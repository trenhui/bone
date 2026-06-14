package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.req.CreateMasterDataFieldReq;
import com.bone.masterdata.adapter.web.dto.req.UpdateMasterDataFieldReq;
import com.bone.masterdata.adapter.web.dto.resp.MasterDataFieldDetailResp;
import com.bone.masterdata.application.command.cmd.CreateMasterDataFieldCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataFieldCommand;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import org.springframework.stereotype.Component;

@Component
public class MasterDataFieldWebConverter {

  public CreateMasterDataFieldCommand toCommand(CreateMasterDataFieldReq req) {
    return CreateMasterDataFieldCommand.builder()
        .masterDataEntityId(req.getMasterDataEntityId())
        .name(req.getName())
        .type(req.getType())
        .length(req.getLength())
        .required(req.getRequired())
        .defaultValue(req.getDefaultValue())
        .description(req.getDescription())
        .build();
  }

  public UpdateMasterDataFieldCommand toCommand(Long id, UpdateMasterDataFieldReq req) {
    return UpdateMasterDataFieldCommand.builder()
        .id(id)
        .name(req.getName())
        .type(req.getType())
        .length(req.getLength())
        .required(req.getRequired())
        .defaultValue(req.getDefaultValue())
        .description(req.getDescription())
        .sortOrder(req.getSortOrder())
        .build();
  }

  public MasterDataFieldDetailResp toResp(MasterDataFieldDTO dto) {
    return MasterDataFieldDetailResp.builder()
        .id(dto.getId())
        .masterDataEntityId(dto.getMasterDataEntityId())
        .name(dto.getName())
        .code(dto.getCode())
        .type(dto.getType())
        .length(dto.getLength())
        .required(dto.getRequired())
        .defaultValue(dto.getDefaultValue())
        .description(dto.getDescription())
        .sortOrder(dto.getSortOrder())
        .build();
  }
}
