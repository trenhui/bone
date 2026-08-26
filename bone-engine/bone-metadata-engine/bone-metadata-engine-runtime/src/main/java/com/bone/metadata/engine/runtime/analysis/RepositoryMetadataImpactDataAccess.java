package com.bone.metadata.engine.runtime.analysis;

import com.bone.metadata.engine.domain.metadata.WorkflowMetadata;
import com.bone.metadata.engine.runtime.repository.MetadataRepository;
import java.util.ArrayList;
import java.util.List;

/** 基于 {@link MetadataRepository} 的工作流影响读访问（实体目录由调用方注入）。 */
public class RepositoryMetadataImpactDataAccess implements MetadataImpactDataAccess {

  private final MetadataRepository metadataRepository;
  private final List<com.bone.metadata.engine.domain.model.EntityMetadata> entityCatalog;

  public RepositoryMetadataImpactDataAccess(
      MetadataRepository metadataRepository,
      List<com.bone.metadata.engine.domain.model.EntityMetadata> entityCatalog) {
    this.metadataRepository = metadataRepository;
    this.entityCatalog = entityCatalog != null ? List.copyOf(entityCatalog) : List.of();
  }

  @Override
  public List<com.bone.metadata.engine.domain.model.EntityMetadata> findAllEntities() {
    return entityCatalog;
  }

  @Override
  public List<WorkflowRef> findWorkflowsForEntity(String entityApiName) {
    if (entityApiName == null || entityApiName.isBlank()) {
      return List.of();
    }
    List<WorkflowMetadata> workflows =
        metadataRepository.findWorkflowsByEntityApiName(entityApiName);
    List<WorkflowRef> refs = new ArrayList<>();
    for (WorkflowMetadata workflow : workflows) {
      if (workflow == null) {
        continue;
      }
      refs.add(new WorkflowRef(workflow.getName(), workflow.getTargetEntity()));
    }
    return refs;
  }
}
