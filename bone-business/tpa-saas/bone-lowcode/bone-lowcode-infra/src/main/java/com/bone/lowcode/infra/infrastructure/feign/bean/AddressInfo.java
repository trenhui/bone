package com.bone.lowcode.infra.infrastructure.feign.bean;

import lombok.Data;

@Data
public class AddressInfo {

    private String id;

    private String type;

    private String typeName;

    private String code;

    private String name;

    private String parentCode;

    private String parentType;
}
