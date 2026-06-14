package com.bone.masterdata.domain.service.entity;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;

/** 主数据实体领域服务 */
@RequiredArgsConstructor
public class MasterDataEntityService {
  private final MasterDataEntityRepository entityRepository;
  private final MasterDataFieldRepository fieldRepository;

  public MasterDataEntity createEntity(
      Long id, MasterDataEntityName name, String description, String category) {
    long existing =
        entityRepository.countByCriteria(
            Criteria.<MasterDataEntity>create()
                .entityClass(MasterDataEntity.class)
                .eq("name", name));
    if (existing > 0) {
      throw new DomainException("主数据实体名称已存在");
    }
    return MasterDataEntity.create(id, null, name, description, category);
  }

  public void publishEntity(Long entityId) {
    MasterDataEntity entity = entityRepository.findById(entityId);
    if (entity == null) {
      throw new DomainException("主数据实体不存在");
    }
    entity.publish();
    entityRepository.save(entity);
  }

  public int getFieldCount(Long entityId) {
    return fieldRepository
        .countByCriteria(
            Criteria.<MasterDataField>create()
                .entityClass(MasterDataField.class)
                .eq("masterDataEntityId", entityId))
        .intValue();
  }
}
