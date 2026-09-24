package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.CodeTemplate;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模板渲染：优先用库里存的模板内容（在线编辑的自定义模板），缺失时回落 classpath 内置 {@code *.ftl}。
 *
 * <p>背景：原先四个 Generator 直接 {@code freemarkerConfig.getTemplate(code + ".ftl")}，而 Freemarker {@code
 * Configuration} 没有设置 template_loader，任何生成都抛 TemplateNotFoundException；同时库里的 {@code content}
 * 字段从未参与渲染，自定义模板改了不生效。
 */
@Component
@RequiredArgsConstructor
public class TemplateRenderer {

  private final Configuration freemarkerConfig;
  private final freemarker.cache.StringTemplateLoader dbTemplateLoader;

  public String render(CodeTemplate template, Map<String, Object> model) {
    String name = template.getCode() + ".ftl";
    String content = template.getContent();
    try {
      if (content != null && !content.isBlank()) {
        synchronized (dbTemplateLoader) {
          dbTemplateLoader.putTemplate(name, content);
          freemarkerConfig.removeTemplateFromCache(name);
        }
      }
      StringWriter writer = new StringWriter();
      freemarkerConfig.getTemplate(name).process(model, writer);
      return writer.toString();
    } catch (IOException | TemplateException e) {
      throw new IllegalStateException(
          "模板渲染失败: template=" + template.getCode() + ", file=" + name, e);
    }
  }
}
