package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "管理后台 - 数据源配置创建/修改 Request VO")
public class DataSourceConfigSaveRequest {

    @Schema(description = "主键编号", example = "1024")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    @NotNull(message = "数据源名称不能为空")
    private String name;

    @Schema(description = "数据源连接", requiredMode = Schema.RequiredMode.REQUIRED, example = "jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro")
    @NotNull(message = "数据源连接不能为空")
    private String url;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "root")
    @NotNull(message = "用户名不能为空")
    private String username;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotNull(message = "密码不能为空")
    private String password;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
