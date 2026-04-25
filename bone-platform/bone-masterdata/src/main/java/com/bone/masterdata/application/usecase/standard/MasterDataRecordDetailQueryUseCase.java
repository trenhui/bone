package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQry;
import com.bone.masterdata.application.query.handler.MasterDataRecordDetailQueryHandler;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "MasterDataRecordDetailQuery",
    description = "标准主数据记录详情查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class MasterDataRecordDetailQueryUseCase implements UseCaseExecutor<MasterDataRecordByIdQry, MasterDataRecordDTO> {

    private final MasterDataRecordDetailQueryHandler masterDataRecordDetailQueryHandler;

    @Override
    public MasterDataRecordDTO execute(MasterDataRecordByIdQry qry) {
        return masterDataRecordDetailQueryHandler.handle(qry);
    }
}
