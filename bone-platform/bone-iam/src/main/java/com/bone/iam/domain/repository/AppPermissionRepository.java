package com.bone.iam.domain.repository;

import com.bone.iam.domain.model.app.AppPermission;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import java.util.Optional;

/**
 * 应用权限聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>本聚合的读模型驻留此处，不另设 {@code *QueryPort}——读与写模型没有分歧，建端口只是仪式性分层（E-3.2）。 读侧 DSL（{@link
 * Criteria}）因此只出现在域层，不进入 {@code application}（E-4.2）。
 */
public interface AppPermissionRepository extends Repository<AppPermission, Long> {

  /** 按 (应用 ID, 用户 ID) 定位唯一绑定（唯一键 {@code uk_app_user} 保证至多一条）。 */
  default Optional<AppPermission> findByAppAndUser(Long appId, Long userId) {
    return Optional.ofNullable(
        findOneByCriteria(
            Criteria.<AppPermission>create().eq("appId", appId).eq("userId", userId)));
  }

  /** 查询某应用下的全部权限绑定（应用权限列表）。 */
  default List<AppPermission> findByApp(Long appId) {
    return findByCriteria(Criteria.<AppPermission>create().eq("appId", appId));
  }
}
