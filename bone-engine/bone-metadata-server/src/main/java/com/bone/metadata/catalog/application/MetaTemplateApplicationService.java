package com.bone.metadata.catalog.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.InstantiateFromTemplateCommand;
import com.bone.metadata.catalog.common.CatalogErrorCodes;
import com.bone.metadata.catalog.common.CatalogErrors;
import com.bone.metadata.catalog.domain.gateway.ActionDefMaterializePort;
import com.bone.metadata.catalog.domain.gateway.TemplateReadPort;
import com.bone.metadata.catalog.domain.gateway.TemplateUiSnapshot;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplate;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateAction;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateField;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台模型模板应用服务（G3/ADR-0031）。
 *
 * <p>模板是平台层（tenant_id=0）资产：本服务只实现「查询 + 实例化」两个租户侧用例（UC-MP1 的模板维护由平台运营经管理通道/种子完成， 暂不开写
 * API——避免平台写面扩散；见 ADR-0031 §6 分批）。目录读取走 {@link TemplateReadPort}（E-4.2 infrastructure/query，
 * 平台表跨租户只读，不走域仓储 disableTenantFilter 通道）；实例化 = 复制模板为租户 {@code meta_entity} （{@code
 * scope=TENANT}、{@code template_id} 追溯）+ 逐条复制默认字段集，实体创建复用 {@link MetaEntityApplicationService}
 * 的唯一性与建模准入校验（G1②）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@NoDomainEvent // E-5.4 豁免：技术中间态落库，见类 JavaDoc
public class MetaTemplateApplicationService {

  private final TemplateReadPort templateReadPort;
  private final MetaEntityApplicationService metaEntityApplicationService;
  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;

  /** 模板状态机动作展开（迁移 0040）：产出 meta_entity_action_def，使实例化结果「可用」而非只是「有形」。 */
  private final ActionDefMaterializePort actionDefMaterializePort;

  /** 界面 schema 拷贝（迁移 0046）：实例化同步复制模板快照 + base 指针（同界应用服务互调）。 */
  private final UiSchemaApplicationService uiSchemaApplicationService;

  /** 模板目录（平台层全租户共享，keyword 模糊匹配编码/名称）。 */
  @Transactional(readOnly = true)
  public List<MetaModelTemplate> listTemplates(String keyword) {
    return templateReadPort.listPlatformTemplates(keyword);
  }

  /** 模板默认字段集（平台层数据，跨租户只读）。 */
  @Transactional(readOnly = true)
  public List<MetaModelTemplateField> templateFields(Long templateId) {
    requireTemplate(templateId);
    return templateReadPort.fieldsOf(templateId);
  }

