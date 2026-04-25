package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.UseCase;
import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.application.service.CodeGenerationTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(name = "CreateCodeGeneration", description = "创建代码生成任务", transactional = false)
@Service
@RequiredArgsConstructor
public class CreateCodeGenerationUseCase implements UseCaseExecutor<CreateCodeGenerationCommand, String> {

    private final CodeGenerationTaskService codeGenerationTaskService;

    @Override
    public String execute(CreateCodeGenerationCommand command) {
        // 同步执行代码生成
        // 实际项目中可以使用异步执行，并返回任务ID
        final String[] taskId = new String[1];
        codeGenerationTaskService.executeTask(command, new CodeGenerationTaskService.CodeGenerationCallback() {
            @Override
            public void onSuccess(String id) {
                taskId[0] = id;
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        });
        return taskId[0];
    }
}
