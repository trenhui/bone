package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class MasterDataEntityPageQry {
    private int pageNum;
    private int pageSize;
    private String keyword;
    private String category;
    private String status;
}