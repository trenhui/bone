package com.bone.metadata.engine.ports.spi;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.OperationMetadata;
import com.bone.metadata.engine.domain.metadata.WorkflowMetadata;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 元数据仓储端口（读侧契约）。
 *
 * <p>本接口是 {@code bone-metadata-engine} 与持久化层（{@code bone-metadata-sdk} / 已发布 {@code meta_*}
 * 表）之间的防腐层契约。引擎领域层（{@code domain}）仅依赖本端口，不感知 SDK 或 JDBC。
 *
 * <p>设计要点（见 change {@code refactor/metadata-engine-boundary-ddd} design.md §3）：
 *
 * <ul>
 *   <li>仅声明<b>读侧</b>方法；写侧（CRUD/save/事务/导入导出）归 {@code bone-metadata-server} 的 catalog 管理
 *       API，不在此端口暴露。
 *   <li>实现由 {@code engine-runtime} 提供：{@code SdkMetadataRepository} 基于 SDK 查询已发布元数据
 *       并转换为引擎领域模型；{@code InMemoryMetadataRepository} 保留为测试实现。
 * </ul>
 */
public interface MetadataRepositoryPort {

  Optional<EntityMetadata> findEntityById(String id);

  EntityMetadata findEntityByApiName(String apiName);

  List<EntityMetadata> findAllEntities();

  List<EntityMetadata> findEntitiesByDomain(String domain);

  List<EntityMetadata> searchEntities(String query, int offset, int limit);

  boolean existsEntity(String apiName);

  Set<String> findAllDomains();

  List<EntityMetadata> findEntitiesByTag(String tag);

  List<OperationMetadata> findAllOperations();

  Optional<WorkflowMetadata> findWorkflowByApiName(String apiName);
}
