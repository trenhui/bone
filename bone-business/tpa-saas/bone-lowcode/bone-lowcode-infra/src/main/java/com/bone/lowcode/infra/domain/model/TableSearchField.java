package com.bone.lowcode.infra.domain.model;

import lombok.Data;

@Data
public class TableSearchField {

    private String fieldId;

    private Integer sequence;

    //是否必填，0：不必填，1：必填
    private Byte required;

    //搜索方式
    private Byte searchMode;
}
