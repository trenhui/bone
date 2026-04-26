package com.bone.tpa.claim.application.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DeleteRequest implements Serializable {

    @Schema(description = "表记录id")
    private List<Long> idList;

    @Schema(description = "模型名称")
    private String modelNames;
}
