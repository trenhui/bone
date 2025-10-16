package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据源配置保存请求
 */
@Schema(description = "数据源配置保存请求")
@Data
public class DataSourceConfigSaveRequest {

    @Schema(description = "主键ID", example = "1024")
    private Long id;

    @Schema(description = "数据源名", example = "测试数据库")
    @NotBlank(message = "数据源名不能为空")
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
    
    /**
     * 判断是否为更新操作
     * @return 是否为更新操作
     */
    public boolean isUpdate() {
        return id != null;
    }
}
