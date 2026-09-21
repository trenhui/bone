package com.bone.studio.generator.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.data.GenerationTask;

public interface GenerationTaskRepository extends Repository<GenerationTask, Long> {

  default GenerationTask findByTaskId(String taskId) throws MultipleResultsException {
    return findOneByCriteria(
        Criteria.<GenerationTask>create().eq(GenerationTask::getTaskId, taskId));
  }
}
