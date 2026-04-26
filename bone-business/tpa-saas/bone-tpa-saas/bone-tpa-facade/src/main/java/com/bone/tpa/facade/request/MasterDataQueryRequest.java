package com.bone.tpa.facade.request;

import lombok.Data;

@Data
public class MasterDataQueryRequest {

    private String code;
    private String type ;
    private String name;
    private String parentCode;

}
