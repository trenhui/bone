package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "数据源配置响应")
@Data
public class DataSourceConfigResponse {

    @Schema(description = "主键ID", example = "1024")
    private Long id;

    @Schema(description = "数据源名", example = "测试数据库")
    private String name;

    @Schema(description = "连接地址", example = "jdbc:mysql://localhost:3306/test_db")
    private String url;

    @Schema(description = "用户名", example = "root")
    private String username;
    
    @Schema(description = "密码", hidden = true)
    private String password;
    
    @Schema(description = "驱动类", example = "com.mysql.cj.jdbc.Driver")
    private String driverClassName;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "创建时间字符串")
    private String createdAtStr;
}
