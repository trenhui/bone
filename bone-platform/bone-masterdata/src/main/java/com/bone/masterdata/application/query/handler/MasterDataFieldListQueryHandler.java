package com.bone.masterdata.application.query.handler;

import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.qry.MasterDataFieldListQuery;
import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MasterDataFieldListQueryHandler {

  @Transactional(readOnly = true)
  public List<MasterDataFieldDTO> handle(MasterDataFieldListQuery qry) {
    FluentQuery<MasterDataField> query = QueryBuilder.from(MasterDataField.class);

    if (qry.getMasterDataEntityId() != null) {
      query.where(MasterDataField::getMasterDataEntityId).eq(qry.getMasterDataEntityId());
    }

    return query.list().stream().map(this::toDto).collect(Collectors.toList());
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
