package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 测试连接请求
 */
@Schema(description = "测试连接请求")
@Data
public class TestConnectionRequest {
    
    @Schema(description = "数据源名", example = "测试数据库")
    private String name;
    
    @Schema(description = "连接地址", example = "jdbc:mysql://localhost:3306/test_db")
    @NotBlank(message = "连接地址不能为空")
    private String url;

    @Schema(description = "用户名", example = "root")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "密码", hidden = true)
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "驱动类", example = "com.mysql.cj.jdbc.Driver")
    @NotBlank(message = "驱动类不能为空")
    private String driverClassName;
}