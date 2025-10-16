package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 数据源配置查询请求
 */
@Schema(description = "数据源配置查询请求")
@Data
public class DataSourceConfigQueryRequest {
    @Schema(description = "主键ID", example = "1024")
    private Long id;

    @Schema(description = "主键ID列表")
    private List<Long> idList;

    @Schema(description = "数据源名", example = "test")
    private String name;

    @Schema(description = "连接地址", example = "jdbc:mysql://127.0.0.1:3306/test-db")
    private String url;
}
