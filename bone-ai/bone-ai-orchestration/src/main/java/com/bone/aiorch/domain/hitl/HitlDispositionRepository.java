package com.bone.aiorch.domain.hitl;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.Comparator;
import java.util.List;

/**
 * HITL 处置链仓储：{@code extends Repository} 由 bone-metadata-sdk 动态代理（{@code @EnableSqlRepositories} 扫描
 * “*Repository”接口即生成实现），无需手写实现类（HC-001 唯一持久化方案）。
 *
 * <p>租户 / 软删由 TenantFilterInjector 自动注入；自定义查询用 {@code default} + QueryBuilder。
 */
public interface HitlDispositionRepository extends Repository<HitlDisposition, Long> {

  /** 某待裁决项的完整处置链（按时间正序——回放“先做了什么、后做了什么”）。 */
  default List<HitlDisposition> findByItemId(String itemId) {
    List<HitlDisposition> list =
        QueryBuilder.from(HitlDisposition.class)
            .where(HitlDisposition::getItemId)
            .eq(itemId)
            .list();
    list.sort(
        new Comparator<HitlDisposition>() {
          @Override
          public int compare(HitlDisposition a, HitlDisposition b) {
            return a.getActedAt().compareTo(b.getActedAt());
          }
        });
    return list;
  }
}
