package com.bone.metadata.catalog.application.query.mapper;

import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.dto.MetaFieldDTO;
import com.bone.metadata.catalog.application.query.dto.MetaRelationDTO;
import com.bone.metadata.catalog.domain.enums.MetaEntityStatus;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.model.MetaEntityRelation;
import com.bone.metadata.catalog.domain.model.MetaField;

public final class CatalogDtoMapper {

  private CatalogDtoMapper() {}

  public static MetaEntityDTO toDto(MetaEntity e) {
    MetaEntityDTO dto = new MetaEntityDTO();
    dto.setId(e.getId());
    dto.setName(e.getName());
    dto.setCode(e.getCode());
    dto.setDisplayName(e.getDisplayName());
    dto.setDescription(e.getDescription());
    dto.setTableName(e.getTableName());
    dto.setType(e.getType());
    dto.setDeliveryMode(e.getDeliveryMode());
    dto.setDeliveryModeLabel(e.deliveryModeEnum().name());
    dto.setStatus(e.getStatus());
    dto.setStatusLabel(MetaEntityStatus.fromCode(e.getStatus()).name());
    dto.setSortOrder(e.getSortOrder());
    dto.setIcon(e.getIcon());
    dto.setVersion(e.getVersion());
    return dto;
  }

  public static MetaFieldDTO toDto(MetaField f) {
    MetaFieldDTO dto = new MetaFieldDTO();
    dto.setId(f.getId());
    dto.setEntityId(f.getEntityId());
    dto.setName(f.getName());
    dto.setCode(f.getCode());
    dto.setDisplayName(f.getDisplayName());
    dto.setType(f.getType());
    dto.setLength(f.getLength());
    dto.setRequired(f.getRequired());
    dto.setUnique(f.getUnique());
    dto.setSortOrder(f.getSortOrder());
    dto.setComment(f.getComment());
    dto.setCreatedAt(f.getCreatedAt());
    dto.setVersion(f.getVersion());
    return dto;
  }

  public static MetaRelationDTO toDto(MetaEntityRelation r) {
    MetaRelationDTO dto = new MetaRelationDTO();
    dto.setId(r.getId());
    dto.setName(r.getName());
    dto.setSourceEntityId(r.getSourceEntityId());
    dto.setTargetEntityId(r.getTargetEntityId());
    dto.setType(r.getType());
    dto.setSourceFieldId(r.getSourceFieldId());
    dto.setTargetFieldId(r.getTargetFieldId());
    dto.setForeignKeyField(r.getForeignKeyField());
    dto.setRequired(r.getRequired());
    dto.setCascadeType(r.getCascadeType());
    dto.setVersion(r.getVersion());
    return dto;
  }
}
