package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class MasterDataRecordListQry {
    private int pageNum;
    private int pageSize;
    private Long masterDataEntityId;
    private String status;
    private String keyword;
}