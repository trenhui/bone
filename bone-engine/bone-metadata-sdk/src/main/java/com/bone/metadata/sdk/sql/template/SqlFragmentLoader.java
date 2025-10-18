package com.bone.metadata.sdk.sql.template;

import com.bone.metadata.sdk.domain.annotation.SqlFragment;
import com.bone.metadata.sdk.support.cache.FragmentCache;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;
import java.util.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SQL 片段加载器，负责根据配置加载和缓存 SQL 片段
 */
@Slf4j
public class SqlFragmentLoader {
    // 确保log变量可用，作为@Slf4j的备份
    private static final Logger log = Logger.getLogger(SqlFragmentLoader.class.getName());
    private final SqlConfigProperties sqlConfigProperties;

    public SqlFragmentLoader(SqlConfigProperties sqlConfigProperties) {
        this.sqlConfigProperties = sqlConfigProperties;
    }

    /**
     * 缓存 SQL 片段，根据配置的加载优先级决定加载顺序
     */
    public void cacheSqlFragmentsForInterface(Class<?> repoInterface) {
        String interfaceName = repoInterface.getName();
        if (FragmentCache.isInitialized(interfaceName)) {
            log.debug("接口 {} 的 SQL 片段已缓存，跳过加载", interfaceName);
            return;
        }

        Map<String, String> fragmentMap = new ConcurrentHashMap<>();
        String loadPriority = getLoadPriority();
        boolean annotationFirst = "annotation-first".equals(loadPriority);

        // 根据优先级决定加载顺序
        boolean fragmentsLoaded = annotationFirst
                ? loadAnnotationFragmentsWithFallback(repoInterface, fragmentMap)
                : loadFileFragmentsWithFallback(interfaceName, fragmentMap);

        // 如果启用回退机制且未加载到片段，尝试次要来源
        if (!fragmentsLoaded && sqlConfigProperties.getTemplate().isFallbackEnabled()) {
            fragmentsLoaded = annotationFirst
                    ? loadFileFragmentsWithExclusion(interfaceName, fragmentMap)
                    : loadAnnotationFragmentsWithExclusion(repoInterface, fragmentMap);
        }

        if (!fragmentMap.isEmpty()) {
            FragmentCache.putClassFragments(interfaceName, fragmentMap);
            log.info("缓存 {} 个 SQL 片段: interface={}, priority={}",
                    fragmentMap.size(), interfaceName, loadPriority);
        } else {
            log.debug("未找到 SQL 片段: interface={}", interfaceName);
        }

        FragmentCache.markInitialized(interfaceName);
    }

    /**
     * 获取加载优先级配置
     */
    private String getLoadPriority() {
        try {
            return sqlConfigProperties.getTemplate().getLoadPriority();
        } catch (Exception e) {
            log.debug("无法获取 SqlConfigProperties，使用默认优先级: annotation-first");
            return "annotation-first";
        }
    }

    /**
     * 优先加载注解片段
     * @return 是否加载到片段
     */
    private boolean loadAnnotationFragmentsWithFallback(Class<?> repoInterface, Map<String, String> fragmentMap) {
        SqlFragment[] fragments = repoInterface.getAnnotationsByType(SqlFragment.class);
        int loadedCount = 0;

        for (SqlFragment fragment : fragments) {
            String id = fragment.id();
            String content = fragment.value().trim();
            if (StringUtils.hasText(id) && StringUtils.hasText(content)) {
                String qualifiedId = repoInterface.getName() + "." + id;
                fragmentMap.putIfAbsent(qualifiedId, content);
                log.debug("从注解缓存 SQL 片段: interface={}, id={}", repoInterface.getName(), qualifiedId);
                loadedCount++;
            }
        }

        if (loadedCount > 0) {
            log.debug("从注解加载了 {} 个 SQL 片段: interface={}", loadedCount, repoInterface.getName());
        }
        return loadedCount > 0;
    }

    /**
     * 优先加载文件片段
     * @return 是否加载到片段
     */
    private boolean loadFileFragmentsWithFallback(String interfaceName, Map<String, String> fragmentMap) {
        try {
            String packagePath = interfaceName.replace('.', '/');
            String fragmentPath = String.format("classpath:sql/%s/sqlFragment/*.sql", packagePath);
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(fragmentPath);

            int loadedCount = 0;
            for (Resource resource : resources) {
                if (loadFragmentFromResource(interfaceName, resource, fragmentMap)) {
                    loadedCount++;
                }
            }

            if (loadedCount > 0) {
                log.debug("从文件加载了 {} 个 SQL 片段: interface={}", loadedCount, interfaceName);
            }
            return loadedCount > 0;
        } catch (IOException e) {
            log.debug("无法从类路径加载 SQL 片段，接口 {}: {}", interfaceName, e.getMessage());
            return false;
        }
    }

