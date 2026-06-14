package com.bone.metadata.sdk.sql.template.provider;

import com.bone.metadata.sdk.domain.exception.TemplateLoadException;
import com.bone.metadata.sdk.domain.exception.TemplateNotFoundException;
import com.bone.metadata.sdk.sql.template.TemplateDescriptor;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;

/** 从类路径加载YAML SQL模板，支持结构化路径（如 sql-templates/<ClassName>/<methodName>.yaml）。 */
@Slf4j
public class ClasspathYamlSourceProvider implements TemplateSourceProvider {
  private final ResourceLoader resourceLoader;
  private final SqlConfigProperties config;

  public ClasspathYamlSourceProvider(ResourceLoader resourceLoader, SqlConfigProperties config) {
    this.resourceLoader = resourceLoader;
    this.config = config;
  }

  @Override
  public boolean supports(URI sourceUri) {
    return "classpath".equalsIgnoreCase(sourceUri.getScheme());
  }

  @Override
  public LoadedSource load(Method method, TemplateDescriptor descriptor)
      throws TemplateLoadException {
    String path = descriptor.getSourceUri();
    try {
      Resource resource = resourceLoader.getResource(path);
      if (!resource.exists()) {
        log.warn("类路径资源未找到: {}。请确保文件存在于 src/main/resources。", path);
        return null;
      }
      if (!resource.isReadable()) {
        log.error("类路径资源不可读: {}", path);
        throw new TemplateLoadException("类路径资源不可读: " + path);
      }
      byte[] content = FileCopyUtils.copyToByteArray(resource.getInputStream());
      String contentStr = new String(content, StandardCharsets.UTF_8).trim();
      log.debug("加载类路径模板: {}, 大小: {} 字节", path, content.length);
      return new LoadedSource(
          contentStr, StandardCharsets.UTF_8.name(), path, System.currentTimeMillis());
    } catch (TemplateNotFoundException e) {
      throw e;
    } catch (Exception e) {
      log.error("加载类路径模板失败: {}, 原因: {}", path, e.getMessage(), e);
      throw new TemplateLoadException("加载类路径模板失败: " + path, e);
    }
  }
}
