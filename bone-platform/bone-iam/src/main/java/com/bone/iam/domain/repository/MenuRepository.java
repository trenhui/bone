package com.bone.iam.domain.repository;

import com.bone.iam.domain.model.menu.Menu;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/**
 * 菜单聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>菜单树与「当前用户可见菜单」的取数属「本聚合读」：DSL 驻留此处，{@code application} 层只做权限过滤与装配（E-4.2）。
 */
public interface MenuRepository extends Repository<Menu, Long> {

  /** 全量菜单节点（树装配 / 当前用户菜单过滤前的取数，仍受租户过滤）。 */
  default List<Menu> listAll() {
    return QueryBuilder.from(Menu.class).list();
  }
}