    /**
     * 加载注解片段（排除已存在的片段）
     */
    private boolean loadAnnotationFragmentsWithExclusion(Class<?> repoInterface, Map<String, String> fragmentMap) {
        SqlFragment[] fragments = repoInterface.getAnnotationsByType(SqlFragment.class);
        int loadedCount = 0;

        for (SqlFragment fragment : fragments) {
            String id = fragment.id();
            String content = fragment.value().trim();
            if (StringUtils.hasText(id) && StringUtils.hasText(content)) {
                String qualifiedId = repoInterface.getName() + "." + id;
                if (fragmentMap.putIfAbsent(qualifiedId, content) == null) {
                    loadedCount++;
                    log.debug("从注解补充缓存 SQL 片段: interface={}, id={}", repoInterface.getName(), qualifiedId);
                } else {
                    log.debug("跳过已存在的注解片段（文件优先）: {}", qualifiedId);
                }
            }
        }

        if (loadedCount > 0) {
            log.debug("从注解补充加载了 {} 个 SQL 片段: interface={}", loadedCount, repoInterface.getName());
        }
        return loadedCount > 0;
    }

    /**
     * 加载文件片段（排除已存在的片段）
     */
    private boolean loadFileFragmentsWithExclusion(String interfaceName, Map<String, String> fragmentMap) {
        try {
            String packagePath = interfaceName.replace('.', '/');
            String fragmentPath = String.format("classpath:sql/%s/sqlFragment/*.sql", packagePath);
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(fragmentPath);

            int loadedCount = 0;
            for (Resource resource : resources) {
                if (loadFragmentFromResourceWithExclusion(interfaceName, resource, fragmentMap)) {
                    loadedCount++;
                }
            }

            if (loadedCount > 0) {
                log.debug("从文件补充加载了 {} 个 SQL 片段: interface={}", loadedCount, interfaceName);
            }
            return loadedCount > 0;
        } catch (IOException e) {
            log.debug("无法从类路径加载 SQL 片段，接口 {}: {}", interfaceName, e.getMessage());
            return false;
        }
    }

    /**
     * 从资源加载片段（排除已存在的片段）
     */
    private boolean loadFragmentFromResourceWithExclusion(String interfaceName, Resource resource, Map<String, String> fragmentMap) {
        String fileName = resource.getFilename();
        if (fileName != null && fileName.endsWith(".sql")) {
            String fragmentId = fileName.substring(0, fileName.lastIndexOf(".sql"));
            String qualifiedId = interfaceName + "." + fragmentId;

            if (fragmentMap.containsKey(qualifiedId)) {
                log.debug("跳过已存在的文件片段（注解优先）: {}", qualifiedId);
                return false;
            }

            return loadFragmentFromResource(interfaceName, resource, fragmentMap, qualifiedId, fragmentId);
        }
        return false;
    }

    /**
     * 从资源加载片段（核心实现）
     */
    private boolean loadFragmentFromResource(String interfaceName, Resource resource, Map<String, String> fragmentMap) {
        String fileName = resource.getFilename();
        if (fileName != null && fileName.endsWith(".sql")) {
            String fragmentId = fileName.substring(0, fileName.lastIndexOf(".sql"));
            String qualifiedId = interfaceName + "." + fragmentId;
            return loadFragmentFromResource(interfaceName, resource, fragmentMap, qualifiedId, fragmentId);
        }
        return false;
    }

    /**
     * 从资源加载片段（核心实现）
     */
    private boolean loadFragmentFromResource(String interfaceName, Resource resource, Map<String, String> fragmentMap,
                                             String qualifiedId, String fragmentId) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n")).trim();
            if (StringUtils.hasText(content)) {
                fragmentMap.putIfAbsent(qualifiedId, content);
                log.debug("从文件缓存 SQL 片段: interface={}, id={}, file={}",
                        interfaceName, qualifiedId, resource.getFilename());
                return true;
            }
        } catch (IOException e) {
            log.warn("读取 SQL 片段文件失败: {}", resource.getFilename(), e);
        }
        return false;
    }
}