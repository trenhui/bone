package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.command.cmd.InstantiateFromTemplateCommand;
import com.bone.metadata.catalog.domain.gateway.TemplateReadPort;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplate;
import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateField;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 平台模型模板服务单测（G3/ADR-0031）：实例化复制契约、草稿模板拒绝、模板缺失 404。 */
@ExtendWith(MockitoExtension.class)
class MetaTemplateApplicationServiceTest {

  @Mock private TemplateReadPort templateReadPort;
  @Mock private MetaEntityApplicationService metaEntityApplicationService;
  @Mock private MetaEntityRepository metaEntityRepository;
  @Mock private MetaFieldRepository metaFieldRepository;
  @InjectMocks private MetaTemplateApplicationService service;

  private static MetaModelTemplate template(long id, int status) {
    MetaModelTemplate t =
        MetaModelTemplate.create(id, "tpl_customer_base", "客户主数据基础模板", null, "客户", "v1.0.0");
    ReflectionTestUtils.setField(t, "status", status);
    return t;
  }

  private static MetaModelTemplateField templateField(String code, int sortOrder) {
    return MetaModelTemplateField.create(
        null,
        0L,
        9101L,
        code,
        code,
        code + " 显示名",
        "string",
        64,
        true,
        false,
        null,
        null,
        sortOrder);
  }

  @Test
  void instantiate_shouldCopyFieldsAndMarkTemplateTrace() {
    MetaModelTemplate tpl = template(9101L, 1);
    when(templateReadPort.findPlatformTemplate(9101L)).thenReturn(Optional.of(tpl));
    when(templateReadPort.fieldsOf(9101L))
        .thenReturn(List.of(templateField("cust_no", 1), templateField("cust_name", 2)));
    when(metaEntityApplicationService.createEntity(any())).thenReturn(8001L);
    MetaEntity created =
        MetaEntity.create(null, 1L, "客户", "customer", "客户", null, "t_customer", 1, 1, null, 1L);
    when(metaEntityRepository.findById(8001L)).thenReturn(created);

    InstantiateFromTemplateCommand cmd = new InstantiateFromTemplateCommand();
    cmd.setName("客户");
    cmd.setCode("customer");
    cmd.setDisplayName("客户");
    cmd.setTableName("t_customer");
    cmd.setModuleId(1L);

    Long entityId = service.instantiate(9101L, cmd);

    assertThat(entityId).isEqualTo(8001L);
    // G3 追溯：实体标记模板来源
    assertThat(created.getTemplateId()).isEqualTo(9101L);
    assertThat(created.getScope()).isEqualTo("TENANT");
    // 字段逐条复制（2 条模板字段 → 2 条 meta_field insert）
    ArgumentCaptor<MetaField> fieldCaptor = ArgumentCaptor.forClass(MetaField.class);
    verify(metaFieldRepository, org.mockito.Mockito.times(2)).insert(fieldCaptor.capture());
    assertThat(fieldCaptor.getAllValues())
        .extracting(MetaField::getCode)
        .containsExactly("cust_no", "cust_name");
    assertThat(fieldCaptor.getAllValues())
        .allMatch(f -> Long.valueOf(8001L).equals(f.getEntityId()));
  }

  @Test
  void instantiate_shouldReject409_whenTemplateIsDraft() {
    when(templateReadPort.findPlatformTemplate(9101L)).thenReturn(Optional.of(template(9101L, 0)));
    InstantiateFromTemplateCommand cmd = new InstantiateFromTemplateCommand();
    BizException ex =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> service.instantiate(9101L, cmd), BizException.class);
    assertThat(ex.getMessage()).contains("模板尚未发布");
    verify(metaFieldRepository, never()).insert(any(MetaField.class));
  }

  @Test
  void instantiate_shouldReject404_whenTemplateMissing() {
    when(templateReadPort.findPlatformTemplate(9101L)).thenReturn(Optional.empty());
    InstantiateFromTemplateCommand cmd = new InstantiateFromTemplateCommand();
    BizException ex =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> service.instantiate(9101L, cmd), BizException.class);
    assertThat(ex.getMessage()).contains("平台模板不存在");
    verify(metaEntityApplicationService, never()).createEntity(any());
  }

  @Test
  void instantiate_shouldNotQueryTemplateFields_whenEntityCreateFails() {
    when(templateReadPort.findPlatformTemplate(9101L)).thenReturn(Optional.of(template(9101L, 1)));
    when(metaEntityApplicationService.createEntity(any())).thenThrow(BizException.of("实体编码已存在"));
    InstantiateFromTemplateCommand cmd = new InstantiateFromTemplateCommand();
    assertThatThrownBy(() -> service.instantiate(9101L, cmd)).isInstanceOf(BizException.class);
    verify(templateReadPort, never()).fieldsOf(anyLong());
  }
}
