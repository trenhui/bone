package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataEntityReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataEntityReq;
import com.bone.masterdata.adapter.web.dto.response.MasterDataEntityDetailResp;
import com.bone.masterdata.application.command.CreateMasterDataEntityCommand;
import com.bone.masterdata.application.command.UpdateMasterDataEntityCommand;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import org.springframework.stereotype.Component;

@Component
public class MasterDataEntityWebConverter {

  public CreateMasterDataEntityReq toReq(CreateMasterDataEntityCommand cmd) {
    CreateMasterDataEntityReq req = new CreateMasterDataEntityReq();
    req.setName(cmd.getName());
    req.setDescription(cmd.getDescription());
    req.setCategory(cmd.getCategory());
    return req;
  }

  public CreateMasterDataEntityCommand toCommand(CreateMasterDataEntityReq req) {
    return CreateMasterDataEntityCommand.builder()
        .name(req.getName())
        .description(req.getDescription())
        .category(req.getCategory())
        .build();
  }

  public UpdateMasterDataEntityReq toReq(UpdateMasterDataEntityCommand cmd) {
    UpdateMasterDataEntityReq req = new UpdateMasterDataEntityReq();
    req.setName(cmd.getName());
    req.setDescription(cmd.getDescription());
    req.setCategory(cmd.getCategory());
    return req;
  }

  public UpdateMasterDataEntityCommand toCommand(Long id, UpdateMasterDataEntityReq req) {
    return UpdateMasterDataEntityCommand.builder()
        .id(id)
        .name(req.getName())
        .description(req.getDescription())
        .category(req.getCategory())
        .build();
  }

  public MasterDataEntityDetailResp toResp(MasterDataEntityDTO dto) {
    return MasterDataEntityDetailResp.builder()
        .id(dto.getId())
        .name(dto.getName())
        .description(dto.getDescription())
        .category(dto.getCategory())
        .status(dto.getStatus())
        .createdAt(dto.getCreatedAt())
        .updatedAt(dto.getUpdatedAt())
        .fieldCount(dto.getFieldCount())
        .build();
  }
}
