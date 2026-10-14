package com.bone.metadata.catalog.domain.gateway;

import com.bone.metadata.catalog.domain.model.template.MetaModelTemplate;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateAction;
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

  /**
   * 模板状态机动作定义（按 sort_order 升序；迁移 0040）。
   *
   * <p>没有动作定义 ⇒ 实例化出的实体 {@code state_field} 推导为空 ⇒ 受保护状态列既不被注入入口态
   * （创建必失败）也不被保护（可绕过状态机直写终态）。故实例化必须同步消费本端口。
   *
   * @return 空列表表示该模板无状态机（如主数据类模板），属正常情况
   */
  List<MetaModelTemplateAction> actionsOf(Long templateId);

  /**
   * 模板当前界面快照（迁移 0046：实例化拷贝 + 跟随的 {@code base.new}）。
   *
   * @param templateCode 模板 code
   * @return empty = 模板不存在，或资产未补齐（{@code ui_schema_json} 为空，跟随入口保持 disabled）
   */
  Optional<TemplateUiSnapshot> findUiSnapshotByCode(String templateCode);

  /**
   * 模板界面版本历史快照（迁移 0046：三路归并的 {@code base.old}）。
   *
   * @param templateCode 模板 code
   * @param version 实例 {@code base_version} 指向的版本
   * @return empty = 无历史快照，降级为两路归并（base.new vs instance）
   */
  Optional<String> findUiHistorySnapshot(String templateCode, String version);
}
