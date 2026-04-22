package com.bone.masterdata.adapter.web.dto.req;

import lombok.Data;

@Data
public class CreateMasterDataRecordReq {
    private Long masterDataEntityId;
    private String data;
}
