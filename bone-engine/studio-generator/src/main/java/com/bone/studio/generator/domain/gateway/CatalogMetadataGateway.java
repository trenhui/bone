package com.bone.studio.generator.domain.gateway;

import com.bone.studio.generator.domain.data.DatabaseTable;
import java.util.List;

/** 从 meta_* 目录读取已发布实体快照，转换为生成器内部表模型 */
public interface CatalogMetadataGateway {

  /**
   * @param tenantId 租户，null 时默认 1
   * @param entityCodes 实体编码过滤，空则全部已发布实体
   */
  List<DatabaseTable> loadPublishedSnapshots(Long tenantId, List<String> entityCodes);
}
