package com.bone.iam.application.query.handler;

import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.domain.permission.Permission;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PermissionTreeQueryHandler {

  @Transactional(readOnly = true)
  public List<PermissionDTO> handle() {
    List<Permission> allPermissions = QueryBuilder.from(Permission.class).list();

    List<PermissionDTO> dtoList = allPermissions.stream().map(this::toDto).toList();

    Map<Long, List<PermissionDTO>> childrenMap =
        dtoList.stream()
            .filter(d -> d.getParentId() != null)
            .collect(Collectors.groupingBy(PermissionDTO::getParentId));

    dtoList.forEach(d -> d.setChildren(childrenMap.getOrDefault(d.getId(), new ArrayList<>())));

    return dtoList.stream()
        .filter(d -> d.getParentId() == null)
        .sorted(
            (a, b) ->
                Integer.compare(
                    a.getSortOrder() == null ? 0 : a.getSortOrder(),
                    b.getSortOrder() == null ? 0 : b.getSortOrder()))
        .toList();
  }

  private PermissionDTO toDto(Permission p) {
    PermissionDTO dto = new PermissionDTO();
    dto.setId(p.getId());
    dto.setCode(p.getCode());
    dto.setName(p.getName());
    dto.setResourceType(p.getResourceType());
    dto.setResourcePath(p.getResourcePath());
    dto.setAction(p.getAction());
    dto.setParentId(p.getParentId());
    dto.setSortOrder(p.getSortOrder());
    dto.setChildren(new ArrayList<>());
    return dto;
  }
}
