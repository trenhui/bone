package com.bone.masterdata.adapter.web.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class MasterDataRecordDetailResp {
    private Long id;
    private Long masterDataEntityId;
    private String data;
    private String status;
    private Integer version;
    private Date createdAt;
    private Date updatedAt;
}
