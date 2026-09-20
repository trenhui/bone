package com.bone.iam.application;

import com.bone.core.exception.NotFoundException;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.CreatePermissionCommand;
import com.bone.iam.application.command.cmd.UpdatePermissionCommand;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.qry.PermissionPageQuery;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.permission.vo.PermissionType;
import com.bone.iam.domain.repository.PermissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限应用层统一门面（Application Service First）——权限类用例的唯一入口。
 *
 * <p>原 {@code application.command.handler.*PermissionCommandHandler} 与 {@code
 * application.query.handler.PermissionTreeQueryHandler / PermissionDetailQueryHandler /
 * PermissionPageQueryHandler} 已全量内联进本类。适配器只依赖本类，HTTP 契约保持不变。
 *
 * <p>本类不出现读侧 DSL：树 / 详情 / 分页的取数全部下沉 {@link PermissionRepository} 的 {@code default} 方法（E-4.2）。
 */
@Service
@RequiredArgsConstructor
public class PermissionApplicationService {

  private final PermissionRepository permissionRepository;

  @Transactional
  public Long create(CreatePermissionCommand cmd) {
    String resourceType = cmd.getResourceType();
    if (resourceType == null || resourceType.isBlank()) {
      resourceType = "API";
    }
    String resourcePath = cmd.getResourcePath();
    if (resourcePath == null || resourcePath.isBlank()) {
      resourcePath = cmd.getCode() != null ? "/" + cmd.getCode() : "/";
    }
    String action = cmd.getAction();
    if (action == null || action.isBlank()) {
      action = "ALL";
    }
    PermissionType type = cmd.getType();
    if (type == null) {
      type = PermissionType.OPERATION;
    }
    Permission permission =
        Permission.create(
            cmd.getCode(),
            cmd.getName(),
            cmd.getDescription(),
            resourceType,
            resourcePath,
            action,
            cmd.getParentId(),
            type,
            cmd.getSortOrder());
    permissionRepository.save(permission);
    return permission.getId();
  }

  @Transactional
  public void update(UpdatePermissionCommand cmd) {
    Permission permission = permissionRepository.findById(cmd.getId());
    if (permission == null) {
      throw new NotFoundException("权限不存在");
    }
    permission.update(
        cmd.getName(),
        cmd.getDescription(),
        cmd.getResourceType(),
        cmd.getResourcePath(),
        cmd.getAction(),
        cmd.getParentId(),
        cmd.getType(),
        cmd.getSortOrder() == null ? 0 : cmd.getSortOrder());
    permissionRepository.update(permission);
  }

  @Transactional
  public void delete(Long id) {
    permissionRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<PermissionDTO> tree() {
    List<PermissionDTO> dtoList =
        permissionRepository.listAll().stream()
            .map(PermissionApplicationService::toTreeDto)
            .toList();

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

  @Transactional(readOnly = true)
  public PermissionDTO detail(Long id) {
    Permission permission =
        permissionRepository
            .findPermissionById(id)
            .orElseThrow(() -> NotFoundException.of("权限不存在"));
    PermissionDTO dto = new PermissionDTO();
    dto.setId(permission.getId());
    dto.setCode(permission.getCode());
    dto.setName(permission.getName());
    dto.setResourceType(permission.getResourceType());
    dto.setResourcePath(permission.getResourcePath());
    dto.setAction(permission.getAction());
    return dto;
  }

  @Transactional(readOnly = true)
  public PageResult<PermissionDTO> page(PermissionPageQuery qry) {
    PageResult<Permission> result =
        permissionRepository.findPermissionPage(
            qry.getKeyword(), qry.getType(), qry.getParentId(), qry.getPage(), qry.getSize());
    List<PermissionDTO> dtoList =
        result.getRecords().stream().map(PermissionApplicationService::toDetailDto).toList();
    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  private static PermissionDTO toTreeDto(Permission p) {
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

  private static PermissionDTO toDetailDto(Permission permission) {
    PermissionDTO dto = new PermissionDTO();
    dto.setId(permission.getId());
    dto.setCode(permission.getCode());
    dto.setName(permission.getName());
    dto.setDescription(permission.getDescription());
    dto.setParentId(permission.getParentId());
    dto.setType(permission.getType());
    dto.setCreatedAt(permission.getCreatedAt());
    dto.setUpdatedAt(permission.getUpdatedAt());
    return dto;
  }
}
