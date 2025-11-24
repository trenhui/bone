package com.bone.engine.extension.api.model.definition;

import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展点核心定义 - 运行时路由决策模型
 *
 * @author renhui.trh
 * @since  2025-11-21
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionPointDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private String description = "";
    private String version = "1.0.0";
    private boolean transactional = false;
    private int timeout = 30;
    private boolean singleton = true;
    private Class<?> interfaceType;

    /**
     * 所有已注册的扩展实现（线程安全）
     * 使用 volatile + 双重检查锁定确保线程安全
     */
    private transient volatile Map<String, ExtensionDefinition> extensions;

    // ==================== 核心访问方法（完美兼容你的注册逻辑） ====================

    /**
     * 获取线程安全的 extensions Map（懒加载 + 双重检查锁定）
     */
    public Map<String, ExtensionDefinition> getExtensions() {
        Map<String, ExtensionDefinition> result = extensions;
        if (result == null) {
            synchronized (this) {
                result = extensions;
                if (result == null) {
                    result = new ConcurrentHashMap<>();
                    extensions = result;
                }
            }
        }
        return result;
    }

    // ==================== 其他安全访问方法（生产推荐） ====================

    public ExtensionDefinition addExtension(ExtensionDefinition extension) {
        return getExtensions().put(extension.getCode(), extension);
    }

    public ExtensionDefinition removeExtension(String code) {
        return getExtensions().remove(code);
    }

    public ExtensionDefinition getExtension(String code) {
        return getExtensions().get(code);
    }

    public Collection<ExtensionDefinition> getAllExtensions() {
        return Collections.unmodifiableCollection(getExtensions().values());
    }

    public Collection<ExtensionDefinition> getEnabledExtensions() {
        return getExtensions().values().stream()
                .filter(ExtensionDefinition::isEnabled)
                .toList();
    }

    public Map<String, ExtensionDefinition> getExtensionsView() {
        return Collections.unmodifiableMap(getExtensions());
    }

    public int getExtensionCount() {
        return getExtensions().size();
    }

    public boolean isEmpty() {
        return getExtensions().isEmpty();
    }

    // ==================== 兜底实现（无 primary 字段时代的最优方案） ====================

    public ExtensionDefinition getDefaultExtension() {
        return getExtensions().values().stream()
                .filter(ExtensionDefinition::isEnabled)
                .min(ExtensionDefinition::compareTo)
                .orElse(null);
    }

    @Override
    public String toString() {
        return String.format("ExtensionPointDefinition[code=%s, extensions=%d, singleton=%b]",
                code, getExtensionCount(), singleton);
    }
}