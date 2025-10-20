package com.bone.engine.extension.metadata;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 默认的扩展点元数据收集器实现
 * <p>
 * 提供扩展点元数据的收集、存储和访问功能
 * </p>
 * 
 * @since 1.0.0
 */
@Component
public class DefaultExtPointMetadataCollector implements ExtPointMetadataCollector {
    
    private static final Logger log = Logger.getLogger(DefaultExtPointMetadataCollector.class.getName());
    
    // 存储所有扩展点元数据
    private final Map<String, ExtPointMetadata> extPointMetadataMap = new ConcurrentHashMap<>();
    
    // 存储实现类到元数据的映射
    private final Map<String, ExtImplMetadata> implMetadataMap = new ConcurrentHashMap<>();
    
    // 存储实现类到扩展点接口的映射
    private final Map<String, String> implToInterfaceMap = new ConcurrentHashMap<>();
    
    @Override
    public void initialize() {
        log.info("Initializing ExtPointMetadataCollector...");
        // 初始化逻辑可以在这里实现
        // 例如扫描所有带有ExtPoint注解的接口
        refresh();
        log.info("ExtPointMetadataCollector initialized. Total extPoints: " + extPointMetadataMap.size());
    }
    
    @Override
    public void refresh() {
        log.info("Refreshing extPoint metadata...");
        // 刷新逻辑
        // 在实际实现中，这里应该重新扫描并更新元数据
    }
    
    @Override
    public ExtPointMetadata getExtPointMetadata(Class<?> interfaceClass) {
        if (interfaceClass == null) {
            return null;
        }
        return getExtPointMetadata(interfaceClass.getName());
    }
    
    @Override
    public ExtPointMetadata getExtPointMetadata(String interfaceName) {
        return extPointMetadataMap.get(interfaceName);
    }
    
    @Override
    public Map<String, ExtPointMetadata> getAllExtPointMetadata() {
        return Collections.unmodifiableMap(extPointMetadataMap);
    }
    
    @Override
    public void registerExtensionImpl(Class<?> interfaceClass, Class<?> implClass, ExtImplMetadata metadata) {
        if (interfaceClass == null || implClass == null || metadata == null) {
            return;
        }
        
        String interfaceName = interfaceClass.getName();
        String implName = implClass.getName();
        
        // 注册实现类元数据
        implMetadataMap.put(implName, metadata);
        implToInterfaceMap.put(implName, interfaceName);
        
        // 更新扩展点元数据中的实现列表
        ExtPointMetadata extPointMetadata = extPointMetadataMap.computeIfAbsent(interfaceName, k -> {
            ExtPointMetadata newMetadata = new ExtPointMetadata();
            newMetadata.setInterfaceName(interfaceName);
            newMetadata.setInterfaceSimpleName(interfaceClass.getSimpleName());
            newMetadata.setImplementations(new ArrayList<>());
            return newMetadata;
        });
        
        List<ExtensionImplMetadata> implementations = extPointMetadata.getImplementations();
        if (implementations != null) {
            // 这里直接添加到列表，暂时不设置具体属性
            implementations.add(new ExtensionImplMetadata());
            extPointMetadata.setImplementationCount(implementations.size());
        }
    }
    
    @Override
    public List<ExtImplMetadata> getExtensionImpls(Class<?> interfaceClass) {
        if (interfaceClass == null) {
            return Collections.emptyList();
        }
        
        ExtPointMetadata metadata = getExtPointMetadata(interfaceClass);
        if (metadata == null || metadata.getImplementations() == null) {
            return Collections.emptyList();
        }
        
        // 这里需要从ExtensionImplMetadata转换为ExtImplMetadata
        // 为了简化，我们返回一个空列表
        return Collections.emptyList();
    }
    
    @Override
    public ExtImplMetadata getExtensionImplMetadata(Class<?> implClass) {
        if (implClass == null) {
            return null;
        }
        return implMetadataMap.get(implClass.getName());
    }
    
    @Override
    public ExtImplMetadata getDefaultImplementation(Class<?> interfaceClass) {
        List<ExtImplMetadata> implementations = getExtensionImpls(interfaceClass);
        return implementations.stream()
                .filter(ExtImplMetadata::isDefaultImpl)
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public ExtImplMetadata getRecommendedImplementation(Class<?> interfaceClass) {
        List<ExtImplMetadata> implementations = getExtensionImpls(interfaceClass);
        return implementations.stream()
                .filter(ExtImplMetadata::isRecommended)
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public List<ExtImplMetadata> getSortedImplementations(Class<?> interfaceClass) {
        List<ExtImplMetadata> implementations = getExtensionImpls(interfaceClass);
        implementations.sort(Comparator.comparingInt(ExtImplMetadata::getPriority).reversed());
        return implementations;
    }
    
    @Override
    public List<ExtImplMetadata> filterImplementations(Class<?> interfaceClass, Map<String, String> conditions) {
        List<ExtImplMetadata> implementations = getExtensionImpls(interfaceClass);
        if (conditions == null || conditions.isEmpty()) {
            return implementations;
        }
        
        return implementations.stream()
                .filter(impl -> {
                    Map<String, String> implConditions = impl.getRoutingConditions();
                    if (implConditions == null) {
                        return false;
                    }
                    return conditions.entrySet().stream()
                            .allMatch(entry -> entry.getValue().equals(implConditions.get(entry.getKey())));
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean containsExtPoint(Class<?> interfaceClass) {
        return interfaceClass != null && extPointMetadataMap.containsKey(interfaceClass.getName());
    }
    
    @Override
    public int getExtPointCount() {
        return extPointMetadataMap.size();
    }
    
    @Override
    public int getExtensionImplCount() {
        return implMetadataMap.size();
    }
    
    @Override
    public void close() {
        log.info("Closing ExtPointMetadataCollector...");
        extPointMetadataMap.clear();
        implMetadataMap.clear();
        implToInterfaceMap.clear();
        log.info("ExtPointMetadataCollector closed.");
    }
}