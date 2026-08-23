package com.bone.iam.application.query.handler;

import static com.bone.iam.application.query.handler.AccountPageQueryHandler.resolveTenantFilter;

import com.bone.iam.application.query.dto.MenuTreeDTO;
import com.bone.iam.application.query.qry.MenuTreeQuery;
import com.bone.iam.domain.menu.Menu;
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
public class MenuTreeQueryHandler {

  @Transactional(readOnly = true)
  public List<MenuTreeDTO> handle(MenuTreeQuery qry) {
    List<Menu> all = QueryBuilder.from(Menu.class).list();

    Long effectiveTenant = resolveTenantFilter(qry.getTenantId());
    List<Menu> scoped =
        effectiveTenant == null
            ? all
            : all.stream().filter(m -> effectiveTenant.equals(m.getTenantId())).toList();

    List<MenuTreeDTO> dtoList = scoped.stream().map(this::toDto).toList();

    Map<Long, List<MenuTreeDTO>> childrenMap =
        dtoList.stream()
            .filter(d -> d.getParentId() != null)
            .collect(Collectors.groupingBy(MenuTreeDTO::getParentId));

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

  private MenuTreeDTO toDto(Menu m) {
    MenuTreeDTO dto = new MenuTreeDTO();
    dto.setId(m.getId());
    dto.setName(m.getName());
    dto.setParentId(m.getParentId());
    dto.setPath(m.getPath());
    dto.setIcon(m.getIcon());
    dto.setOrderNo(m.getOrderNo());
    dto.setPermission(m.getPermission());
    dto.setType(m.getType());
    dto.setChildren(new ArrayList<>());
    return dto;
  }
}
