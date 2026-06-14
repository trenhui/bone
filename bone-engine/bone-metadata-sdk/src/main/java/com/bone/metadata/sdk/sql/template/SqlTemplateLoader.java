package com.bone.metadata.sdk.sql.template;

import com.bone.metadata.sdk.domain.exception.TemplateLoadException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

public interface SqlTemplateLoader {
  SqlTemplate loadTemplate(String templateId) throws TemplateLoadException;

  SqlTemplate loadTemplate(Method method, String templateId) throws TemplateLoadException;

  Map<String, SqlTemplate> loadTemplates(Collection<String> templateIds);

  void refreshTemplate(String templateId);
}
