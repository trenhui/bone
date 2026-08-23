package com.bone.iam.application.app.query.handler;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.iam.application.app.query.dto.ModuleDTO;
import com.bone.iam.application.app.query.qry.ModuleListQuery;
import com.bone.iam.domain.app.BoneModule;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ModuleListQueryHandler {

  @Transactional(readOnly = true)
  public PageResult<ModuleDTO> handle(ModuleListQuery qry) {
    var query = QueryBuilder.from(BoneModule.class);
    if (qry.getAppId() != null) {
      query.where(BoneModule::getAppId).eq(qry.getAppId());
    }
    var result =
        query
            .orderByAsc(BoneModule::getSortOrder)
            .page(
                qry.getPage() != null ? qry.getPage() : 1,
                qry.getSize() != null ? qry.getSize() : 100);
    List<ModuleDTO> list =
        result.getRecords().stream().map(this::toDto).collect(Collectors.toList());
    return PageResult.of(list, result.getTotal(), result.getPage(), result.getSize());
  }

  public ModuleDTO handle(Long id) {
    BoneModule entity =
        QueryBuilder.from(BoneModule.class).where(BoneModule::getId).eq(id).single();
    if (entity == null) throw new BizException(404, "模块不存在");
    return toDto(entity);
  }

  private ModuleDTO toDto(BoneModule entity) {
    ModuleDTO dto = new ModuleDTO();
    dto.setId(entity.getId());
    dto.setAppId(entity.getAppId());
    dto.setName(entity.getName());
    dto.setCode(entity.getCode());
    dto.setDescription(entity.getDescription());
    dto.setStatus(entity.getStatus());
    dto.setEntityCount(0);
    dto.setFieldCount(0);
    dto.setSortOrder(entity.getSortOrder());
    dto.setCreatedAt(entity.getCreatedAt());
    return dto;
  }
}
