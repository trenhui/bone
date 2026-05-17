package com.bone.masterdata.application.usecase.standard;

import com.bone.core.result.PageResult;
import com.bone.core.usecase.UseCase;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.handler.MasterDataRecordListQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
        name = "MasterDataRecordListQuery",
        description = "标准主数据记录列表查询，支持 AI 编排与多租户扩展",
        transactional = false)
@Service
@RequiredArgsConstructor
public class MasterDataRecordListQueryUseCase
        implements UseCaseExecutor<MasterDataRecordListQry, PageResult<MasterDataRecordDTO>> {

    private final MasterDataRecordListQueryHandler masterDataRecordListQueryHandler;

    @Override
    public PageResult<MasterDataRecordDTO> execute(MasterDataRecordListQry qry) {
        return masterDataRecordListQueryHandler.handle(qry);
    }
}