  /**
   * UC-MT2 从模板实例化租户实体。
   *
   * @return 新实体 ID
   */
  @Transactional
  public Long instantiate(Long templateId, InstantiateFromTemplateCommand cmd) {
    MetaModelTemplate template = requireTemplate(templateId);
    if (template.getStatus() == null || template.getStatus() != 1) {
      throw CatalogErrors.of(CatalogErrorCodes.TEMPLATE_NOT_PUBLISHED, template.getCode());
    }
    // 复用实体创建门面：编码/表名唯一性校验 + 模块建模准入（G1②）都在其中
    CreateMetaEntityCommand createCmd = new CreateMetaEntityCommand();
    createCmd.setName(cmd.getName());
    createCmd.setCode(cmd.getCode());
    createCmd.setDisplayName(cmd.getDisplayName());
    createCmd.setDescription(cmd.getDescription());
    createCmd.setTableName(cmd.getTableName());
    createCmd.setModuleId(cmd.getModuleId());
    createCmd.setIcon(cmd.getIcon());
    // 交付模式：调用方显式值优先，缺省继承模板声明（0041）。
    // 继承而非重猜——模板是蓝图，其实例化漏传 GENERATIVE 会「发布成功但无表」（E2E 实证坑）；
    // 模板自身未声明时（存量 0）兜底 GENERATIVE，保守不建表。
    createCmd.setDeliveryMode(
        cmd.getDeliveryMode() != null ? cmd.getDeliveryMode() : template.getDeliveryMode());
    Long entityId = metaEntityApplicationService.createEntity(createCmd);

    MetaEntity entity = metaEntityRepository.findById(entityId);
    entity.markInstantiatedFrom(templateId);
    metaEntityRepository.update(entity);

    List<MetaModelTemplateField> templateFields = templateReadPort.fieldsOf(templateId);
    for (MetaModelTemplateField tf : templateFields) {
      MetaField field =
          MetaField.create(
              null,
              entity.getTenantId(),
              entityId,
              tf.getName(),
              tf.getCode(),
              tf.getDisplayName(),
              tf.getFieldType(),
              tf.getLength(),
              tf.getRequired(),
              tf.getUnique(),
              tf.getDefaultValue(),
              tf.getComment(),
              tf.getSortOrder(),
              cmd.getModuleId(),
              // 以下 7 项为数据治理列（MetaField 于 aa2e77d0 扩展为 23 参工厂）。模板字段
              // MetaModelTemplateField 本身不含这些列，实例化时无值可透传，故显式置 null
              // （与 8 参工厂 create(...) 内部填 null 的口径一致）。
              null, // dataClassification
              null, // pii
              null, // sensitivityLevel
              null, // dataSteward
              null, // businessTerm
              null, // sourceSystem
              tf.getEnumValues(), // enumValues（W1a T2：模板码表透传）
              tf.getValidationRules()); // validationRules（W1a T2：模板质量规则透传）
      metaFieldRepository.insert(field);
    }

    // ── 状态机动作：实例化必须同步产出 meta_entity_action_def（迁移 0040）────────────
    // 只复制对象与字段是不够的：state_field 只能从动作定义推导，缺失 ⇒
    //   ① 入口状态不被注入 → 受保护状态列必填却无来源 → 创建记录恒 400（UI 实测录入失败）；
    //   ② 写保护不生效 → 可绕过状态机直写终态（实测 status=PAID 201）。
    // 无状态机的模板（如主数据类）actionsOf 返回空列表是正常情况，不是错误。
    List<MetaModelTemplateAction> templateActions = templateReadPort.actionsOf(templateId);
    int actionCount = 0;
    if (!templateActions.isEmpty()) {
      actionCount =
          actionDefMaterializePort.materialize(
              entity.getTenantId() == null ? 0L : entity.getTenantId(),
              entity.getCode(),
              templateActions);
    }

    // ── 界面 schema：实例化同步拷贝模板快照 + 写 base 指针（迁移 0046）────────────
    // 模板作者看不到目标租户字段定义，只能引用抽象列；实例化后草稿态可改，发布冻结快照。
    // 模板资产缺失（ui_schema_json 为空）即跳过，不阻断实例化（前置诚实门，技术方案 §5.2）。
    templateReadPort
        .findUiSnapshotByCode(template.getCode())
        .filter(TemplateUiSnapshot::hasSchema)
        .ifPresent(
            snapshot ->
                uiSchemaApplicationService.copyFromTemplate(
                    entityId,
                    entity.getCode(),
                    template.getCode(),
                    template.getCurrentVersion(),
                    snapshot.schemaJson()));

    log.info(
        "[G3] 模板实例化完成: template={}({}) -> entity={}({}) fields={} actions={}",
        template.getCode(),
        template.getCurrentVersion(),
        entity.getCode(),
        entityId,
        templateFields.size(),
        actionCount);
    return entityId;
  }

  private MetaModelTemplate requireTemplate(Long templateId) {
    if (templateId == null) {
      throw CatalogErrors.of(CatalogErrorCodes.TEMPLATE_ID_REQUIRED, "模板ID不能为空");
    }
    return templateReadPort
        .findPlatformTemplate(templateId)
        .orElseThrow(() -> CatalogErrors.of(CatalogErrorCodes.TEMPLATE_NOT_FOUND, templateId));
  }
}
