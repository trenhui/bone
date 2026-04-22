package com.bone.system.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建日志请求
 */
@Data
public class CreateLogReq {
    @NotBlank(message = "日志级别不能为空")
    private String logLevel;

    @NotBlank(message = "服务名称不能为空")
    private String serviceName;

    @NotBlank(message = "日志内容不能为空")
    private String content;

    private String traceId;
}
