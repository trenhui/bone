package com.bone.iam.application.app.query.handler;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.iam.application.app.query.dto.ApplicationDTO;
import com.bone.iam.application.app.query.qry.ApplicationPageQuery;
import com.bone.iam.domain.app.BoneApplication;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ApplicationPageQueryHandler {

  @Transactional(readOnly = true)
  public PageResult<ApplicationDTO> handle(ApplicationPageQuery qry) {
    var query = QueryBuilder.from(BoneApplication.class);
    if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
      query.where(
          w -> {
            w.and(BoneApplication::getName).like("%" + qry.getKeyword() + "%");
            w.or(BoneApplication::getCode).like("%" + qry.getKeyword() + "%");
          });
    }
    if (qry.getStatus() != null) {
      query.where(BoneApplication::getStatus).eq(qry.getStatus());
    }
    var result =
        query
            .orderByDesc(BoneApplication::getCreatedAt)
            .page(
                qry.getPage() != null ? qry.getPage() : 1,
                qry.getSize() != null ? qry.getSize() : 10);
    List<ApplicationDTO> list =
        result.getRecords().stream().map(this::toDto).collect(Collectors.toList());
    return PageResult.of(list, result.getTotal(), result.getPage(), result.getSize());
  }

  public ApplicationDTO handle(Long id) {
    BoneApplication entity =
        QueryBuilder.from(BoneApplication.class).where(BoneApplication::getId).eq(id).single();
    if (entity == null) throw new BizException(404, "应用不存在");
    return toDto(entity);
  }

  private ApplicationDTO toDto(BoneApplication entity) {
    ApplicationDTO dto = new ApplicationDTO();
    dto.setId(entity.getId());
    dto.setName(entity.getName());
    dto.setCode(entity.getCode());
    dto.setDescription(entity.getDescription());
    dto.setIcon(entity.getIcon());
    dto.setStatus(entity.getStatus());
    dto.setModuleCount(0);
    dto.setEntityCount(0);
    dto.setMyRole(null);
    dto.setCreatedAt(entity.getCreatedAt());
    dto.setUpdatedAt(entity.getUpdatedAt());
    return dto;
  }
}
