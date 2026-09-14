package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.common.CatalogVersionSupport;
import com.bone.metadata.catalog.domain.enums.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.runtime.RuntimeEntityCacheEvictor;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PublishMetaEntityHandler {

  private final MetaEntityRepository metaEntityRepository;
  private final Optional<RuntimeEntityCacheEvictor> runtimeEntityCacheEvictor;
  private final PhysicalStructureGateway physicalStructureGateway;

  @Transactional
  public Integer handle(Long id, Integer expectedVersion) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw BizException.of("实体不存在: " + id);
    }
    CatalogVersionSupport.assertExpected(expectedVersion, entity.getVersion());
    entity.publish();
    metaEntityRepository.update(entity);
    runtimeEntityCacheEvictor.ifPresent(
        evictor -> evictor.evict(entity.getCode(), entity.getTenantId()));
    // MVP-11：已发布 RUNTIME 实体在发布时对齐物理表结构（建表/加列，幂等非破坏 DDL）
    if (MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      physicalStructureGateway.align(entity.getTenantId(), entity.getCode());
    }
    return entity.getVersion();
  }
}
