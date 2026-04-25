package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.command.cmd.CreateMasterDataRecordCmd;
import com.bone.masterdata.application.command.handler.CreateMasterDataRecordHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreateMasterDataRecord",
    description = "标准主数据记录创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateMasterDataRecordUseCase implements UseCaseExecutor<CreateMasterDataRecordCmd, Long> {

    private final CreateMasterDataRecordHandler createMasterDataRecordHandler;

    @Override
    public Long execute(CreateMasterDataRecordCmd cmd) {
        return createMasterDataRecordHandler.handle(cmd);
    }
}
