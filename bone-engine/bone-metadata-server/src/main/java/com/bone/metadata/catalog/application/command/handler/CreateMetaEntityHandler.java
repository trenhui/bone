package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCmd;
import com.bone.metadata.catalog.common.CatalogTenantSupport;
import com.bone.metadata.catalog.domain.enums.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateMetaEntityHandler {

  private final MetaEntityRepository metaEntityRepository;

  @Transactional
  public Long handle(CreateMetaEntityCmd cmd) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    var existing =
        metaEntityRepository
            .where(MetaEntity::getTenantId)
            .eq(tenantId)
            .and(MetaEntity::getCode)
            .eq(cmd.getCode())
            .list();
    if (!existing.isEmpty()) {
      throw BizException.of("实体编码已存在: " + cmd.getCode());
    }
    Long id = DistributedIdGenerator.generateLongId();
    int type = cmd.getType() != null ? cmd.getType() : 0;
    int deliveryMode =
        cmd.getDeliveryMode() != null
            ? MetaDeliveryMode.fromCode(cmd.getDeliveryMode()).getCode()
            : MetaDeliveryMode.GENERATIVE.getCode();
    MetaEntity entity =
        MetaEntity.create(
            id,
            tenantId,
            cmd.getName(),
            cmd.getCode(),
            cmd.getDisplayName(),
            cmd.getDescription(),
            cmd.getTableName(),
            type,
            deliveryMode,
            cmd.getIcon());
    metaEntityRepository.insert(entity);
    return id;
  }
}
