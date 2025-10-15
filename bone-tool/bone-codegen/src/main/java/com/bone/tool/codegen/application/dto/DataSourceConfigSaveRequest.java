package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "数据源配置创建/修改请求对象")
@Data
public class DataSourceConfigSaveRequest {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试数据库")
    @NotNull(message = "数据源名称不能为空")
    private String name;

    @Schema(description = "JDBC连接URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "jdbc:mysql://localhost:3306/test_db")
    @NotNull(message = "JDBC连接URL不能为空")
    private String url;

    @Schema(description = "数据库用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "root")
    @NotNull(message = "数据库用户名不能为空")
    private String username;

    @Schema(description = "数据库密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotNull(message = "数据库密码不能为空")
    private String password;
    
    @Schema(description = "JDBC驱动类名", requiredMode = Schema.RequiredMode.REQUIRED, example = "com.mysql.cj.jdbc.Driver")
    @NotNull(message = "JDBC驱动类名不能为空")
    private String driverClassName;
}
