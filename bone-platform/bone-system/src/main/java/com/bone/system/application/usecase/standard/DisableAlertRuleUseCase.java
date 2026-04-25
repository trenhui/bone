package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.cmd.DisableAlertRuleCmd;
import com.bone.system.application.command.handler.AlertCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "DisableAlertRule",
    description = "标准告警规则禁用，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class DisableAlertRuleUseCase implements UseCaseExecutor<DisableAlertRuleCmd, Void> {

    private final AlertCommandHandler alertCommandHandler;

    @Override
    public Void execute(DisableAlertRuleCmd cmd) {
        alertCommandHandler.handle(cmd);
        return null;
    }
}
