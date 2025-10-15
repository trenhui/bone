package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "数据源配置响应对象")
@Data
public class DataSourceConfigResponse {

    @Schema(description = "主键编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试数据库")
    private String name;

    @Schema(description = "JDBC连接URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "jdbc:mysql://localhost:3306/test_db")
    private String url;

    @Schema(description = "数据库用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "root")
    private String username;
    
    @Schema(description = "数据库密码", hidden = true) // 密码字段隐藏在API文档中
    private String password;
    
    @Schema(description = "JDBC驱动类名", requiredMode = Schema.RequiredMode.REQUIRED, example = "com.mysql.cj.jdbc.Driver")
    private String driverClassName;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "创建时间字符串格式")
    private String createTimeStr;

}
