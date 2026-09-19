package com.bone.studio.generator.infrastructure.gateway;

import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.data.GenerationTask;
import com.bone.studio.generator.domain.gateway.GenerationTaskReadPort;
import com.bone.studio.generator.domain.repository.GenerationTaskRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link GenerationTaskReadPort} 实现：读侧 DSL 仅出现在 infrastructure 查询层。 */
@Component
@RequiredArgsConstructor
public class GenerationTaskReadPortImpl implements GenerationTaskReadPort {

  private final GenerationTaskRepository generationTaskRepository;

  @Override
  public Optional<GenerationTask> findByTaskId(String taskId) {
    try {
      return Optional.ofNullable(
          generationTaskRepository.findOneByCriteria(
              Criteria.<GenerationTask>create().eq("taskId", taskId)));
    } catch (MultipleResultsException e) {
      throw new IllegalStateException("duplicate generation task: " + taskId, e);
    }
  }
}
