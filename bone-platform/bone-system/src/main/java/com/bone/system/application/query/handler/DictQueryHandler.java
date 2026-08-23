package com.bone.system.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.query.dto.DictDTO;
import com.bone.system.application.query.qry.DictByTypeQuery;
import com.bone.system.application.query.qry.DictPageQuery;
import com.bone.system.domain.dict.SysDict;
import com.bone.system.domain.dict.vo.DictType;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictQueryHandler {
  private final com.bone.system.domain.repository.SysDictRepository sysDictRepository;

  public List<DictDTO> listByType(DictByTypeQuery qry) {
    return QueryBuilder.from(SysDict.class)
        .where(SysDict::getType)
        .eq(DictType.of(qry.getType()))
        .list()
        .stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  public DictDTO getByTypeAndCode(String type, String code) {
    SysDict dict =
        QueryBuilder.from(SysDict.class)
            .where(SysDict::getType)
            .eq(DictType.of(type))
            .and(SysDict::getCode)
            .eq(code)
            .single();
    return dict != null ? toDTO(dict) : null;
  }

  public PageResult<DictDTO> page(DictPageQuery qry) {
    FluentQuery<SysDict> query = QueryBuilder.from(SysDict.class);
    if (qry.getType() != null && !qry.getType().isBlank()) {
      query.where(SysDict::getType).eq(DictType.of(qry.getType()));
    }
    if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
      query
          .where(SysDict::getLabel)
          .like(qry.getKeyword())
          .or(SysDict::getCode)
          .like(qry.getKeyword());
    }
    com.bone.core.model.PageResult<SysDict> result =
        query.page(qry.getPageNum(), qry.getPageSize());
    List<DictDTO> dtoList =
        result.getRecords().stream().map(this::toDTO).collect(Collectors.toList());
    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  private DictDTO toDTO(SysDict dict) {
    return DictDTO.builder()
        .id(dict.getId())
        .type(dict.getType().value())
        .typeName(dict.getTypeName())
        .code(dict.getCode())
        .label(dict.getLabel())
        .value(dict.getValue())
        .sort(dict.getSort())
        .status(dict.getStatus())
        .createdAt(dict.getCreatedAt())
        .updatedAt(dict.getUpdatedAt())
        .build();
  }
}
