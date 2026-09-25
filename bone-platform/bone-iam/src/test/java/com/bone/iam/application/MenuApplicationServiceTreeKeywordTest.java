package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.iam.application.query.dto.MenuTreeDTO;
import com.bone.iam.application.query.qry.MenuTreeQuery;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.menu.Menu;
import com.bone.iam.domain.repository.MenuRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MenuApplicationService#tree 的 keyword 过滤契约测试：按名称 / 路由路径 / 权限码命中，保留命中节点、 其祖先链与其子树；无命中返回空树；keyword
 * 为空时不过滤。
 */
@ExtendWith(MockitoExtension.class)
class MenuApplicationServiceTreeKeywordTest {

  @Mock private MenuRepository menuRepository;

  @Mock private TenantProvider tenantProvider;

  @InjectMocks private MenuApplicationService service;

  private Menu root;
  private Menu child;
  private Menu other;

  @BeforeEach
  void setUp() {
    lenient().when(tenantProvider.currentTenantIdOrNull()).thenReturn(null);
    root = menu(1L, null, "平台控制台", "/console", null);
    child = menu(2L, 1L, "账号管理", "/iam/users", "iam:users:read");
    other = menu(3L, null, "租户运营", "/tenants", null);
  }

  @Test
  void keywordMatchesName() {
    when(menuRepository.listAll()).thenReturn(List.of(root, child, other));

    List<MenuTreeDTO> tree = service.tree(query("账号"));

    assertThat(tree).hasSize(1);
    assertThat(tree.get(0).getName()).isEqualTo("平台控制台");
    assertThat(tree.get(0).getChildren()).extracting(MenuTreeDTO::getName).containsExactly("账号管理");
  }

  @Test
  void keywordMatchesPath() {
    when(menuRepository.listAll()).thenReturn(List.of(root, child, other));

    List<MenuTreeDTO> tree = service.tree(query("/iam/users"));

    assertThat(tree).hasSize(1);
    assertThat(tree.get(0).getChildren())
        .extracting(MenuTreeDTO::getPath)
        .containsExactly("/iam/users");
  }

  @Test
  void keywordMatchesPermission() {
    when(menuRepository.listAll()).thenReturn(List.of(root, child, other));

    assertThat(service.tree(query("iam:users:read"))).hasSize(1);
  }

  @Test
  void noMatchReturnsEmptyTree() {
    when(menuRepository.listAll()).thenReturn(List.of(root, child, other));

    assertThat(service.tree(query("不存在"))).isEmpty();
  }

  @Test
  void blankKeywordKeepsAllNodes() {
    when(menuRepository.listAll()).thenReturn(List.of(root, child, other));

    assertThat(service.tree(query(null))).hasSize(2);
    assertThat(service.tree(query(""))).hasSize(2);
  }

  private static MenuTreeQuery query(String keyword) {
    MenuTreeQuery qry = new MenuTreeQuery();
    qry.setKeyword(keyword);
    return qry;
  }

  private static Menu menu(long id, Long parentId, String name, String path, String permission) {
    Menu menu = mock(Menu.class);
    when(menu.getId()).thenReturn(id);
    lenient().when(menu.getParentId()).thenReturn(parentId);
    lenient().when(menu.getName()).thenReturn(name);
    lenient().when(menu.getPath()).thenReturn(path);
    lenient().when(menu.getPermission()).thenReturn(permission);
    lenient().when(menu.getTenantId()).thenReturn(0L);
    return menu;
  }
}
