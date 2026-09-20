package com.bone.iam.application;

import com.bone.core.security.auth.CurrentAccountResolver;
import com.bone.iam.application.command.cmd.CreateMenuCommand;
import com.bone.iam.application.command.cmd.DeleteMenuCommand;
import com.bone.iam.application.command.cmd.UpdateMenuCommand;
import com.bone.iam.application.query.dto.MenuNode;
import com.bone.iam.application.query.dto.MenuTreeDTO;
import com.bone.iam.application.query.qry.MenuCurrentQuery;
import com.bone.iam.application.query.qry.MenuTreeQuery;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.menu.Menu;
import com.bone.iam.domain.repository.MenuRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 菜单应用层统一门面（Application Service First）——菜单类用例的唯一入口。
 *
 * <p>原 {@code application.command.handler.*MenuCommandHandler} 与 {@code
 * application.query.handler.MenuTreeQueryHandler / MenuCurrentQueryHandler} 已全量内联进本类。适配器只依赖本类，HTTP
 * 契约保持不变。
 *
 * <p>本类不出现读侧 DSL 与 {@code TenantContext}：取数下沉 {@link MenuRepository#listAll()}，租户取值走 {@link
 * TenantProvider} 端口（E-2 / E-4.2）。
 */
@Service
@RequiredArgsConstructor
public class MenuApplicationService {

  private final MenuRepository menuRepository;
  private final TenantProvider tenantProvider;

  @Transactional
  public Long create(CreateMenuCommand cmd) {
    Long tenantId = resolveTenantId(cmd.getTenantId());
    Menu menu =
        Menu.create(
            cmd.getName(),
            cmd.getParentId(),
            cmd.getPath(),
            cmd.getIcon(),
            cmd.getOrderNo(),
            cmd.getPermission(),
            cmd.getType(),
            tenantId);
    menuRepository.save(menu);
    return menu.getId();
  }

  @Transactional
  public Long update(UpdateMenuCommand cmd) {
    Menu menu = menuRepository.findById(cmd.getId());
    if (menu == null) {
      throw new IllegalArgumentException("菜单不存在: " + cmd.getId());
    }
    menu.update(
        cmd.getName(),
        cmd.getParentId(),
        cmd.getPath(),
        cmd.getIcon(),
        cmd.getOrderNo(),
        cmd.getPermission(),
        cmd.getType());
    menuRepository.update(menu);
    return menu.getId();
  }

  @Transactional
  public Long delete(DeleteMenuCommand cmd) {
    if (menuRepository.findById(cmd.getId()) == null) {
      throw new IllegalArgumentException("菜单不存在: " + cmd.getId());
    }
    menuRepository.deleteById(cmd.getId());
    return cmd.getId();
  }

  @Transactional(readOnly = true)
  public List<MenuTreeDTO> tree(MenuTreeQuery qry) {
    Long effectiveTenant = resolveTenantFilter(qry.getTenantId());
    List<Menu> scoped =
        menuRepository.listAll().stream()
            .filter(m -> effectiveTenant == null || effectiveTenant.equals(m.getTenantId()))
            .toList();

    List<MenuTreeDTO> dtoList = scoped.stream().map(MenuApplicationService::toTreeDto).toList();

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

  @Transactional(readOnly = true)
  public List<MenuNode> current(MenuCurrentQuery qry) {
    Long tenantId = resolveTenantId(qry.getTenantId());

    Set<String> scopes =
        CurrentAccountResolver.currentPrincipal()
            .map(p -> new HashSet<>(p.scopes()))
            .orElseThrow(() -> IamErrors.of(IamErrorCodes.PROFILE_OWNERSHIP_DENIED, "未登录"));

    List<Menu> visible =
        menuRepository.listAll().stream()
            .filter(m -> tenantId.equals(m.getTenantId()))
            .filter(
                m ->
                    m.getPermission() == null
                        || m.getPermission().isBlank()
                        || scopes.contains(m.getPermission()))
            .toList();

    List<MenuNode> nodes = visible.stream().map(MenuApplicationService::toNode).toList();

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

  private Long resolveTenantId(Long fromQuery) {
    if (fromQuery != null) {
      return fromQuery;
    }
    Long fromContext = tenantProvider.currentTenantIdOrNull();
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

  private static MenuTreeDTO toTreeDto(Menu m) {
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

  private static MenuNode toNode(Menu m) {
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
