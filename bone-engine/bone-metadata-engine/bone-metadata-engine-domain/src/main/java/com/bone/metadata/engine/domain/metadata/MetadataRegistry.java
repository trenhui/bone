package com.bone.metadata.engine.domain.metadata;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 元数据注册表接口 提供统一的元数据管理功能，支持实体元数据的注册、获取、更新和删除 */
public interface MetadataRegistry {

  /**
   * 注册实体元数据
   *
   * @param entityMetadata 实体元数据
   * @return 是否已注册成功
   */
  boolean registerEntity(EntityMetadata entityMetadata);

  /**
   * 批量注册实体元数据
   *
   * @param entityMetadataList 实体元数据列表
   * @return 注册成功的数量
   */
  int registerEntities(List<EntityMetadata> entityMetadataList);

  /**
   * 更新实体元数据
   *
   * @param entityMetadata 实体元数据
   * @return 是否已更新成功
   */
  boolean updateEntity(EntityMetadata entityMetadata);

  /**
   * 注销实体元数据
   *
   * @param entityApiName 实体API名称
   * @return 是否已注销成功
   */
  boolean unregisterEntity(String entityApiName);

  /**
   * 获取实体元数据
   *
   * @param entityApiName 实体API名称
   * @return 实体元数据，如果不存在则返回null
   */
  EntityMetadata getEntityMetadata(String entityApiName);

  /**
   * 获取实体元数据（Optional）
   *
   * @param entityApiName 实体API名称
   * @return 包含实体元数据的Optional对象
   */
  Optional<EntityMetadata> getEntityMetadataOptional(String entityApiName);

  /**
   * 检查实体是否已注册
   *
   * @param entityApiName 实体API名称
   * @return 是否已注册
   */
  boolean isEntityRegistered(String entityApiName);

  /**
   * 获取所有实体元数据
   *
   * @return 实体元数据集合
   */
  Collection<EntityMetadata> getAllEntityMetadata();

  /**
   * 获取所有实体API名称
   *
   * @return 实体API名称集合
   */
  Set<String> getAllEntityApiNames();

  /**
   * 根据实体类型获取实体元数据
   *
   * @param entityType 实体类型
   * @return 实体元数据列表
   */
  List<EntityMetadata> getEntityMetadataByType(String entityType);

  /**
   * 根据领域获取实体元数据
   *
   * @param domain 领域名称
   * @return 实体元数据列表
   */
  List<EntityMetadata> getEntityMetadataByDomain(String domain);

  /**
   * 根据标签获取实体元数据
   *
   * @param tag 标签名称
   * @return 实体元数据列表
   */
  List<EntityMetadata> getEntityMetadataByTag(String tag);

  /**
   * 搜索实体元数据
   *
   * @param query 搜索关键字
   * @return 匹配的实体元数据列表
   */
  List<EntityMetadata> searchEntityMetadata(String query);

  /** 清空注册表 */
  void clear();

  /**
   * 获取注册表中的实体数量
   *
   * @return 实体数量
   */
  int size();

  /**
   * 检查注册表是否为空
   *
   * @return 是否为空
   */
  boolean isEmpty();

  /** 刷新注册表缓存 */
  void refreshCache();

  /**
   * 导出注册表数据
   *
   * @return 可序列化的注册表数据
   */
  byte[] exportRegistry();

  /**
   * 导入注册表数据
   *
   * @param registryData 注册表数据
   * @return 导入成功的实体数量
   */
  int importRegistry(byte[] registryData);

  /**
   * 获取实体的字段元数据
   *
   * @param entityApiName 实体API名称
   * @param fieldApiName 字段API名称
   * @return 字段元数据，如果不存在则返回null
   */
  SmartFieldMetadata getFieldMetadata(String entityApiName, String fieldApiName);

  /**
   * 获取实体的所有字段元数据
   *
   * @param entityApiName 实体API名称
   * @return 字段元数据列表
   */
  List<SmartFieldMetadata> getAllFieldMetadata(String entityApiName);

  /**
   * 获取实体的计算字段元数据
   *
   * @param entityApiName 实体API名称
   * @return 计算字段元数据列表
   */
  List<SmartFieldMetadata> getCalculatedFieldMetadata(String entityApiName);

  /**
   * 获取实体的虚拟字段元数据
   *
   * @param entityApiName 实体API名称
   * @return 虚拟字段元数据列表
   */
  List<SmartFieldMetadata> getVirtualFieldMetadata(String entityApiName);

  /**
   * 获取实体的关系元数据
   *
   * @param entityApiName 实体API名称
   * @param relationshipName 关系名称
   * @return 关系元数据，如果不存在则返回null
   */
  RelationshipMetadata getRelationshipMetadata(String entityApiName, String relationshipName);

  /**
   * 获取实体的所有关系元数据
   *
   * @param entityApiName 实体API名称
   * @return 关系元数据列表
   */
  List<RelationshipMetadata> getAllRelationshipMetadata(String entityApiName);

  /**
   * 获取实体的业务规则元数据
   *
   * @param entityApiName 实体API名称
   * @return 业务规则元数据列表
   */
  List<BusinessRuleMetadata> getBusinessRuleMetadata(String entityApiName);

  /**
   * 添加元数据变更监听器
   *
   * @param listener 监听器
   */
  void addMetadataChangeListener(MetadataChangeListener listener);

  /**
   * 移除元数据变更监听器
   *
   * @param listener 监听器
   */
  void removeMetadataChangeListener(MetadataChangeListener listener);

  /** 元数据变更监听器接口 */
  interface MetadataChangeListener {

    /**
     * 当实体元数据注册时触发
     *
     * @param entityMetadata 实体元数据
     */
    void onEntityRegistered(EntityMetadata entityMetadata);

    /**
     * 当实体元数据更新时触发
     *
     * @param entityMetadata 实体元数据
     */
    void onEntityUpdated(EntityMetadata entityMetadata);

    /**
     * 当实体元数据注销时触发
     *
     * @param entityApiName 实体API名称
     */
    void onEntityUnregistered(String entityApiName);

    /**
     * 当字段元数据更新时触发
     *
     * @param entityApiName 实体API名称
     * @param fieldMetadata 字段元数据
     */
    void onFieldUpdated(String entityApiName, SmartFieldMetadata fieldMetadata);

    /**
     * 当关系元数据更新时触发
     *
     * @param entityApiName 实体API名称
     * @param relationshipMetadata 关系元数据
     */
    void onRelationshipUpdated(String entityApiName, RelationshipMetadata relationshipMetadata);
  }
}
