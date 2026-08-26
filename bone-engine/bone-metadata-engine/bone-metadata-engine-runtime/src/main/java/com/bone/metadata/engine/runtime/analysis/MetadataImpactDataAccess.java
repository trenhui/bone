package com.bone.metadata.engine.runtime.analysis;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import java.util.Collections;
import java.util.List;

/** 影响分析所需元数据读模型（与 {@code metadata.EntityMetadata} 仓储解耦）。 */
public interface MetadataImpactDataAccess {

  List<EntityMetadata> findAllEntities();

  List<WorkflowRef> findWorkflowsForEntity(String entityApiName);

  /** 无跨实体目录时的空实现。 */
  static MetadataImpactDataAccess empty() {
    return new MetadataImpactDataAccess() {
      @Override
      public List<EntityMetadata> findAllEntities() {
        return Collections.emptyList();
      }

      @Override
      public List<WorkflowRef> findWorkflowsForEntity(String entityApiName) {
        return Collections.emptyList();
      }
    };
  }
}
