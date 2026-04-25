package com.bone.studio.generator.domain.service;

import com.bone.studio.generator.domain.code.CodeGenerationRequest;
import com.bone.studio.generator.domain.code.CodeGenerationResponse;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.data.DatabaseTable;
import java.util.List;

public interface CodeGeneratorService {
    CodeGenerationResponse generateCode(CodeGenerationRequest request);
    boolean testConnection(DataSource dataSource);
    List<DatabaseTable> loadTables(String dataSourceId);
}