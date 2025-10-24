package com.bone.metadata.sdk.sql.template.provider;

import com.bone.metadata.sdk.domain.exception.TemplateLoadException;
import com.bone.metadata.sdk.domain.exception.TemplateNotFoundException;
import com.bone.metadata.sdk.sql.template.TemplateDescriptor;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 统一的类路径资源提供者，支持加载各种格式的SQL模板文件
 * 支持结构化路径（如 sql/<package>/<ClassName>/<methodName>.sql 或 sql-templates/<ClassName>/<methodName>.yaml）
 */
public class ClasspathSourceProvider implements TemplateSourceProvider {
    private static final Logger log = LoggerFactory.getLogger(ClasspathSourceProvider.class);
    private final ResourceLoader resourceLoader;
    private final SqlConfigProperties config;

    public ClasspathSourceProvider(ResourceLoader resourceLoader, SqlConfigProperties config) {
        this.resourceLoader = resourceLoader;
        this.config = config;
    }

    @Override
    public boolean supports(URI sourceUri) {
        return "classpath".equalsIgnoreCase(sourceUri.getScheme());
    }

    @Override
    public LoadedSource load(Method method, TemplateDescriptor descriptor) throws TemplateLoadException {
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
                    contentStr,
                    StandardCharsets.UTF_8.name(),
                    path,
                    System.currentTimeMillis()
            );
        } catch (TemplateNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("加载类路径模板失败: {}, 原因: {}", path, e.getMessage(), e);
            throw new TemplateLoadException("加载类路径模板失败: " + path, e);
        }
    }
}