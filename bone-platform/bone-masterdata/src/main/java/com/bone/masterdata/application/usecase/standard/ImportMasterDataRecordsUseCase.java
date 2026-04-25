package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCmd;
import com.bone.masterdata.application.command.handler.ImportMasterDataRecordsHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "ImportMasterDataRecords",
    description = "标准主数据记录导入，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class ImportMasterDataRecordsUseCase implements UseCaseExecutor<ImportMasterDataRecordsCmd, Void> {

    private final ImportMasterDataRecordsHandler importMasterDataRecordsHandler;

    @Override
    public Void execute(ImportMasterDataRecordsCmd cmd) {
        importMasterDataRecordsHandler.handle(cmd);
        return null;
    }
}
