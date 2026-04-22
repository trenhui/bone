package com.bone.system.application.command.cmd;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateConfigCmd {
    @NotNull(message = "配置ID不能为空")
    private Long id;

    private String configValue;

    private String description;
}
