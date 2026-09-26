package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataFieldReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataFieldReq;
import com.bone.masterdata.adapter.web.dto.response.MasterDataFieldDetailResp;
import com.bone.masterdata.application.command.CreateMasterDataFieldCommand;
import com.bone.masterdata.application.command.UpdateMasterDataFieldCommand;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class MasterDataFieldWebConverter {

  public CreateMasterDataFieldCommand toCommand(CreateMasterDataFieldReq req) {
    return CreateMasterDataFieldCommand.builder()
        .masterDataEntityId(req.getMasterDataEntityId())
        .name(req.getName())
        .code(req.getCode())
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
        .createdAt(toInstant(dto.getCreatedAt()))
        .updatedAt(toInstant(dto.getUpdatedAt()))
        .sortOrder(dto.getSortOrder())
        .build();
  }

  private static Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }
}
