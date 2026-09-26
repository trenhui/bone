package com.bone.metadata.catalog.domain.gateway;

import com.bone.metadata.catalog.domain.model.template.MetaModelTemplate;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateField;
import java.util.List;
import java.util.Optional;

/**
 * 平台模型模板读端口（G3/ADR-0031）。
 *
 * <p>模板是<b>平台层</b>资产（{@code tenant_id = 0}，全租户共享只读），读取天然跨租户——不属租户域仓储的读模型（ADR-0030）， 故按 E-4.2 落在
 * {@code infrastructure/query} 实现本端口，而非域仓储 + {@code disableTenantFilter}（后者被 「全租户入口仅限
 * adapter.schedule」门禁正确拦截，模板目录是 HTTP 请求侧读，不该绕过）。
 */
public interface TemplateReadPort {

  /** 平台模板目录（keyword 模糊匹配编码/名称，null/blank 返回全部）。 */
  List<MetaModelTemplate> listPlatformTemplates(String keyword);

  /** 按 ID 取平台模板（不存在返回 empty）。 */
  Optional<MetaModelTemplate> findPlatformTemplate(Long templateId);

  /** 模板默认字段集（按 sort_order 升序）。 */
  List<MetaModelTemplateField> fieldsOf(Long templateId);
}
