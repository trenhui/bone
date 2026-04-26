package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class MasterDataData {
    private String code;
    private String type ;
    private String name;
    private String parentCode;
    private String enumDesc;
    private String typeName;
    private String parentName;
    private Integer source;
}
