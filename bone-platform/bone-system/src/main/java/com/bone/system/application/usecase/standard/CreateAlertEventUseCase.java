package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.handler.AlertCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@UseCase(
    name = "CreateAlertEvent",
    description = "标准告警事件创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateAlertEventUseCase implements UseCaseExecutor<Map<String, Object>, Long> {

    private final AlertCommandHandler alertCommandHandler;

    @Override
    public Long execute(Map<String, Object> params) {
        Long ruleId = (Long) params.get("ruleId");
        Double actualValue = (Double) params.get("actualValue");
        return alertCommandHandler.createAlertEvent(ruleId, actualValue);
    }
}
