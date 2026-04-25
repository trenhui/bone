package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.query.qry.AccountPageQry;
import com.bone.iam.application.query.handler.AccountPageQueryHandler;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.core.model.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "AccountPageQuery",
    description = "标准账户分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class AccountPageQueryUseCase implements UseCaseExecutor<AccountPageQry, PageResult<AccountDTO>> {

    private final AccountPageQueryHandler accountPageQueryHandler;

    @Override
    public PageResult<AccountDTO> execute(AccountPageQry qry) {
        return accountPageQueryHandler.handle(qry);
    }
}
