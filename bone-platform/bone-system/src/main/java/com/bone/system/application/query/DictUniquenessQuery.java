package com.bone.system.application.query;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.system.domain.dict.SysDict;
import com.bone.system.domain.dict.vo.DictType;
import com.bone.system.domain.repository.SysDictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 字典项唯一性查询（应用读侧）。
 *
 * <p>同 {@link ConfigUniquenessQuery}：读侧 DSL（{@code Criteria}）只允许出现在应用读侧，写用例与 domain 层禁止
 * 直接依赖（CORE-05 / E-9.3）。
 */
@Component
@RequiredArgsConstructor
public class DictUniquenessQuery {

  private final SysDictRepository sysDictRepository;

  /** 同一字典类型下 {@code code} 是否已存在（软删记录不计入）。 */
  public boolean existsByTypeAndCode(DictType type, String code) {
    SysDict existing =
        sysDictRepository.findOneByCriteria(
            Criteria.<SysDict>create()
                .entityClass(SysDict.class)
                .eq("type", type)
                .eq("code", code));
    return existing != null;
  }
}
