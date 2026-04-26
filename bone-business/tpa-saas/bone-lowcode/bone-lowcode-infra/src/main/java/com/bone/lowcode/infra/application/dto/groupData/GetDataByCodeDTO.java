package com.bone.lowcode.infra.application.dto.groupData;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class GetDataByCodeDTO {

    @NotNull(message = "数据来源类型不能为空")
    private Byte type;

    /**
     * 选项集code、枚举类型code
     */
    @NotEmpty(message = "根节点code不能为空")
    private String rootCode;

    @NotEmpty(message = "codeList不能为空")
    private List<String> codeList;
}
