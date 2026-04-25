package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.query.qry.MasterDataEntityPageQry;
import com.bone.masterdata.application.query.handler.MasterDataEntityPageQueryHandler;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.core.result.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "MasterDataEntityPageQuery",
    description = "标准主数据实体分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class MasterDataEntityPageQueryUseCase implements UseCaseExecutor<MasterDataEntityPageQry, PageResult<MasterDataEntityDTO>> {

    private final MasterDataEntityPageQueryHandler masterDataEntityPageQueryHandler;

    @Override
    public PageResult<MasterDataEntityDTO> execute(MasterDataEntityPageQry qry) {
        return masterDataEntityPageQueryHandler.handle(qry);
    }
}
