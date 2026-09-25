package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.iam.application.query.dto.DeptTreeDTO;
import com.bone.iam.application.query.qry.DeptTreeQuery;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.dept.Dept;
import com.bone.iam.domain.repository.DeptRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * DeptApplicationService#tree 的 keyword 过滤契约测试。
 *
 * <p>keyword 过滤须保留命中节点、其祖先链与其子树（树结构完整可操作）；无命中返回空树；keyword 为空时不过滤。
 */
@ExtendWith(MockitoExtension.class)
class DeptApplicationServiceTreeKeywordTest {

  @Mock private DeptRepository deptRepository;

  @Mock private TenantProvider tenantProvider;

  @InjectMocks private DeptApplicationService service;

  private Dept root;
  private Dept child;
  private Dept other;

  @BeforeEach
  void setUp() {
    lenient().when(tenantProvider.currentTenantIdOrNull()).thenReturn(null);
    root = dept(1L, null, "技术研发中心");
    child = dept(2L, 1L, "前端组");
    other = dept(3L, null, "客户成功部");
  }

  @Test
  void keywordKeepsMatchedNodeWithAncestorsAndSubtree() {
    when(deptRepository.listAll()).thenReturn(List.of(root, child, other));

    List<DeptTreeDTO> tree = service.tree(query("前端"));

    assertThat(tree).hasSize(1);
    assertThat(tree.get(0).getName()).isEqualTo("技术研发中心");
    assertThat(tree.get(0).getChildren()).extracting(DeptTreeDTO::getName).containsExactly("前端组");
  }

  @Test
  void keywordMatchedParentKeepsSubtree() {
    when(deptRepository.listAll()).thenReturn(List.of(root, child, other));

    List<DeptTreeDTO> tree = service.tree(query("技术研发"));

    assertThat(tree).hasSize(1);
    assertThat(tree.get(0).getName()).isEqualTo("技术研发中心");
    assertThat(tree.get(0).getChildren()).extracting(DeptTreeDTO::getName).containsExactly("前端组");
  }

  @Test
  void noMatchReturnsEmptyTree() {
    when(deptRepository.listAll()).thenReturn(List.of(root, child, other));

    assertThat(service.tree(query("不存在的名字"))).isEmpty();
  }

  @Test
  void blankKeywordKeepsAllNodes() {
    when(deptRepository.listAll()).thenReturn(List.of(root, child, other));

    assertThat(service.tree(query(null))).hasSize(2);
    assertThat(service.tree(query(""))).hasSize(2);
    assertThat(service.tree(query("  "))).hasSize(2);
  }

  private static DeptTreeQuery query(String keyword) {
    DeptTreeQuery qry = new DeptTreeQuery();
    qry.setKeyword(keyword);
    return qry;
  }

  private static Dept dept(long id, Long parentId, String name) {
    Dept dept = mock(Dept.class);
    when(dept.getId()).thenReturn(id);
    lenient().when(dept.getParentId()).thenReturn(parentId);
    lenient().when(dept.getName()).thenReturn(name);
    lenient().when(dept.getTenantId()).thenReturn(0L);
    return dept;
  }
}
