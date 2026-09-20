package com.bone.iam.application.app.query.handler;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.iam.application.app.query.dto.ApplicationDTO;
import com.bone.iam.application.app.query.qry.ApplicationPageQuery;
import com.bone.iam.domain.app.BoneApplication;
import com.bone.iam.domain.repository.BoneApplicationRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用分页 / 详情查询处理器。
 *
 * <p><b>E-4.2</b>：本类不持有任何读侧 DSL——关键字与分页条件经 {@link BoneApplicationRepository#findPage}
 * 这一「本聚合读」通道下发（ADR-0030），DSL 只存在于 {@code ..domain.repository..}。
 */
@Component
@RequiredArgsConstructor
public class ApplicationPageQueryHandler {

  private final BoneApplicationRepository boneApplicationRepository;

  @Transactional(readOnly = true)
  public PageResult<ApplicationDTO> handle(ApplicationPageQuery qry) {
    PageResult<BoneApplication> result =
        boneApplicationRepository.findPage(
            qry.getKeyword(),
            qry.getStatus(),
            qry.getPage() != null ? qry.getPage() : 1,
            qry.getSize() != null ? qry.getSize() : 10);
    List<ApplicationDTO> list =
        result.getRecords().stream().map(this::toDto).collect(Collectors.toList());
    return PageResult.of(list, result.getTotal(), result.getPage(), result.getSize());
  }

  public ApplicationDTO handle(Long id) {
    BoneApplication entity = boneApplicationRepository.findById(id);
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
