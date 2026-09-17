package com.bone.masterdata.domain.service.entity;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import lombok.RequiredArgsConstructor;

/** 主数据实体领域服务 */
@RequiredArgsConstructor
public class MasterDataEntityService {
  private final MasterDataEntityRepository entityRepository;

  public void publishEntity(Long entityId) {
    MasterDataEntity entity = entityRepository.findById(entityId);
    if (entity == null) {
      throw new DomainException("主数据实体不存在");
    }
    entity.publish();
    entityRepository.save(entity);
  }
}
