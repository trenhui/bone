package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.iam.application.query.dto.MenuNode;
import com.bone.iam.application.query.dto.MenuTreeDTO;
import com.bone.iam.application.query.qry.MenuCurrentQuery;
import com.bone.iam.application.query.qry.MenuTreeQuery;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.menu.Menu;
import com.bone.iam.domain.repository.MenuRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 菜单排序契约：**每一层**都按 order_no 升序（同序号按 id 升序），而不是只排顶层。
 *
 * <p>缺陷背景（2026-10-28）：改造前只有根节点参与排序，子节点顺序 = 仓储返回顺序（实际为主键序）， 于是「菜单管理」里调整子菜单 order_no
 * 完全看不到效果（配了不生效）。本测试用**乱序 + 与期望序相反的 主键序**构造输入，删掉排序实现即转红（变异可验证）。
 */
@ExtendWith(MockitoExtension.class)
class MenuApplicationServiceMenuOrderTest {

  @Mock private MenuRepository menuRepository;

  @Mock private TenantProvider tenantProvider;

  @InjectMocks private MenuApplicationService service;

  /** 子节点按主键升序返回，但业务期望顺序（order_no）恰好相反 ⇒ 只有真排序才能通过。 */
  private Menu dir;

  private Menu first;
  private Menu second;
  private Menu third;

  @BeforeEach
  void setUp() {
    lenient().when(tenantProvider.currentTenantIdOrNull()).thenReturn(null);
    dir = menu(100L, null, "智能理赔", null, null, 12);
    // 主键序：101 报案申请(order 8) → 102 核赔工作台(order 1) → 103 后督抽检(order 7)
    first = menu(101L, 100L, "理赔申请", "/claim#/submit", "claim:claims:read", 8);
    second = menu(102L, 100L, "核赔工作台", "/claim#/review", "claim:claims:read", 1);
    third = menu(103L, 100L, "后督抽检", "/claim#/inspection", "claim:claims:read", 7);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void treeSortsChildrenByOrderNoNotById() {
    when(menuRepository.listAll()).thenReturn(List.of(dir, first, second, third));

    List<MenuTreeDTO> tree = service.tree(new MenuTreeQuery());

    assertThat(tree).extracting(MenuTreeDTO::getName).containsExactly("智能理赔");
    assertThat(tree.get(0).getChildren())
        .extracting(MenuTreeDTO::getName)
        .containsExactly("核赔工作台", "后督抽检", "理赔申请");
  }

  @Test
  void currentSortsChildrenByOrderNoSoSidebarMatchesOrderNo() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new JwtPrincipal("1", "admin", "0", List.of("claim:claims:read"), List.of()),
                null,
                List.of()));
    when(menuRepository.listAll()).thenReturn(List.of(dir, first, second, third));

    List<MenuNode> nodes = service.current(new MenuCurrentQuery());

    assertThat(nodes).extracting(MenuNode::getName).containsExactly("智能理赔");
    assertThat(nodes.get(0).getChildren())
        .extracting(MenuNode::getName)
        .containsExactly("核赔工作台", "后督抽检", "理赔申请");
  }

  @Test
  void equalOrderNoFallsBackToIdAscending() {
    Menu a = menu(201L, 100L, "同序-后建", "/a", null, 5);
    Menu b = menu(200L, 100L, "同序-先建", "/b", null, 5);
    when(menuRepository.listAll()).thenReturn(List.of(dir, a, b));

    List<MenuTreeDTO> tree = service.tree(new MenuTreeQuery());

    // order_no 相同时按 id 升序（200 < 201），保证每次下发顺序稳定，侧边栏不会「忽上忽下」
    assertThat(tree.get(0).getChildren())
        .extracting(MenuTreeDTO::getName)
        .containsExactly("同序-先建", "同序-后建");
  }

  private static Menu menu(
      long id, Long parentId, String name, String path, String permission, int orderNo) {
    Menu menu = mock(Menu.class);
    lenient().when(menu.getId()).thenReturn(id);
    lenient().when(menu.getParentId()).thenReturn(parentId);
    lenient().when(menu.getName()).thenReturn(name);
    lenient().when(menu.getPath()).thenReturn(path);
    lenient().when(menu.getPermission()).thenReturn(permission);
    lenient().when(menu.getOrderNo()).thenReturn(orderNo);
    lenient().when(menu.getTenantId()).thenReturn(0L);
    lenient().when(menu.getType()).thenReturn(1);
    return menu;
  }
}
