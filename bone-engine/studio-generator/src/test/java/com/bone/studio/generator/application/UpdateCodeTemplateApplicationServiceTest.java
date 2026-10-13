package com.bone.studio.generator.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.studio.generator.application.command.cmd.UpdateCodeTemplateCommand;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code UpdateCodeTemplateApplicationService} 单元测试：守卫「编辑模板」的元信息必须落库。
 *
 * <p><b>实测缺陷：</b>service 曾只调 {@code template.updateContent(command.getContent())}， 而控制器明明已经把 {@code
 * name / code / description / type} 都装配进命令了 —— 结果是 {@code PUT /api/v1/generator/templates/{id}} 返回
 * <b>200</b>、UI 提示「保存成功」， 但改名/改描述/改类型全部静默丢弃。只看状态码完全测不出来，必须对落库聚合做断言。
 *
 * <p><b>为什么需要本类（而不是只加领域单测）：</b>{@code CodeTemplate#update} 的领域单测只证明 「方法本身正确」，无法拦住「service 忘了调它」。这里对
 * service → repository 的落库对象断言， 才真正守住了这条装配线。
 *
 * <p><b>负向探针：</b>把 service 里的 {@code template.update(...)} 退回 {@code
 * template.updateContent(command.getContent());}，本类的名称/描述断言必须变红。
 */
@ExtendWith(MockitoExtension.class)
class UpdateCodeTemplateApplicationServiceTest {

  @Mock CodeTemplateRepository codeTemplateRepository;

  @InjectMocks UpdateCodeTemplateApplicationService service;

  @Test
  void updateAppliesNameCodeDescriptionTypeAndContent() {
    CodeTemplate existing =
        CodeTemplate.create(1L, 1L, "旧模板名", "old_code", "旧描述", "ENTITY", "public class Old {}");
    when(codeTemplateRepository.findById(1L)).thenReturn(existing);

    UpdateCodeTemplateCommand cmd =
        UpdateCodeTemplateCommand.builder()
            .id(1L)
            .name("新模板名")
            .code("new_code")
            .description("新描述")
            .type("SERVICE")
            .content("public class New {}")
            .build();

    Long id = service.handle(cmd);

    assertThat(id).isEqualTo(1L);
    ArgumentCaptor<CodeTemplate> captor = ArgumentCaptor.forClass(CodeTemplate.class);
    verify(codeTemplateRepository).save(captor.capture());
    CodeTemplate persisted = captor.getValue();

    assertThat(persisted.getName()).isEqualTo("新模板名");
    assertThat(persisted.getCode()).isEqualTo("new_code");
    assertThat(persisted.getDescription()).isEqualTo("新描述");
    assertThat(persisted.getType()).isEqualTo("SERVICE");
    assertThat(persisted.getContent()).isEqualTo("public class New {}");
  }

  /** 模板不存在必须显式报错，而不是静默「更新成功」。 */
  @Test
  void updateRejectsMissingTemplate() {
    when(codeTemplateRepository.findById(404L)).thenReturn(null);

    UpdateCodeTemplateCommand cmd =
        UpdateCodeTemplateCommand.builder().id(404L).name("whatever").build();

    assertThatThrownBy(() -> service.handle(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("模板不存在");
    verify(codeTemplateRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }
}
