package com.bone.lowcode.infra.domain.model;

import lombok.Data;

import java.util.List;

@Data
public class GroupFieldRule {

    private List<String> fieldIdList;

    private Boolean unique;

    private List<String> fieldIdListA;

    private List<String> fieldIdListB;
}
