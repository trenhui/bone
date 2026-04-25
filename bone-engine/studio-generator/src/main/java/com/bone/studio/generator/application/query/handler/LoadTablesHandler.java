package com.bone.studio.generator.application.query.handler;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.query.qry.LoadTablesQry;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
@Capability(name = "loadTables", description = "加载数据源表结构", inputSchema = "{}", outputSchema = "{}")
public class LoadTablesHandler {

    private final CodeGeneratorService codeGeneratorService;

    public LoadTablesHandler(CodeGeneratorService codeGeneratorService) {
        this.codeGeneratorService = codeGeneratorService;
    }

    @Transactional(readOnly = true)
    public List<DatabaseTable> handle(LoadTablesQry query) {
        return codeGeneratorService.loadTables(query.getDataSourceId());
    }
}