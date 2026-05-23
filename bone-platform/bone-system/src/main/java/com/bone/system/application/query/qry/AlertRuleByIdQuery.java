package com.bone.system.application.query.qry;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 根据ID查询告警规则
 */
@Data
public class AlertRuleByIdQuery {
    @NotNull(message = "规则ID不能为空")
    private Long id;
}
