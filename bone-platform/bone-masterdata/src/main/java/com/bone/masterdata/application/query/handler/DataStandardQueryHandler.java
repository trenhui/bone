package com.bone.masterdata.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.masterdata.application.query.dto.DataStandardDTO;
import com.bone.masterdata.application.query.qry.DataStandardPageQuery;
import com.bone.masterdata.domain.standard.DataStandard;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataStandardQueryHandler {

  public DataStandardDTO getById(Long id) {
    DataStandard standard =
        QueryBuilder.from(DataStandard.class).where(DataStandard::getId).eq(id).single();
    return standard != null ? toDTO(standard) : null;
  }

  public List<DataStandardDTO> listByEntity(String entityCode) {
    return QueryBuilder.from(DataStandard.class)
        .where(DataStandard::getEntityCode)
        .eq(entityCode)
        .list()
        .stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  public PageResult<DataStandardDTO> page(DataStandardPageQuery qry) {
    FluentQuery<DataStandard> query = QueryBuilder.from(DataStandard.class);
    if (qry.getEntityCode() != null && !qry.getEntityCode().isBlank()) {
      query.where(DataStandard::getEntityCode).eq(qry.getEntityCode());
    }
    if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
      query.where(DataStandard::getFieldCode).like(qry.getKeyword());
    }
    com.bone.core.model.PageResult<DataStandard> result =
        query.page(qry.getPageNum(), qry.getPageSize());
    List<DataStandardDTO> dtoList =
        result.getRecords().stream().map(this::toDTO).collect(Collectors.toList());
    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  private DataStandardDTO toDTO(DataStandard standard) {
    return DataStandardDTO.builder()
        .id(standard.getId())
        .entityCode(standard.getEntityCode())
        .fieldCode(standard.getFieldCode().value())
        .ruleType(standard.getRuleType().getCode())
        .pattern(standard.getPattern())
        .refCode(standard.getRefCode())
        .description(standard.getDescription())
        .createdAt(standard.getCreatedAt())
        .updatedAt(standard.getUpdatedAt())
        .build();
  }
}
