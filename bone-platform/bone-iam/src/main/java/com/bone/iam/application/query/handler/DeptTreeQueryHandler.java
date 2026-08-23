package com.bone.iam.application.query.handler;

import static com.bone.iam.application.query.handler.AccountPageQueryHandler.resolveTenantFilter;

import com.bone.iam.application.query.dto.DeptTreeDTO;
import com.bone.iam.application.query.qry.DeptTreeQuery;
import com.bone.iam.domain.dept.Dept;
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
public class DeptTreeQueryHandler {

  @Transactional(readOnly = true)
  public List<DeptTreeDTO> handle(DeptTreeQuery qry) {
    List<Dept> all = QueryBuilder.from(Dept.class).list();

    Long effectiveTenant = resolveTenantFilter(qry.getTenantId());
    List<Dept> scoped =
        effectiveTenant == null
            ? all
            : all.stream().filter(d -> effectiveTenant.equals(d.getTenantId())).toList();

    List<DeptTreeDTO> dtoList = scoped.stream().map(this::toDto).toList();

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

  private DeptTreeDTO toDto(Dept d) {
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
