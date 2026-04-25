package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.cmd.EnableAlertRuleCmd;
import com.bone.system.application.command.handler.AlertCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "EnableAlertRule",
    description = "标准告警规则启用，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class EnableAlertRuleUseCase implements UseCaseExecutor<EnableAlertRuleCmd, Void> {

    private final AlertCommandHandler alertCommandHandler;

    @Override
    public Void execute(EnableAlertRuleCmd cmd) {
        alertCommandHandler.handle(cmd);
        return null;
    }
}
