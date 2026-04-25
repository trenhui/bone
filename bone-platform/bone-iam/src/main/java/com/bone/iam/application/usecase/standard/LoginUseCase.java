package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.command.cmd.LoginCmd;
import com.bone.iam.application.command.handler.LoginHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@UseCase(
    name = "Login",
    description = "标准用户登录，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class LoginUseCase implements UseCaseExecutor<LoginCmd, Map<String, Object>> {

    private final LoginHandler loginHandler;

    @Override
    public Map<String, Object> execute(LoginCmd cmd) {
        return loginHandler.handle(cmd);
    }
}
