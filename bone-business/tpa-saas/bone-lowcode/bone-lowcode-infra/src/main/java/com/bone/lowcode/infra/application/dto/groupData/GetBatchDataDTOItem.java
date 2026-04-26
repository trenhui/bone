package com.bone.lowcode.infra.application.dto.groupData;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GetBatchDataDTOItem {

    @NotNull(message = "数据来源类型不能为空")
    private Byte type;

    @NotEmpty(message = "数据来源code不能为空")
    private String parentCode;
}
