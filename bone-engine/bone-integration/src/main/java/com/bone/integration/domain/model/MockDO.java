package com.bone.integration.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MockDO extends BaseModel {
    private Long id;
    private String name;
    private String mockKey;
    private String appCode;
    private String remark;
    private String url;
    private String conditions;
    private Integer deleted;
}
