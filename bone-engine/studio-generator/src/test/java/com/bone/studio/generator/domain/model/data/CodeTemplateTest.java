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
}
