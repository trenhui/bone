package com.bone.metadata.engine.repository;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.OperationMetadata;
import com.bone.metadata.engine.domain.metadata.PackageDefinition;
import com.bone.metadata.engine.domain.metadata.WorkflowMetadata;
import java.util.List;
import java.util.Map; // 添加Map导入
import java.util.Optional;
import java.util.Set;

/** 元数据仓库接口，提供元数据的持久化操作 支持实体元数据、工作流元数据和包定义的CRUD操作 */
public interface MetadataRepository {

  // 实体元数据相关方法

  /**
   * 保存实体元数据
   *
   * @param entityMetadata 实体元数据
   * @return 保存后的实体元数据
   */
  EntityMetadata saveEntity(EntityMetadata entityMetadata);

  /**
   * 批量保存实体元数据
   *
   * @param entityMetadatas 实体元数据列表
   * @return 保存的实体元数据数量
   */
  int saveEntities(List<EntityMetadata> entityMetadatas);

  /**
   * 根据API名称获取实体元数据
   *
   * @param apiName 实体API名称
   * @return 实体元数据
   */
  EntityMetadata findEntityByApiName(String apiName);

  /**
   * 根据ID获取实体元数据
   *
   * @param id 实体ID
   * @return 实体元数据
   */
  Optional<EntityMetadata> findEntityById(String id);

  /**
   * 根据业务域获取实体元数据列表
   *
   * @param domain 业务域
   * @return 实体元数据列表
   */
  List<EntityMetadata> findEntitiesByDomain(String domain);

  /**
   * 获取所有实体元数据
   *
   * @return 实体元数据列表
   */
  List<EntityMetadata> findAllEntities();

  /**
   * 根据查询条件搜索实体元数据
   *
   * @param query 查询条件
   * @param offset 偏移量
   * @param limit 限制数量
   * @return 实体元数据列表
   */
  List<EntityMetadata> searchEntities(String query, int offset, int limit);

  /**
   * 删除实体元数据
   *
   * @param apiName 实体API名称
   * @return 是否删除成功
   */
  boolean deleteEntity(String apiName);

  /**
   * 检查实体元数据是否存在
   *
   * @param apiName 实体API名称
   * @return 是否存在
   */
  boolean existsEntity(String apiName);

  /**
   * 获取所有业务域
   *
   * @return 业务域列表
   */
  Set<String> findAllDomains();

  /**
   * 根据标签查询实体
   *
   * @param tag 标签名
   * @return 实体元数据列表
   */
  List<EntityMetadata> findEntitiesByTag(String tag);

  /**
   * 获取实体元数据的数量
   *
   * @return 实体数量
   */
  int countEntities();

  // 操作元数据相关方法

  /**
   * 保存操作元数据
   *
   * @param operationMetadata 操作元数据
   * @return 保存后的操作元数据
   */
  OperationMetadata saveOperation(OperationMetadata operationMetadata);

  /**
   * 获取所有操作元数据
   *
   * @return 操作元数据列表
   */
  List<OperationMetadata> findAllOperations();

  /**
   * 根据实体名称获取操作元数据
   *
   * @param entityName 实体名称
   * @return 操作元数据列表
   */
  List<OperationMetadata> findOperationsByEntityName(String entityName);

  /**
   * 根据操作名称获取操作元数据
   *
   * @param operationName 操作名称
   * @return 操作元数据
   */
  OperationMetadata findOperationByName(String operationName);

  /**
   * 删除操作元数据
   *
   * @param operationName 操作名称
   * @return 是否删除成功
   */
  boolean deleteOperation(String operationName);

  /**
   * 检查操作元数据是否存在
   *
   * @param operationName 操作名称
   * @return 是否存在
   */
  boolean existsOperation(String operationName);

  // 工作流元数据相关方法

  /**
   * 保存工作流元数据
   *
   * @param workflowMetadata 工作流元数据
   * @return 保存后的工作流元数据
   */
  WorkflowMetadata saveWorkflow(WorkflowMetadata workflowMetadata);

  /**
   * 根据API名称获取工作流元数据
   *
   * @param apiName 工作流API名称
   * @return 工作流元数据
   */
  Optional<WorkflowMetadata> findWorkflowByApiName(String apiName);

  /**
   * 获取所有工作流元数据
   *
   * @return 工作流元数据列表
   */
  List<WorkflowMetadata> findAllWorkflows();

  /**
   * 删除工作流元数据
   *
   * @param apiName 工作流API名称
   * @return 是否删除成功
   */
  boolean deleteWorkflow(String apiName);

  /**
   * 根据实体API名称获取相关的工作流
   *
   * @param entityApiName 实体API名称
   * @return 工作流元数据列表
   */
  List<WorkflowMetadata> findWorkflowsByEntityApiName(String entityApiName);

  // 包定义相关方法

  /**
   * 保存包定义
   *
   * @param packageDefinition 包定义
   * @return 保存后的包定义
   */
  PackageDefinition savePackage(PackageDefinition packageDefinition);

  /**
   * 根据名称获取包定义
   *
   * @param name 包名称
   * @return 包定义
   */
  Optional<PackageDefinition> findPackageByName(String name);

  /**
   * 获取所有包定义
   *
   * @return 包定义列表
   */
  List<PackageDefinition> findAllPackages();

  /**
   * 删除包定义
   *
   * @param name 包名称
   * @return 是否删除成功
   */
  boolean deletePackage(String name);

  // 事务相关方法

  /** 开始事务 */
  void beginTransaction();

  /** 提交事务 */
  void commitTransaction();

  /** 回滚事务 */
  void rollbackTransaction();

  // 批量操作

  /**
   * 批量删除实体
   *
   * @param apiNames 实体API名称列表
   * @return 删除的数量
   */
  int deleteEntities(List<String> apiNames);

  /**
   * 批量删除工作流
   *
   * @param apiNames 工作流API名称列表
   * @return 删除的数量
   */
  int deleteWorkflows(List<String> apiNames);

  // 高级查询

  /**
   * 根据多个条件查询实体
   *
   * @param domain 业务域
   * @param tags 标签列表
   * @param searchTerm 搜索词
   * @return 实体元数据列表
   */
  List<EntityMetadata> findEntitiesByCriteria(String domain, List<String> tags, String searchTerm);

  /**
   * 导出元数据
   *
   * @param format 导出格式 (JSON, XML, YAML)
   * @return 导出的元数据内容
   */
  String exportMetadata(String format);

  /**
   * 导入元数据
   *
   * @param content 元数据内容
   * @param format 导入格式 (JSON, XML, YAML)
   * @return 导入的元数据数量
   */
  int importMetadata(String content, String format);

  /** 刷新缓存 */
  void refresh();

  /** 清理缓存 */
  void clearCache();

  /**
   * 获取元数据统计信息
   *
   * @return 统计信息
   */
  MetadataStatistics getStatistics();

  /** 元数据统计信息 */
  interface MetadataStatistics {
    long getEntityCount();

    long getWorkflowCount();

    long getPackageCount();

    long getFieldCount();

    Map<String, Long> getEntitiesByDomain();

    Map<String, Long> getEntitiesByTag();
  }
}
