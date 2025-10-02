package com.bone.lowcode.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DataSourceConfigQueryRequest {
    private Integer id;

    private List<Integer> idList;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    private String name;

    @Schema(description = "数据源连接", requiredMode = Schema.RequiredMode.REQUIRED, example = "jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro")
    private String url;
}
