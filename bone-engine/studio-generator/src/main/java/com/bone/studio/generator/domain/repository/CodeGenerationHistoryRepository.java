package com.bone.studio.generator.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.studio.generator.domain.history.CodeGenerationHistory;

import java.util.List;

public interface CodeGenerationHistoryRepository extends Repository<CodeGenerationHistory, Long> {
    List<CodeGenerationHistory> findByTaskId(String taskId);
    List<CodeGenerationHistory> findByDataSourceId(String dataSourceId);
    List<CodeGenerationHistory> findByStatus(String status);
}