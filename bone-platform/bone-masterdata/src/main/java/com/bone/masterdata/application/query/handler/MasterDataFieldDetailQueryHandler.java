package com.bone.masterdata.application.query.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.qry.MasterDataFieldDetailQuery;
import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MasterDataFieldDetailQueryHandler {

  private final MasterDataFieldRepository masterDataFieldRepository;

  @Transactional(readOnly = true)
  public MasterDataFieldDTO handle(MasterDataFieldDetailQuery qry) {
    MasterDataField field = masterDataFieldRepository.findById(qry.id());
    if (field == null) {
      throw NotFoundException.of("主数据字段不存在");
    }
    return toDto(field);
  }

  private MasterDataFieldDTO toDto(MasterDataField field) {
    return MasterDataFieldDTO.builder()
        .id(field.getId())
        .masterDataEntityId(field.getMasterDataEntityId())
        .name(field.getName().value())
        .code(field.getCode().value())
        .type(field.getType())
        .length(field.getLength())
        .required(field.getRequired())
        .defaultValue(field.getDefaultValue())
        .description(field.getDescription())
        .sortOrder(field.getSortOrder())
        .build();
  }
}
