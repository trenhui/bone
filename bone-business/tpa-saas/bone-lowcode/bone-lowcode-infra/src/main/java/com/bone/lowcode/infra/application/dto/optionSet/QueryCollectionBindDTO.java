package com.bone.lowcode.infra.application.dto.optionSet;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class QueryCollectionBindDTO {

    @NotEmpty(message = "bizIdentityCode不能为空")
    private String bizIdentityCode;

    private List<String> modelCodeList;
}
