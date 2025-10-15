package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

/**
 * 数据源配置查询请求
 * <p>
 * 用于分页查询数据源配置的应用层DTO
 */
@Schema(description = "数据源配置查询请求")
@Data
public class DataSourceConfigQueryRequest {
    @Schema(description = "主键ID", example = "1024")
    private Long id;

    @Schema(description = "主键ID列表")
    private List<Long> idList;

    @Schema(description = "数据源名称", example = "test")
    @Length(max = 100, message = "数据源名称长度不能超过100个字符")
    private String name;

    @Schema(description = "JDBC连接URL", example = "jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro")
    @Length(max = 500, message = "JDBC连接URL长度不能超过500个字符")
    private String url;
}
