package com.bone.studio.generator.application;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.query.qry.LoadTablesQuery;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Capability(name = "loadTables", description = "加载数据源表结构", inputSchema = "{}", outputSchema = "{}")
public class LoadTablesApplicationService {

  private final CodeGeneratorService codeGeneratorService;

  public LoadTablesApplicationService(CodeGeneratorService codeGeneratorService) {
    this.codeGeneratorService = codeGeneratorService;
  }

  @Transactional(readOnly = true)
  public List<DatabaseTable> handle(LoadTablesQuery query) {
    return codeGeneratorService.loadTables(query.getDataSourceId());
  }
}
