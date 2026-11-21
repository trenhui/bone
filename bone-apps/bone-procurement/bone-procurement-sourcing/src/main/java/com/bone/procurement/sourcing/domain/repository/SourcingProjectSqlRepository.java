package com.bone.procurement.sourcing.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.sourcing.domain.model.sourcing.SourcingProject;
import java.util.List;
import java.util.Optional;

/** 寻源项目写侧仓储：查询落在 domain.repository 的 default 方法（对齐 transaction 范式）。 */
public interface SourcingProjectSqlRepository extends Repository<SourcingProject, Long> {

  default Optional<SourcingProject> findByProjectNo(Long tenantId, String projectNo) {
    List<SourcingProject> list =
        findByCriteria(
            Criteria.<SourcingProject>create()
                .entityClass(SourcingProject.class)
                .eq("tenantId", tenantId)
                .eq("projectNo", projectNo));
    return list.stream().findFirst();
  }
}
