package com.bone.system.application.usecase.standard;

import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.handler.AlertQueryHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "AlertRuleByIdQuery",
    description = "标准告警规则根据ID查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class AlertRuleByIdQueryUseCase implements UseCaseExecutor<Long, AlertRuleDTO> {

    private final AlertQueryHandler alertQueryHandler;

    @Override
    public AlertRuleDTO execute(Long id) {
        return alertQueryHandler.getRuleById(id);
    }
}
