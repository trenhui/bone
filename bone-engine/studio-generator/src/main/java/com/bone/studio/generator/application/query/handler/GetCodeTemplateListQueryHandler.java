package com.bone.studio.generator.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateListQry;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetCodeTemplateListQueryHandler {

    private final CodeTemplateRepository codeTemplateRepository;

    public PageResult<CodeTemplate> handle(GetCodeTemplateListQry qry) {
        int pageNo = qry.getPage() != null ? qry.getPage() : 1;
        int pageSize = qry.getSize() != null ? qry.getSize() : 10;
        Criteria<CodeTemplate> criteria = Criteria.<CodeTemplate>create().page(pageNo, pageSize);
        PageResult<CodeTemplate> sdkPage = codeTemplateRepository.pageByCriteria(criteria);
        long total = sdkPage.getTotal() != null ? sdkPage.getTotal() : 0L;
        return PageResult.of(sdkPage.getRecords(), total, pageNo, pageSize);
    }
}
