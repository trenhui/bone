package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.response.MasterDataRecordDetailResp;
import com.bone.masterdata.adapter.web.dto.response.MasterDataRecordVersionResp;
import com.bone.masterdata.application.command.CreateMasterDataRecordCommand;
import com.bone.masterdata.application.command.UpdateMasterDataRecordCommand;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.domain.model.record.MasterDataRecordVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
        .createdAt(toInstant(dto.getCreatedAt()))
        .updatedAt(toInstant(dto.getUpdatedAt()))
        .publishTime(toInstant(dto.getPublishTime()))
        .build();
  }

  /** 领域版本实体 → 版本历史响应，避免领域模型直接出现在 API 响应体（DTO 纪律收口）。 */
  public MasterDataRecordVersionResp toVersionResp(MasterDataRecordVersion version) {
    return MasterDataRecordVersionResp.builder()
        .id(version.getId())
        .recordId(version.getRecordId())
        .versionNumber(version.getVersionNumber())
        .data(version.getData())
        .status(version.getStatus())
        .changeDescription(version.getChangeDescription())
        .approvedBy(version.getApprovedBy())
        .approvedAt(toInstant(version.getApprovedAt()))
        .createdBy(version.getCreatedBy())
        .createdAt(toInstant(version.getCreatedAt()))
        .build();
  }

  public List<MasterDataRecordVersionResp> toVersionRespList(
      List<MasterDataRecordVersion> versions) {
    return versions.stream().map(this::toVersionResp).toList();
  }

  private static Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
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
