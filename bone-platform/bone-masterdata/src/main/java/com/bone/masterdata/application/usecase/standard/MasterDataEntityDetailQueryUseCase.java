package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.query.qry.MasterDataEntityByIdQry;
import com.bone.masterdata.application.query.handler.MasterDataEntityDetailQueryHandler;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "MasterDataEntityDetailQuery",
    description = "标准主数据实体详情查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class MasterDataEntityDetailQueryUseCase implements UseCaseExecutor<MasterDataEntityByIdQry, MasterDataEntityDTO> {

    private final MasterDataEntityDetailQueryHandler masterDataEntityDetailQueryHandler;

    @Override
    public MasterDataEntityDTO execute(MasterDataEntityByIdQry qry) {
        return masterDataEntityDetailQueryHandler.handle(qry);
    }
}
