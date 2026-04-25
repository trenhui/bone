package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.command.cmd.EnableAccountCmd;
import com.bone.iam.application.command.handler.EnableAccountHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "EnableAccount",
    description = "标准账户启用，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class EnableAccountUseCase implements UseCaseExecutor<EnableAccountCmd, Void> {

    private final EnableAccountHandler enableAccountHandler;

    @Override
    public Void execute(EnableAccountCmd cmd) {
        enableAccountHandler.handle(cmd);
        return null;
    }
}
