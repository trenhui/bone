package com.bone.studio.generator.domain.service;

import com.bone.studio.generator.domain.model.code.CodeGenerationRequest;
import com.bone.studio.generator.domain.model.code.CodeGenerationResponse;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import java.util.List;

public interface CodeGeneratorService {
  CodeGenerationResponse generateCode(CodeGenerationRequest request);

  boolean testConnection(DataSource dataSource);

  List<DatabaseTable> loadTables(String dataSourceId);

  List<DatabaseTable> loadCatalogTables(Long tenantId, List<String> entityCodes);
}
