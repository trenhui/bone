package com.bone.metadata.sdk.sql.template.provider;

import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.exception.TemplateLoadException;
import com.bone.metadata.sdk.sql.template.TemplateDescriptor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;

@Slf4j
public class AnnotationSourceProvider implements TemplateSourceProvider {

  @Override
  public boolean supports(URI sourceUri) {
    return "annotation".equalsIgnoreCase(sourceUri.getScheme());
  }

  @Override
  public LoadedSource load(Method method, TemplateDescriptor descriptor)
      throws TemplateLoadException {
    Sql sqlAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, Sql.class);
    if (sqlAnnotation != null) {
      log.debug("Loaded annotation template: {}", descriptor.getTemplateId());
      return new LoadedSource(
          sqlAnnotation.value().trim(),
          StandardCharsets.UTF_8.name(),
          descriptor.getSourceUri(),
          System.currentTimeMillis());
    }

    log.warn(descriptor.getTemplateId() + "sqlAnnotation is null");
    return null;
  }
}
