package com.bone.masterdata.adapter.web.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MasterDataRecordDetailResp {
    private Long id;
    private Long masterDataEntityId;
    private String data;
    private String status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishTime;
}
