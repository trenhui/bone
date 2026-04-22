package com.bone.system.adapter.web.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新告警规则请求
 */
@Data
public class UpdateAlertRuleReq {
    @NotNull(message = "规则ID不能为空")
    private Long id;

    private String name;

    private String description;

    private Double threshold;

    private String alertLevel;

    private List<String> notificationChannels;
}
