package com.bone.engine.extension.studio.service.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * 资源处理工具类
 * 提供类资源扫描和类名解析的通用功能
 */
@Slf4j
public class ResourceUtils {

    /**
     * 从资源路径中提取类名
     * @param resource 资源对象
     * @param basePackage 基础包名
     * @return 类的全限定名，如果无法解析则返回null
     */
    public static String getClassNameFromResource(Resource resource, String basePackage) {
        try {
            String resourcePath = resource.getURI().getPath();
            String packagePath = basePackage.replace('.', '/');
            int startIndex = resourcePath.indexOf(packagePath);
            if (startIndex != -1) {
                String className = resourcePath.substring(startIndex).replace('/', '.');
                int classIndex = className.lastIndexOf(".class");
                if (classIndex != -1) {
                    return className.substring(0, classIndex);
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Error extracting class name from resource: {}", resource, e);
            return null;
        }
    }

    /**
     * 根据位置模式获取资源集合
     * @param locationPattern 资源位置模式
     * @return 找到的资源集合
     * @throws IOException 如果资源加载失败
     */
    public static Set<Resource> getResources(String locationPattern) throws IOException {
        Set<Resource> result = new HashSet<>();
        try {
            // 使用ResourcePatternResolver获取多个资源
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(locationPattern);
            for (Resource resource : resources) {
                if (resource.exists()) {
                    result.add(resource);
                }
            }
        } catch (IOException e) {
            log.warn("Error loading resources for pattern: {}", locationPattern, e);
            throw e; // 重新抛出异常以便调用者处理
        }
        return result;
    }
}