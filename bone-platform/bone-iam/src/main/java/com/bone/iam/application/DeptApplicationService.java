package com.bone.iam.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.iam.application.command.CreateDeptCommand;
import com.bone.iam.application.command.DeleteDeptCommand;
import com.bone.iam.application.command.UpdateDeptCommand;
import com.bone.iam.application.query.dto.DeptTreeDTO;
import com.bone.iam.application.query.qry.DeptTreeQuery;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.dept.Dept;
import com.bone.iam.domain.repository.DeptRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织机构应用层统一门面（Application Service First）——机构类用例的唯一入口。
 *
 * <p>/*
 *
 * <p>原 {@code application.command.handler.*DeptCommandHandler} 与 {@code
 * application.query.handler.DeptTreeQueryHandler} 已全量内联进本类。适配器只依赖本类，HTTP 契约保持不变。
 *
 * <p>/*
 *
 * <p>本类不出现读侧 DSL 与 {@code TenantContext}：取数下沉 {@link DeptRepository#listAll()}，租户取值走 {@link
 * TenantProvider} 端口（E-2 / E-4.2）。
 */
/*
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务管理的聚合（Dept）当前不发布领域事件，其创建/更新/删除均属内部状态迁移、下游无上下文需感知；若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class DeptApplicationService {

  private final DeptRepository deptRepository;
  private final TenantProvider tenantProvider;

  @Transactional
  public Long create(CreateDeptCommand cmd) {
    Long tenantId = resolveTenantId(cmd.getTenantId());
    Dept dept =
        Dept.create(cmd.getName(), cmd.getParentId(), cmd.getOrderNo(), cmd.getStatus(), tenantId);
    deptRepository.save(dept);
    return dept.getId();
  }

  @Transactional
  public Long update(UpdateDeptCommand cmd) {
    Dept dept = deptRepository.findById(cmd.getId());
    if (dept == null) {
      throw IamErrors.of(IamErrorCodes.DEPT_NOT_FOUND, cmd.getId());
    }
    dept.update(cmd.getName(), cmd.getParentId(), cmd.getOrderNo(), cmd.getStatus());
    deptRepository.update(dept);
    return dept.getId();
  }

  @Transactional
  public Long delete(DeleteDeptCommand cmd) {
    if (deptRepository.findById(cmd.getId()) == null) {
      throw IamErrors.of(IamErrorCodes.DEPT_NOT_FOUND, cmd.getId());
    }
    deptRepository.deleteById(cmd.getId());
    return cmd.getId();
  }

  @Transactional(readOnly = true)
  public List<DeptTreeDTO> tree(DeptTreeQuery qry) {
    Long effectiveTenant = resolveTenantFilter(qry.getTenantId());
    List<Dept> scoped =
        deptRepository.listAll().stream()
            .filter(d -> effectiveTenant == null || effectiveTenant.equals(d.getTenantId()))
            .toList();

    List<DeptTreeDTO> dtoList =
        new ArrayList<>(scoped.stream().map(DeptApplicationService::toDto).toList());
    retainDeptKeywordMatches(dtoList, qry.getKeyword());

    Map<Long, List<DeptTreeDTO>> childrenMap =
        dtoList.stream()
            .filter(d -> d.getParentId() != null)
            .collect(Collectors.groupingBy(DeptTreeDTO::getParentId));

    dtoList.forEach(d -> d.setChildren(childrenMap.getOrDefault(d.getId(), new ArrayList<>())));

    return dtoList.stream()
        .filter(d -> d.getParentId() == null)
        .sorted(
            (a, b) ->
                Integer.compare(
                    a.getOrderNo() == null ? 0 : a.getOrderNo(),
                    b.getOrderNo() == null ? 0 : b.getOrderNo()))
        .toList();
  }

  /** keyword 过滤（名称包含命中）：保留命中节点、其祖先链与其子树，保持树结构完整可操作；keyword 为空时原样保留全部节点，无命中时返回空树。 */
  private static void retainDeptKeywordMatches(List<DeptTreeDTO> dtoList, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return;
    }
    String kw = keyword.trim();
    Set<Long> matched =
        dtoList.stream()
            .filter(d -> d.getName() != null && d.getName().contains(kw))
            .map(DeptTreeDTO::getId)
            .collect(Collectors.toSet());
    if (matched.isEmpty()) {
      dtoList.clear();
      return;
    }
    Map<Long, Long> parentOf =
        dtoList.stream()
            .filter(d -> d.getParentId() != null)
            .collect(Collectors.toMap(DeptTreeDTO::getId, DeptTreeDTO::getParentId));
    Map<Long, List<Long>> childrenOf =
        dtoList.stream()
            .filter(d -> d.getParentId() != null)
            .collect(
                Collectors.groupingBy(
                    DeptTreeDTO::getParentId,
                    Collectors.mapping(DeptTreeDTO::getId, Collectors.toList())));
    Set<Long> keep = new HashSet<>();
    for (Long id : matched) {
      keep.add(id);
      Long parent = parentOf.get(id);
      while (parent != null && keep.add(parent)) {
        parent = parentOf.get(parent);
      }
      Deque<Long> stack = new ArrayDeque<>(childrenOf.getOrDefault(id, List.of()));
      while (!stack.isEmpty()) {
        Long child = stack.pop();
        if (keep.add(child)) {
          stack.addAll(childrenOf.getOrDefault(child, List.of()));
        }
      }
    }
    dtoList.removeIf(d -> !keep.contains(d.getId()));
  }

  /**
   * 写侧租户归属解析：租户上下文 &gt; 0 时<b>强制</b>用上下文租户（租户管理员在命令里传其他 tenantId 视为越权企图，直接忽略，详设
   * §2.6/§2.9）；平台租户（0）/无上下文（平台管理员代操作）才接受命令传入值， 均缺省落平台租户 0。
   */
  private Long resolveTenantId(Long fromCommand) {
    Long fromContext = tenantProvider.currentTenantIdOrNull();
    if (fromContext != null && fromContext != 0L) {
      return fromContext;
    }
    if (fromCommand != null) {
      return fromCommand;
    }
    return fromContext != null ? fromContext : 0L;
  }

  /** 非平台租户（&gt; 0）强制按其过滤；平台租户（0）/无上下文回退到查询参数（详设 §3.4 / §4.8）。 */
  private Long resolveTenantFilter(Long fromQuery) {
    Long fromContext = tenantProvider.currentTenantIdOrNull();
    if (fromContext != null && fromContext != 0L) {
      return fromContext;
    }
    return fromQuery;
  }

  private static DeptTreeDTO toDto(Dept d) {
    DeptTreeDTO dto = new DeptTreeDTO();
    dto.setId(d.getId());
    dto.setName(d.getName());
    dto.setParentId(d.getParentId());
    dto.setOrderNo(d.getOrderNo());
    dto.setStatus(d.getStatus());
    dto.setChildren(new ArrayList<>());
    return dto;
  }
}
