package com.bone.iam.application.query.handler;

import com.bone.core.exception.BizException;
import com.bone.core.security.auth.CurrentAccountResolver;
import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.query.dto.MenuNode;
import com.bone.iam.application.query.qry.MenuCurrentQuery;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.menu.Menu;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MenuCurrentQueryHandler {

  @Transactional(readOnly = true)
  public List<MenuNode> handle(MenuCurrentQuery qry) {
    Long tenantId = qry.getTenantId();
    if (tenantId == null) {
      tenantId = TenantContext.getTenantIdAsLong();
    }
    if (tenantId == null) {
      tenantId = 0L;
    }
    final Long effectiveTenantId = tenantId;

    Set<String> scopes =
        CurrentAccountResolver.currentPrincipal()
            .map(p -> new HashSet<>(p.scopes()))
            .orElseThrow(
                () -> BizException.of(401, IamErrorCodes.PROFILE_OWNERSHIP_DENIED + ": 未登录"));

    List<Menu> all = QueryBuilder.from(Menu.class).list();
    List<Menu> scoped =
        all.stream().filter(m -> effectiveTenantId.equals(m.getTenantId())).toList();

    // 按权限过滤：permission 为空表示所有登录用户可见；否则需 scopes 包含
    List<Menu> visible =
        scoped.stream()
            .filter(
                m ->
                    m.getPermission() == null
                        || m.getPermission().isBlank()
                        || scopes.contains(m.getPermission()))
            .toList();

    List<MenuNode> nodes = visible.stream().map(this::toNode).toList();

    Map<String, List<MenuNode>> childrenMap =
        nodes.stream()
            .filter(n -> n.getParentId() != null)
            .collect(Collectors.groupingBy(MenuNode::getParentId));

    nodes.forEach(n -> n.setChildren(childrenMap.getOrDefault(n.getId(), new ArrayList<>())));

    return nodes.stream()
        .filter(n -> n.getParentId() == null)
        .sorted(
            (a, b) ->
                Integer.compare(
                    a.getOrder() == null ? 0 : a.getOrder(),
                    b.getOrder() == null ? 0 : b.getOrder()))
        .toList();
  }

  private MenuNode toNode(Menu m) {
    MenuNode node = new MenuNode();
    node.setId(String.valueOf(m.getId()));
    node.setParentId(m.getParentId() == null ? null : String.valueOf(m.getParentId()));
    node.setName(m.getName());
    node.setPath(m.getPath());
    node.setIcon(m.getIcon());
    node.setOrder(m.getOrderNo());
    node.setPermission(m.getPermission());
    node.setChildren(new ArrayList<>());
    return node;
  }
}
