package com.bone.studio.generator.application.query.handler;

import com.bone.core.result.PageResult;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateListQry;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetCodeTemplateListQueryHandler {

    private final CodeTemplateRepository codeTemplateRepository;

    public PageResult<CodeTemplate> handle(GetCodeTemplateListQry qry) {
        // 暂时返回空列表，后续可以根据实际需求实现查询逻辑
        List<CodeTemplate> templates = new java.util.ArrayList<>();
        int total = templates.size();
        return PageResult.of(templates, total, qry.getPage(), qry.getSize());
    }
}
