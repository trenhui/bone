package com.bone.engine.extension.version;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.event.ExtensionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 扩展点版本管理器
 * <p>
 * 负责管理扩展点的多版本实现，支持版本选择、切换和兼容性控制
 * 提供版本优先级策略、默认版本设置和版本兼容性检查
 *
 * @author renhui.trh
 */
public class ExtensionVersionManager implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ExtensionVersionManager.class);

    // 版本信息缓存，key: 扩展点类名 + ":" + 版本号, value: 扩展实现对象
    private final Map<String, Object> versionCache = new ConcurrentHashMap<>();
    
    // 版本元数据缓存，包含版本依赖关系和兼容性信息
    private final Map<String, VersionMetadata> versionMetadataCache = new ConcurrentHashMap<>();
    
    // 扩展点默认版本映射
    private final Map<String, String> defaultVersions = new ConcurrentHashMap<>();
    
    @Autowired(required = false)
    private ExtensionEventPublisher eventPublisher;

    /**
     * 注册扩展点的特定版本实现
     */
    public void registerVersionExtension(Class<? extends ExtPoint> extPointClass, String version, Object extension) {
        if (extPointClass == null || extension == null) {
            log.warn("Invalid arguments: extPointClass or extension is null");
            return;
        }

        String cacheKey = buildVersionCacheKey(extPointClass.getName(), version);
        versionCache.put(cacheKey, extension);
        
        // 解析版本元数据
        VersionMetadata metadata = parseVersionMetadata(extension);
        versionMetadataCache.put(cacheKey, metadata);
        
        log.debug("Registered version {} for extension point {}", version, extPointClass.getName());
        
        // 发布版本注册事件
        if (eventPublisher != null) {
            eventPublisher.publishVersionRegister(this, 
                                               extPointClass.getName(), 
                                               version, 
                                               extension.getClass().getName());
        }
    }

    /**
     * 获取指定版本的扩展实现
     */
    public Object getVersionExtension(Class<? extends ExtPoint> extPointClass, String version) {
        if (extPointClass == null) {
            return null;
        }

        // 如果版本为空，使用默认版本
        if (!StringUtils.hasText(version)) {
            version = getDefaultVersion(extPointClass.getName());
        }

        if (!StringUtils.hasText(version)) {
            return null;
        }

        String cacheKey = buildVersionCacheKey(extPointClass.getName(), version);
        return versionCache.get(cacheKey);
    }

    /**
     * 设置扩展点的默认版本
     */
    public void setDefaultVersion(Class<? extends ExtPoint> extPointClass, String version) {
        if (extPointClass == null || !StringUtils.hasText(version)) {
            log.warn("Invalid arguments for setting default version");
            return;
        }

        String extPointName = extPointClass.getName();
        defaultVersions.put(extPointName, version);
        log.info("Set default version {} for extension point {}", version, extPointName);
    }

    /**
     * 获取扩展点的默认版本
     */
    public String getDefaultVersion(String extPointClassName) {
        return defaultVersions.get(extPointClassName);
    }

    /**
     * 获取扩展点的所有可用版本
     */
    public List<String> getAllVersions(Class<? extends ExtPoint> extPointClass) {
        if (extPointClass == null) {
            return Collections.emptyList();
        }

        String prefix = extPointClass.getName() + ":";
        return versionCache.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> key.substring(prefix.length()))
                .sorted(new VersionComparator())
                .collect(Collectors.toList());
    }

    /**
     * 检查版本兼容性
     */
    public boolean isVersionCompatible(String sourceVersion, String targetVersion) {
        if (!StringUtils.hasText(sourceVersion) || !StringUtils.hasText(targetVersion)) {
            return false;
        }

        VersionComparator comparator = new VersionComparator();
        // 简化实现：假设新版本向后兼容
        return comparator.compare(sourceVersion, targetVersion) >= 0;
    }

    /**
     * 按照版本策略选择合适的扩展实现
     */
    public Object selectVersionByStrategy(Class<? extends ExtPoint> extPointClass, VersionSelectionStrategy strategy) {
        List<String> versions = getAllVersions(extPointClass);
        if (versions.isEmpty()) {
            return null;
        }

        String selectedVersion;
        switch (strategy) {
            case LATEST:
                selectedVersion = versions.get(versions.size() - 1);
                break;
            case OLDEST:
                selectedVersion = versions.get(0);
                break;
            case DEFAULT:
                selectedVersion = getDefaultVersion(extPointClass.getName());
                break;
            default:
                selectedVersion = getDefaultVersion(extPointClass.getName());
        }

        if (!StringUtils.hasText(selectedVersion) && !versions.isEmpty()) {
            selectedVersion = versions.get(0);
        }

        return getVersionExtension(extPointClass, selectedVersion);
    }

    /**
     * 清除特定扩展点的所有版本
     */
    public void clearVersions(Class<? extends ExtPoint> extPointClass) {
        if (extPointClass == null) {
            return;
        }

        String extPointName = extPointClass.getName();
        String prefix = extPointName + ":";
        
        // 清除版本缓存
        versionCache.keySet().removeIf(key -> key.startsWith(prefix));
        versionMetadataCache.keySet().removeIf(key -> key.startsWith(prefix));
        
        // 清除默认版本设置
        defaultVersions.remove(extPointName);
        
        log.info("Cleared all versions for extension point {}", extPointName);
    }

    /**
     * 解析扩展实现的版本元数据
     */
    private VersionMetadata parseVersionMetadata(Object extension) {
        VersionMetadata metadata = new VersionMetadata();
        
        // 解析Extension注解获取版本信息
        Extension extensionAnnotation = AnnotationUtils.findAnnotation(extension.getClass(), Extension.class);
        if (extensionAnnotation != null && StringUtils.hasText(extensionAnnotation.version())) {
            metadata.setVersion(extensionAnnotation.version());
            metadata.setCompatibleWith(extensionAnnotation.compatibleWith());
        }
        
        // 解析Version注解获取更详细的版本信息
        Version versionAnnotation = AnnotationUtils.findAnnotation(extension.getClass(), Version.class);
        if (versionAnnotation != null) {
            metadata.setVersion(versionAnnotation.value());
            metadata.setCompatibleWith(versionAnnotation.compatibleWith());
            metadata.setReleaseDate(versionAnnotation.releaseDate());
            metadata.setDeprecated(versionAnnotation.deprecated());
            metadata.setDeprecatedSince(versionAnnotation.deprecatedSince());
        }
        
        return metadata;
    }

    /**
     * 构建版本缓存键
     */
    private String buildVersionCacheKey(String extPointClassName, String version) {
        return extPointClassName + ":" + version;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Extension version manager initialized");
    }

    /**
     * 版本选择策略枚举
     */
    public enum VersionSelectionStrategy {
        LATEST,    // 最新版本
        OLDEST,    // 最早版本
        DEFAULT    // 默认版本
    }

    /**
     * 版本比较器，支持语义化版本比较
     */
    public static class VersionComparator implements Comparator<String> {
        @Override
        public int compare(String v1, String v2) {
            if (v1 == null && v2 == null) return 0;
            if (v1 == null) return -1;
            if (v2 == null) return 1;

            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");

            int minLength = Math.min(parts1.length, parts2.length);
            for (int i = 0; i < minLength; i++) {
                try {
                    int num1 = Integer.parseInt(parts1[i]);
                    int num2 = Integer.parseInt(parts2[i]);
                    if (num1 != num2) {
                        return Integer.compare(num1, num2);
                    }
                } catch (NumberFormatException e) {
                    // 非数字部分按字典序比较
                    int result = parts1[i].compareTo(parts2[i]);
                    if (result != 0) {
                        return result;
                    }
                }
            }

            // 长度不同时，更长的版本更大
            return Integer.compare(parts1.length, parts2.length);
        }
    }

    /**
     * 版本元数据类
     */
    public static class VersionMetadata {
        private String version = "1.0.0";
        private String[] compatibleWith = new String[0];
        private String releaseDate = "";
        private boolean deprecated = false;
        private String deprecatedSince = "";

        // Getters and Setters
        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String[] getCompatibleWith() {
            return compatibleWith;
        }

        public void setCompatibleWith(String[] compatibleWith) {
            this.compatibleWith = compatibleWith;
        }

        public String getReleaseDate() {
            return releaseDate;
        }

        public void setReleaseDate(String releaseDate) {
            this.releaseDate = releaseDate;
        }

        public boolean isDeprecated() {
            return deprecated;
        }

        public void setDeprecated(boolean deprecated) {
            this.deprecated = deprecated;
        }

        public String getDeprecatedSince() {
            return deprecatedSince;
        }

        public void setDeprecatedSince(String deprecatedSince) {
            this.deprecatedSince = deprecatedSince;
        }

        @Override
        public String toString() {
            return "VersionMetadata{" +
                    "version='" + version + '\'' +
                    ", compatibleWith=" + Arrays.toString(compatibleWith) +
                    ", releaseDate='" + releaseDate + '\'' +
                    ", deprecated=" + deprecated +
                    ", deprecatedSince='" + deprecatedSince + '\'' +
                    '}';
        }
    }
}