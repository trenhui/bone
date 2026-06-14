package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaFieldCommand;
import com.bone.metadata.catalog.common.CatalogTenantSupport;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.model.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateMetaFieldHandler {

  private final MetaFieldRepository metaFieldRepository;
  private final MetaEntityRepository metaEntityRepository;

  @Transactional
  public Long handle(CreateMetaFieldCommand cmd) {
    // 兼容前端传 fieldType 而非 type 的情况
    if (cmd.getType() == null && cmd.getFieldType() != null) {
      cmd.setType(cmd.getFieldType());
    }
    MetaEntity entity = metaEntityRepository.findById(cmd.getEntityId());
    if (entity == null) {
      throw BizException.of("所属实体不存在: " + cmd.getEntityId());
    }
    long dup =
        metaFieldRepository.countByCriteria(
            Criteria.<MetaField>create()
                .eq("entityId", cmd.getEntityId())
                .eq("code", cmd.getCode()));
    if (dup > 0) {
      throw BizException.of("字段编码已存在: " + cmd.getCode());
    }
    Long id = DistributedIdGenerator.generateLongId();
    MetaField field =
        MetaField.create(
            id,
            CatalogTenantSupport.currentTenantId(),
            cmd.getEntityId(),
            cmd.getName(),
            cmd.getCode(),
            cmd.getDisplayName(),
            cmd.getType());
    metaFieldRepository.insert(field);
    return id;
  }
}
