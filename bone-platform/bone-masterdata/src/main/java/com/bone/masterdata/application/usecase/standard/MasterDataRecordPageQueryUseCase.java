package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.query.qry.MasterDataRecordPageQry;
import com.bone.masterdata.application.query.handler.MasterDataRecordPageQueryHandler;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.core.result.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "MasterDataRecordPageQuery",
    description = "标准主数据记录分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class MasterDataRecordPageQueryUseCase implements UseCaseExecutor<MasterDataRecordPageQry, PageResult<MasterDataRecordDTO>> {

    private final MasterDataRecordPageQueryHandler masterDataRecordPageQueryHandler;

    @Override
    public PageResult<MasterDataRecordDTO> execute(MasterDataRecordPageQry qry) {
        return masterDataRecordPageQueryHandler.handle(qry);
    }
}
