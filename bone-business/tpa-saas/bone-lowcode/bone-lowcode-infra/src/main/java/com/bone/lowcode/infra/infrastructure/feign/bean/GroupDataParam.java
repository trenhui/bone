package com.bone.lowcode.infra.infrastructure.feign.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupDataParam {

    private Integer pageNumber = 1;

    private Integer pageSize = 10000;

    private String type;

    private String parentCode;
}
