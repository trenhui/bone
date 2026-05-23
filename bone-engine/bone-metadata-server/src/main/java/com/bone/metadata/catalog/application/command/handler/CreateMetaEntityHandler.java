package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.common.CatalogTenantSupport;
import com.bone.metadata.catalog.domain.enums.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateMetaEntityHandler {

  private final MetaEntityRepository metaEntityRepository;

  @Transactional
  public Long handle(CreateMetaEntityCommand cmd) {
    long tenantId = CatalogTenantSupport.currentTenantId();
    long existing =
        metaEntityRepository.countByCriteria(
            Criteria.<MetaEntity>create().eq("tenantId", tenantId).eq("code", cmd.getCode()));
    if (existing > 0) {
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
