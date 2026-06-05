package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.common.CatalogVersionSupport;
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
    return entity.getVersion();
  }
}
