package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.query.qry.RolePageQry;
import com.bone.iam.application.query.handler.RolePageQueryHandler;
import com.bone.iam.application.query.dto.RoleDTO;
import com.bone.core.model.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "RolePageQuery",
    description = "标准角色分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class RolePageQueryUseCase implements UseCaseExecutor<RolePageQry, PageResult<RoleDTO>> {

    private final RolePageQueryHandler rolePageQueryHandler;

    @Override
    public PageResult<RoleDTO> execute(RolePageQry qry) {
        return rolePageQueryHandler.handle(qry);
    }
}
