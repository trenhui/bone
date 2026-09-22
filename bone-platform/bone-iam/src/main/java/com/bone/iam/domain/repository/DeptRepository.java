package com.bone.iam.domain.repository;

import com.bone.iam.domain.model.dept.Dept;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/**
 * 组织机构聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>机构树的取数属「本聚合读」：DSL 驻留此处，{@code application} 层只做租户过滤与 DTO 装配（E-4.2）。
 */
public interface DeptRepository extends Repository<Dept, Long> {

  /** 全量机构节点（树装配前的取数，仍受租户过滤）。 */
  default List<Dept> listAll() {
    return QueryBuilder.from(Dept.class).list();
  }
}
