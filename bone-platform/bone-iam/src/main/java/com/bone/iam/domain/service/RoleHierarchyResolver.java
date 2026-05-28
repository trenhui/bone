package com.bone.iam.domain.service;

import com.bone.iam.domain.role.Role;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 解析角色继承闭包（{@code iam_role.parent_role_id}），用于将"直接绑定的角色集合"扩展为
 * "直接角色 ∪ 全部祖先角色"，再供 {@code AccountAuthoritiesQueryHandler} 求权限并集。
 *
 * <p>性能与安全约束（详设 §3.2 / IAM-22）：
 * <ul>
 *   <li>最大递归深度 {@value #MAX_DEPTH}，超出截断并停止向上回溯，避免病态长链</li>
 *   <li>使用已访问集合做环检测（DDL 未约束环路，但代码兜底）</li>
 *   <li>按层批量查询父角色，避免 N+1</li>
 * </ul>
 */
@Service
public class RoleHierarchyResolver {

    /** 闭包遍历最大深度（包含根层），与详设保持一致。 */
    public static final int MAX_DEPTH = 5;

    /**
     * 返回 {@code seedRoleIds} 的祖先闭包（包含 seed 本身）。
     *
     * <p>遇到 {@code null}/空入参直接返回空集合；自动忽略不存在或已软删的角色。
     */
    public Set<Long> resolveClosure(Collection<Long> seedRoleIds) {
        if (seedRoleIds == null || seedRoleIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> closure = new LinkedHashSet<>();
        Deque<Long> currentLayer = new ArrayDeque<>();
        for (Long id : seedRoleIds) {
            if (id != null && closure.add(id)) {
                currentLayer.add(id);
            }
        }
        Set<Long> visited = new HashSet<>(closure);

        int depth = 1;
        while (!currentLayer.isEmpty() && depth < MAX_DEPTH) {
            List<Role> parents = QueryBuilder.from(Role.class)
                    .where(Role::getId)
                    .in(List.copyOf(currentLayer))
                    .list();
            Deque<Long> nextLayer = new ArrayDeque<>();
            for (Role role : parents) {
                Long parentId = role.getParentRoleId();
                if (parentId == null || !visited.add(parentId)) {
                    continue;
                }
                closure.add(parentId);
                nextLayer.add(parentId);
            }
            currentLayer = nextLayer;
            depth++;
        }
        return closure;
    }
}
