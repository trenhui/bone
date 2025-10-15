package com.bone.tool.codegen.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据源配置保存请求
 * <p>
 * 用于创建或更新数据源配置的应用层DTO
 */
@Schema(description = "数据源配置保存请求")
@Data
public class DataSourceConfigSaveRequest {

    @Schema(description = "主键ID", example = "1024")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试数据库")
    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 100, message = "数据源名称长度不能超过100个字符")
    private String name;

    @Schema(description = "JDBC连接URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "jdbc:mysql://localhost:3306/test_db")
    @NotBlank(message = "JDBC连接URL不能为空")
    @Size(max = 500, message = "JDBC连接URL长度不能超过500个字符")
    private String url;

    @Schema(description = "数据库用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "root")
    @NotBlank(message = "数据库用户名不能为空")
    @Size(max = 50, message = "数据库用户名长度不能超过50个字符")
    private String username;

    @Schema(description = "数据库密码", hidden = true) // 密码字段在API文档中隐藏
    @NotBlank(message = "数据库密码不能为空")
    @Size(max = 50, message = "数据库密码长度不能超过50个字符")
    private String password;
    
    @Schema(description = "JDBC驱动类名", requiredMode = Schema.RequiredMode.REQUIRED, example = "com.mysql.cj.jdbc.Driver")
    @NotBlank(message = "JDBC驱动类名不能为空")
    @Size(max = 200, message = "JDBC驱动类名长度不能超过200个字符")
    private String driverClassName;
    
    /**
     * 检查是否为更新操作
     * @return 是否为更新操作
     */
    @JsonIgnore
    public boolean isUpdate() {
        return id != null;
    }
}
