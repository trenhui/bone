package com.bone.studio.generator.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.studio.generator.domain.data.GenerationTask;

import java.util.List;

public interface GenerationTaskRepository extends Repository<GenerationTask, Long> {
    GenerationTask findByTaskId(String taskId);
    List<GenerationTask> findByDataSourceId(Long dataSourceId);
    List<GenerationTask> findByStatus(String status);
}
