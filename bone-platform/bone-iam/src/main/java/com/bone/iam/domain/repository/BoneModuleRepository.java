package com.bone.iam.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.iam.domain.model.app.BoneModule;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;

/**
 * 模块聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>与 {@link BoneApplicationRepository} 同样落在 {@code domain.repository}：模块与应用的读写模型同居本包，模块侧不再另设
 * {@code domain.app.repository} 子包（E-13 包表达唯一）。
 *
 * <p>读侧 DSL 只出现在本接口的 {@code default} 方法内（E-4.2）。
 */
public interface BoneModuleRepository extends Repository<BoneModule, Long> {

  /** 模块分页（本聚合读）：按应用过滤，按 {@code sortOrder} 升序。 */
  default PageResult<BoneModule> findModulePage(Long appId, int pageNo, int pageSize) {
    FluentQuery<BoneModule> query = QueryBuilder.from(BoneModule.class);
    if (appId != null) {
      query.where(BoneModule::getAppId).eq(appId);
    }
    return query.orderByAsc(BoneModule::getSortOrder).page(pageNo, pageSize);
  }

  /** 按 id 取模块；不存在返回 {@code null}（与原 {@code single()} 语义一致，由调用方转 404）。 */
  default BoneModule findModuleById(Long id) {
    if (id == null) {
      return null;
    }
    return QueryBuilder.from(BoneModule.class).where(BoneModule::getId).eq(id).single();
  }
}
