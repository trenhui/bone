package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.response.MasterDataRecordDetailResp;
import com.bone.masterdata.application.command.cmd.CreateMasterDataRecordCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataRecordCommand;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class MasterDataRecordWebConverter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public CreateMasterDataRecordCommand toCommand(CreateMasterDataRecordReq req) {
    return CreateMasterDataRecordCommand.builder()
        .masterDataEntityId(req.getMasterDataEntityId())
        .data(serializeData(req.getData()))
        .build();
  }

  public UpdateMasterDataRecordCommand toCommand(Long id, UpdateMasterDataRecordReq req) {
    return UpdateMasterDataRecordCommand.builder()
        .id(id)
        .data(serializeData(req.getData()))
        .build();
  }

  public MasterDataRecordDetailResp toResp(MasterDataRecordDTO dto) {
    return MasterDataRecordDetailResp.builder()
        .id(dto.getId())
        .masterDataEntityId(dto.getMasterDataEntityId())
        .data(dto.getData())
        .status(dto.getStatus())
        .createdAt(dto.getCreatedAt())
        .updatedAt(dto.getUpdatedAt())
        .publishTime(dto.getPublishTime())
        .build();
  }

  private String serializeData(Object data) {
    if (data == null) {
      return null;
    }
    if (data instanceof String) {
      return (String) data;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化数据失败", e);
    }
  }
}
