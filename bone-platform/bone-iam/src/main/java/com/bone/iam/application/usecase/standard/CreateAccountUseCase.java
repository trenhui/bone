package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.command.cmd.CreateAccountCmd;
import com.bone.iam.application.command.handler.CreateAccountHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreateAccount",
    description = "标准账户创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateAccountUseCase implements UseCaseExecutor<CreateAccountCmd, Long> {

    private final CreateAccountHandler createAccountHandler;

    @Override
    public Long execute(CreateAccountCmd cmd) {
        return createAccountHandler.handle(cmd);
    }
}
