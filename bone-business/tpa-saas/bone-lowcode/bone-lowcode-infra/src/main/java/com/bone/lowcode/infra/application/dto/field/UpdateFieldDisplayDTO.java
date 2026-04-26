package com.bone.lowcode.infra.application.dto.field;

import lombok.Data;

import java.util.List;

@Data
public class UpdateFieldDisplayDTO {

    private Long modelId;

    private List<Long> fieldIdList;
}
