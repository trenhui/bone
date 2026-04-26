package com.bone.lowcode.infra.domain.model;

import lombok.Data;

import java.util.List;

@Data
public class AddressData {

    private String id;

    private String code;

    private String name;

    private List<String> subElementCodeList;
}
