package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.query.qry.PermissionPageQry;
import com.bone.iam.application.query.handler.PermissionPageQueryHandler;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.core.model.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "PermissionPageQuery",
    description = "标准权限分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class PermissionPageQueryUseCase implements UseCaseExecutor<PermissionPageQry, PageResult<PermissionDTO>> {

    private final PermissionPageQueryHandler permissionPageQueryHandler;

    @Override
    public PageResult<PermissionDTO> execute(PermissionPageQry qry) {
        return permissionPageQueryHandler.handle(qry);
    }
}
