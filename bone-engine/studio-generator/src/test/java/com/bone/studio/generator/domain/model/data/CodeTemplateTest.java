package com.bone.studio.generator.domain.model.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** {@link CodeTemplate} 纯单测：DRAFT 起步、发布与改稿回落 DRAFT 的生命周期（无容器）。 */
class CodeTemplateTest {

  private CodeTemplate createTemplate() {
    return CodeTemplate.create(1L, 1L, "实体模板", "entity", "生成实体类", "ENTITY", "public class X {}");
  }

  @Test
  void testCreateDefaultsToDraftProfile() {
    CodeTemplate template = createTemplate();

    assertEquals("DRAFT", template.getStatus());
    assertEquals("java", template.getLanguage());
    assertEquals("FREEMARKER", template.getEngine());
    assertEquals("1.0.0", template.getTemplateVersion());
    assertNull(template.getPublishedAt());
  }

  @Test
  void testPublishMarksPublishedAndBumpsVersion() {
    CodeTemplate template = createTemplate();

    template.publish();

    assertEquals("PUBLISHED", template.getStatus());
    assertNotNull(template.getPublishedAt());
    assertEquals(1, template.getVersion());
  }

  @Test
  void testUpdateContentFlipsBackToDraft() {
    CodeTemplate template = createTemplate();
    template.publish();

    template.updateContent("public class Y {}");

    assertEquals("DRAFT", template.getStatus());
    assertEquals("public class Y {}", template.getContent());
  }

  /**
   * 模板元信息（名称/编码/描述/类型）必须能被编辑生效。
   *
   * <p><b>实测缺陷：</b>{@code UpdateCodeTemplateApplicationService} 曾只调 {@link
   * CodeTemplate#updateContent}，于是 {@code PUT /api/v1/generator/templates/{id}} 返回 200， 但 UI
   * 上改名/改描述全部静默丢弃。
   *
   * <p><b>负向探针：</b>把 service 里的 {@code template.update(...)} 退回 {@code
   * template.updateContent(command.getContent());}，下面的元信息断言必须变红。
   */
  @Test
  void testUpdateAppliesMetadataAndContent() {
    CodeTemplate template = createTemplate();
    template.publish();

    template.update("实体模板改名", "entity_v2", "生成实体类 v2", "ENTITY", "public class Y {}");

    assertEquals("实体模板改名", template.getName());
    assertEquals("entity_v2", template.getCode());
    assertEquals("生成实体类 v2", template.getDescription());
    assertEquals("ENTITY", template.getType());
    assertEquals("public class Y {}", template.getContent());
    // 正文变了 ⇒ 必须回落到 DRAFT，等待重新发布
    assertEquals("DRAFT", template.getStatus());
  }

  /** null = 本次不改该字段；且仅改元信息不应把已发布状态打回 DRAFT。 */
  @Test
  void testUpdateKeepsFieldsWhenNullAndKeepsPublishedStatus() {
    CodeTemplate template = createTemplate();
    template.publish();

    template.update("只改名字", null, null, null, null);

    assertEquals("只改名字", template.getName());
    assertEquals("生成实体类", template.getDescription());
    assertEquals("entity", template.getCode());
    assertEquals("public class X {}", template.getContent());
    assertEquals("PUBLISHED", template.getStatus());
  }
}
