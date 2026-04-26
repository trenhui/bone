package com.bone.lowcode.infra.domain.model;

import lombok.Data;

@Data
public class SingleFieldRule {

    private String fieldId;

    private Boolean required;

    private Boolean unique;
}
