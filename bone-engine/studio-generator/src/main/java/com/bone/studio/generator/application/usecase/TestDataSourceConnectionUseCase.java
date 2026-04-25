package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.UseCase;
import com.bone.studio.generator.application.command.cmd.TestDataSourceConnectionCommand;
import com.bone.studio.generator.application.command.handler.TestDataSourceConnectionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(name = "TestDataSourceConnection", description = "测试数据源连接", transactional = false)
@Service
@RequiredArgsConstructor
public class TestDataSourceConnectionUseCase implements UseCaseExecutor<TestDataSourceConnectionCommand, Boolean> {

    private final TestDataSourceConnectionHandler testDataSourceConnectionHandler;

    @Override
    public Boolean execute(TestDataSourceConnectionCommand command) {
        return testDataSourceConnectionHandler.handle(command);
    }
}
