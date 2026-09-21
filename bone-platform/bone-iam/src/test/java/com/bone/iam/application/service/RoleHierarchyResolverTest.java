package com.bone.iam.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.bone.iam.domain.repository.RoleRepository;
import com.bone.iam.domain.role.Role;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 角色继承闭包解析的纯算法单测。
 *
 * <p>下沉前父角色查询走 {@code QueryBuilder}（需真实 DB，难以单测）；下沉后解析器只依赖 {@code RoleRepository#findByIds}，可纯
 * mock，故补本测试覆盖 BFS / 环检测 / 深度截断三类关键路径。
 *
 * <p>注意：{@code RoleRepository#findByIds} 是接口的 {@code default} 方法，其方法体包含 {@code
 * QueryBuilder.from(...)}。必须用 {@code doAnswer().when(mock).findByIds(any())} 而非 {@code
 * when(mock.findByIds(any())).thenAnswer(...)} 打桩——后者会在记录桩时 真实执行一次 default 方法体，触发 {@code
 * QueryBuilder not initialized}，污染 ArchUnit 测试。
 */
@ExtendWith(MockitoExtension.class)
class RoleHierarchyResolverTest {

  @Mock private RoleRepository roleRepository;

  @InjectMocks private RoleHierarchyResolver resolver;

  private Role role(Long id, Long parentId) {
    return Role.create("r" + id, "c" + id, "desc", 0, 0L, parentId);
  }

  private void stubFindByIds(Map<Long, Long> parentOf) {
    doAnswer(inv -> resolveParents(inv.<List<Long>>getArgument(0), parentOf))
        .when(roleRepository)
        .findByIds(any());
  }

  @Test
  void resolvesLinearChain() {
    Map<Long, Long> parentOf = new HashMap<>();
    parentOf.put(3L, 2L);
    parentOf.put(2L, 1L);
    parentOf.put(1L, null);
    stubFindByIds(parentOf);

    assertThat(resolver.resolveClosure(List.of(3L))).containsExactlyInAnyOrder(3L, 2L, 1L);
  }

  @Test
  void detectsCycleWithoutInfiniteLoop() {
    Map<Long, Long> parentOf = Map.of(1L, 2L, 2L, 1L);
    stubFindByIds(parentOf);

    assertThat(resolver.resolveClosure(List.of(1L))).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void truncatesBeyondMaxDepth() {
    // 链 7<-6<-5<-4<-3<-2<-1（7 层 > MAX_DEPTH=5）：从 7 出发，闭包截到 3
    // （含 7,6,5,4,3），第 6 层（2）因深度截断不再展开。
    Map<Long, Long> parentOf = Map.of(7L, 6L, 6L, 5L, 5L, 4L, 4L, 3L, 3L, 2L, 2L, 1L);
    stubFindByIds(parentOf);

    assertThat(resolver.resolveClosure(List.of(7L))).containsExactlyInAnyOrder(7L, 6L, 5L, 4L, 3L);
  }

  @Test
  void emptySeedReturnsEmpty() {
    assertThat(resolver.resolveClosure(List.of())).isEmpty();
  }

  private List<Role> resolveParents(List<Long> ids, Map<Long, Long> parentOf) {
    List<Role> result = new ArrayList<>();
    for (Long id : ids) {
      result.add(role(id, parentOf.get(id)));
    }
    return result;
  }
}
