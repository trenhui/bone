package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.template.TemplateVersion;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 域模板版本仓储：写能力来自基类 {@link Repository}，读侧以 default 方法承载（ADR-0030）。
 *
 * <p>读侧 DSL（Criteria）只许出现在本接口（{@code ..domain.repository..} 被门禁豁免）。
 */
public interface TemplateVersionRepository extends Repository<TemplateVersion, Long> {

  /** 按模板 ID 列出全部版本（按创建时间倒序由调用方排序或此处直接返回）。 */
  default List<TemplateVersion> findByTemplateId(Long templateId) {
    return findByCriteria(
        Criteria.<TemplateVersion>create()
            .entityClass(TemplateVersion.class)
            .eq("templateId", templateId));
  }

  /** 判断某模板下版本号是否已存在（发布版本幂等性校验）。 */
  default long countByTemplateIdAndVersion(Long templateId, String versionNumber) {
    return countByCriteria(
        Criteria.<TemplateVersion>create()
            .entityClass(TemplateVersion.class)
            .eq("templateId", templateId)
            .eq("versionNumber", versionNumber));
  }
}
