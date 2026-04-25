package com.bone.studio.generator.application.usecase;

import com.bone.core.result.PageResult;
import com.bone.studio.generator.application.query.handler.GetCodeTemplateListQueryHandler;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateListQry;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.application.usecase.UseCaseExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetCodeTemplateListUseCase implements UseCaseExecutor<GetCodeTemplateListQry, PageResult<CodeTemplate>> {

    private final GetCodeTemplateListQueryHandler queryHandler;

    @Override
    public PageResult<CodeTemplate> execute(GetCodeTemplateListQry qry) {
        return queryHandler.handle(qry);
    }
}
