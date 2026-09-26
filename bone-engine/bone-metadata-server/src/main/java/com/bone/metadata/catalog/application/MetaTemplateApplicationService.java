package com.bone.metadata.catalog.application;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.InstantiateFromTemplateCommand;
import com.bone.metadata.catalog.domain.gateway.TemplateReadPort;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplate;
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
public class MetaTemplateApplicationService {

  private final TemplateReadPort templateReadPort;
  private final MetaEntityApplicationService metaEntityApplicationService;
  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;

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
      throw BizException.of(409, "模板尚未发布，不可实例化: " + template.getCode());
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
    createCmd.setDeliveryMode(cmd.getDeliveryMode());
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
              cmd.getModuleId());
      metaFieldRepository.insert(field);
    }
    log.info(
        "[G3] 模板实例化完成: template={}({}) -> entity={}({}) fields={}",
        template.getCode(),
        template.getCurrentVersion(),
        entity.getCode(),
        entityId,
        templateFields.size());
    return entityId;
  }

  private MetaModelTemplate requireTemplate(Long templateId) {
    if (templateId == null) {
      throw BizException.of("模板ID不能为空");
    }
    return templateReadPort
        .findPlatformTemplate(templateId)
        .orElseThrow(() -> BizException.of(404, "平台模板不存在: " + templateId));
  }
}
