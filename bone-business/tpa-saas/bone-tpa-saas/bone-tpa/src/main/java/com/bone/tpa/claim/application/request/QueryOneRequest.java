package com.bone.tpa.claim.application.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class QueryOneRequest implements Serializable {

    @Schema(description = "表记录id")
    private Long id;

    @Schema(description = "模型名称")
    private List<String> modelNames = new ArrayList<>();
}
