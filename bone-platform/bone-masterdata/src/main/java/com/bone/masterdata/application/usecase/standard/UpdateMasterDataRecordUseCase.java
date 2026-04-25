package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.command.cmd.UpdateMasterDataRecordCmd;
import com.bone.masterdata.application.command.handler.UpdateMasterDataRecordHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "UpdateMasterDataRecord",
    description = "标准主数据记录更新，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class UpdateMasterDataRecordUseCase implements UseCaseExecutor<UpdateMasterDataRecordCmd, Void> {

    private final UpdateMasterDataRecordHandler updateMasterDataRecordHandler;

    @Override
    public Void execute(UpdateMasterDataRecordCmd cmd) {
        updateMasterDataRecordHandler.handle(cmd);
        return null;
    }
}
