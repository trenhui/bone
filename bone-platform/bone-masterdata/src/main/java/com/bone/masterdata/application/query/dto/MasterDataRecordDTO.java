package com.bone.masterdata.application.query.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MasterDataRecordDTO {
    private Long id;
    private Long masterDataEntityId;
    private String data;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishTime;
}