package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.cmd.CreateAlertRuleCmd;
import com.bone.system.application.command.handler.AlertCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreateAlertRule",
    description = "标准告警规则创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateAlertRuleUseCase implements UseCaseExecutor<CreateAlertRuleCmd, Long> {

    private final AlertCommandHandler alertCommandHandler;

    @Override
    public Long execute(CreateAlertRuleCmd cmd) {
        return alertCommandHandler.handle(cmd);
    }
}
