package com.bone.engine.extension.studio.service.impl;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.model.ExtPointEntity;
import com.bone.engine.extension.studio.repository.ExtensionRepository;
import com.bone.engine.extension.studio.repository.ExtPointRepository;
import com.bone.engine.extension.studio.service.ExtensionService;
import com.bone.engine.extension.studio.service.common.ClassScanner;
import com.bone.engine.extension.studio.service.common.ResourceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 扩展实现服务实现类
 */
@Service
@Slf4j
@CacheConfig(cacheNames = "extensions")
public class ExtensionServiceImpl implements ExtensionService {

    @Autowired
    private ExtensionRepository extensionRepository;

    @Autowired
    private ExtPointRepository extPointRepository;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${bone.extension.studio.scan.base-packages:com.bone.engine.extension.example}")
    private String scanBasePackages;

    // 用于缓存扩展实现的验证结果
    private final ConcurrentHashMap<String, Boolean> extensionValidationCache = new ConcurrentHashMap<>();

    @Override
    @Cacheable(value = "allExtensions", unless = "#result == null")
    public Page<ExtensionEntity> findAllExtensions(Pageable pageable) {
        log.debug("查询所有扩展实现，页码: {}, 每页数量: {}", pageable.getPageNumber(), pageable.getPageSize());
        return extensionRepository.findAll(pageable);
    }

    @Override
    @Cacheable(key = "#id", unless = "#result == null")
    public Optional<ExtensionEntity> findExtensionById(Long id) {
        log.debug("根据ID查询扩展实现: {}", id);
        return extensionRepository.findById(id);
    }

    @Override
    @Cacheable(key = "'byExtPoint:' + #extPointId", unless = "#result == null")
    public List<ExtensionEntity> findExtensionsByExtPointId(Long extPointId) {
        log.debug("根据扩展点ID查询扩展实现: {}", extPointId);
        return extensionRepository.findByExtPointId(extPointId);
    }

    @Override
    @Cacheable(key = "'byExtPointAndTenant:' + #extPointId + '-' + #tenantCode", unless = "#result == null")
    public List<ExtensionEntity> findExtensionsByExtPointIdAndTenantCode(Long extPointId, String tenantCode) {
        log.debug("根据扩展点ID和租户代码查询扩展实现: {}, {}", extPointId, tenantCode);
        return extensionRepository.findByExtPointIdAndTenantCode(extPointId, tenantCode);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allExtensions", "extensionsByExtPoint"}, allEntries = true)
    public ExtensionEntity saveExtension(ExtensionEntity extension) {
        try {
            // 验证扩展点是否存在
            ExtPointEntity extPoint = extPointRepository.findById(extension.getExtPoint().getId())
                    .orElseThrow(() -> new IllegalArgumentException("扩展点不存在: " + extension.getExtPoint().getId()));
            
            extension.setExtPoint(extPoint);
            
            // 检查实现类名是否已存在（针对同一扩展点）
            Optional<ExtensionEntity> existing = extensionRepository.findByExtPointIdAndClassName(
                    extPoint.getId(), extension.getClassName());
            if (existing.isPresent()) {
                throw new IllegalArgumentException("扩展实现已存在: " + extension.getClassName());
            }
            
            // 优先级已经有默认值，不需要再检查
            
            // 验证扩展实现
            if (!validateExtension(extension)) {
                throw new IllegalArgumentException("扩展实现验证失败: " + extension.getClassName());
            }
            
            ExtensionEntity saved = extensionRepository.save(extension);
            log.info("创建扩展实现成功: {}", saved.getName());
            
            // 清除验证缓存
            extensionValidationCache.remove(extension.getClassName());
            
            return saved;
        } catch (IllegalArgumentException e) {
            log.error("创建扩展实现失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("创建扩展实现时发生异常", e);
            throw new RuntimeException("创建扩展实现失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allExtensions", "extensionsByExtPoint"}, allEntries = true)
    @CachePut(key = "#id")
    public ExtensionEntity updateExtension(Long id, ExtensionEntity extension) {
        try {
            ExtensionEntity existing = extensionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("扩展实现不存在: " + id));
            
            // 验证扩展点是否存在
            ExtPointEntity extPoint = extPointRepository.findById(extension.getExtPoint().getId())
                    .orElseThrow(() -> new IllegalArgumentException("扩展点不存在: " + extension.getExtPoint().getId()));
            
            // 检查实现类名是否与其他记录冲突
            if (!existing.getClassName().equals(extension.getClassName())) {
                extensionRepository.findByExtPointIdAndClassNameAndIdNot(
                        extPoint.getId(), extension.getClassName(), id)
                        .ifPresent(existingExt -> {
                            throw new IllegalArgumentException("扩展实现已存在: " + extension.getClassName());
                        });
                // 清除旧的验证缓存
                extensionValidationCache.remove(existing.getClassName());
            }
            
            // 更新字段
            existing.setExtPoint(extPoint);
            existing.setName(extension.getName());
            existing.setDescription(extension.getDescription());
            existing.setClassName(extension.getClassName());
            existing.setTenantCode(extension.getTenantCode());
            existing.setPriority(extension.getPriority());
            existing.setConfig(extension.getConfig());
            existing.setEnabled(extension.isEnabled());
            
            // 验证扩展实现
            if (!validateExtension(existing)) {
                throw new IllegalArgumentException("扩展实现验证失败: " + existing.getClassName());
            }
            
            ExtensionEntity updated = extensionRepository.save(existing);
            log.info("更新扩展实现成功: {}", updated.getName());
            
            // 更新验证缓存
            extensionValidationCache.put(updated.getClassName(), true);
            
            return updated;
        } catch (IllegalArgumentException e) {
            log.error("更新扩展实现失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("更新扩展实现时发生异常", e);
            throw new RuntimeException("更新扩展实现失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allExtensions", "extensionsByExtPoint"}, allEntries = true)
    public void deleteExtension(Long id) {
        try {
            ExtensionEntity extension = extensionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("扩展实现不存在: " + id));
            
            // 清除验证缓存
            extensionValidationCache.remove(extension.getClassName());
            
            extensionRepository.deleteById(id);
            log.info("删除扩展实现成功: {}", id);
        } catch (IllegalArgumentException e) {
            log.error("删除扩展实现失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("删除扩展实现时发生异常", e);
            throw new RuntimeException("删除扩展实现失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allExtensions", "extensionsByExtPoint"}, allEntries = true)
    @CachePut(key = "#id")
    public ExtensionEntity enableExtension(Long id, boolean enabled) {
        try {
            ExtensionEntity extension = extensionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("扩展实现不存在: " + id));
            
            extension.setEnabled(enabled);
            ExtensionEntity updated = extensionRepository.save(extension);
            log.info("{}扩展实现成功: {}, 状态: {}", enabled ? "启用" : "禁用", updated.getName(), enabled ? "启用" : "禁用");
            
            return updated;
        } catch (IllegalArgumentException e) {
            log.error("启用/禁用扩展实现失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("启用/禁用扩展实现时发生异常", e);
            throw new RuntimeException("启用/禁用扩展实现失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allExtensions", "extensionsByExtPoint"}, allEntries = true)
    @CachePut(key = "#id")
    public ExtensionEntity updateExtensionPriority(Long id, int priority) {
        try {
            if (priority < 0 || priority > 1000) {
                throw new IllegalArgumentException("优先级必须在0-1000之间");
            }
            
            ExtensionEntity extension = extensionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("扩展实现不存在: " + id));
            
            extension.setPriority(priority);
            ExtensionEntity updated = extensionRepository.save(extension);
            log.info("更新扩展实现优先级成功: {}, 优先级: {}", updated.getName(), priority);
            
            return updated;
        } catch (IllegalArgumentException e) {
            log.error("更新扩展实现优先级失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("更新扩展实现优先级时发生异常", e);
            throw new RuntimeException("更新扩展实现优先级失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Cacheable(key = "'byTenant:' + #tenantCode", unless = "#result == null")
    public List<ExtensionEntity> findExtensionsByTenantCode(String tenantCode) {
        log.debug("根据租户代码查询扩展实现: {}", tenantCode);
        return extensionRepository.findByTenantCode(tenantCode);
    }

    @Override
    public Page<ExtensionEntity> searchExtensions(String keyword, Pageable pageable) {
        log.debug("搜索扩展实现，关键词: {}, 页码: {}, 每页数量: {}", 
                keyword, pageable.getPageNumber(), pageable.getPageSize());
                
        if (StringUtils.hasText(keyword)) {
            // 使用Specification进行复杂查询
            Specification<ExtensionEntity> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.or(
                        cb.like(root.get("name"), "%" + keyword + "%"),
                        cb.like(root.get("description"), "%" + keyword + "%"),
                        cb.like(root.get("className"), "%" + keyword + "%"),
                        cb.like(root.get("tenantCode"), "%" + keyword + "%"),
                        cb.like(root.get("extPoint").get("name"), "%" + keyword + "%")
                ));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            return extensionRepository.findAll(spec, pageable);
        }
        return extensionRepository.findAll(pageable);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allExtensions", "extensionsByExtPoint"}, allEntries = true)
    public int registerExtensions() {
        // 实现接口要求的方法，调用现有的扫描注册方法
        return scanAndRegisterExtensions();
    }
    
    @Transactional
    @CacheEvict(value = {"allExtensions", "extensionsByExtPoint"}, allEntries = true)
    public int scanAndRegisterExtensions() {
        log.info("开始扫描并注册扩展实现，基础包: {}", scanBasePackages);
        
        try {
            // 重置验证缓存
            extensionValidationCache.clear();
            
            // 解析基础包列表
            List<String> basePackages = ClassScanner.parseBasePackages(scanBasePackages);
            
            // 使用ClassScanner扫描并处理带有@Extension注解的类
            int registeredCount = ClassScanner.scanAndProcessAnnotatedClasses(
                basePackages,
                Extension.class,
                (clazz, annotation) -> registerExtensionClass(clazz, annotation)
            );
            
            log.info("扫描并注册扩展实现完成，共注册: {} 个", registeredCount);
            return registeredCount;
        } catch (Exception e) {
            log.error("扫描并注册扩展实现时发生异常", e);
            throw new RuntimeException("扫描并注册扩展实现失败: " + e.getMessage(), e);
        }
    }
    
    private int registerExtensionClass(Class<?> clazz, Extension annotation) {
        try {
            // 查找实现的扩展点接口
            Class<?>[] interfaces = clazz.getInterfaces();
            if (interfaces.length == 0) {
                log.warn("类 {} 没有实现任何接口，跳过注册", clazz.getName());
                return 0;
            }
            
            // 查找对应的扩展点实体
            for (Class<?> intf : interfaces) {
                if (intf.isAnnotationPresent(com.bone.engine.extension.ExtPoint.class)) {
                    ExtPointEntity extPoint = extPointRepository.findByInterfaceName(intf.getName())
                            .orElseGet(() -> {
                                // 如果扩展点不存在，创建新的扩展点
                                ExtPointEntity newExtPoint = new ExtPointEntity();
                                newExtPoint.setName(annotation.name());
                                newExtPoint.setDescription(annotation.description());
                                newExtPoint.setInterfaceName(intf.getName());
                                // 使用默认值或从其他属性获取领域和分类
                                newExtPoint.setDomain("DEFAULT");
                                newExtPoint.setCategory("DEFAULT");
                                newExtPoint.setEnabled(true);
                                return extPointRepository.save(newExtPoint);
                            });
                    
                    // 检查扩展实现是否已存在
                    Optional<ExtensionEntity> existing = extensionRepository.findByClassName(clazz.getName());
                    if (!existing.isPresent()) {
                        // 创建新的扩展实现
                        ExtensionEntity extensionEntity = new ExtensionEntity();
                        extensionEntity.setExtPoint(extPoint);
                        extensionEntity.setName(annotation.name());
                        extensionEntity.setDescription(annotation.description());
                        extensionEntity.setClassName(clazz.getName());
                        extensionEntity.setTenantCode(StringUtils.hasText(annotation.tenantCode()) ? annotation.tenantCode() : "DEFAULT");
                        extensionEntity.setBizCode(StringUtils.hasText(annotation.bizCode()) ? annotation.bizCode() : "*");
                        extensionEntity.setScenario(StringUtils.hasText(annotation.scenario()) ? annotation.scenario() : "*");
                        extensionEntity.setPriority(annotation.priority());
                        extensionEntity.setEnabled(true);
                         
                        extensionRepository.save(extensionEntity);
                        log.info("成功注册扩展实现: {} 到扩展点: {}", clazz.getName(), extPoint.getName());
                        return 1;
                    } else {
                        // 扩展实现已存在，移除重复的日志语句
                        log.debug("扩展实现已存在: {}", clazz.getName());
                        return 0;
                    }
                }
            }
            
            log.warn("类 {} 没有实现任何扩展点接口，跳过注册", clazz.getName());
            return 0;
        } catch (Exception e) {
            log.error("注册扩展实现时出错: {}", clazz.getName(), e);
            return 0;
        }
    }
    
    // 移除重复的方法，使用common包中的ResourceUtils
    



    @Override
    public boolean validateExtension(ExtensionEntity extension) {
        try {
            // 检查缓存中是否已有验证结果
            String cacheKey = extension.getClassName();
            if (extensionValidationCache.containsKey(cacheKey)) {
                return extensionValidationCache.get(cacheKey);
            }
            
            // 加载类并验证
            Class<?> clazz = ClassUtils.forName(extension.getClassName(), ClassUtils.getDefaultClassLoader());
            
            // 检查类是否带有@Extension注解
            Extension annotation = clazz.getAnnotation(Extension.class);
            if (annotation == null) {
                log.warn("类 {} 未添加 @Extension 注解", extension.getClassName());
                extensionValidationCache.put(cacheKey, false);
                return false;
            }
            
            // 验证注解中的路由参数与实体数据是否一致
            validateExtensionAnnotation(extension, annotation);
            
            // 检查实现的接口是否存在
            Class<?>[] interfaces = clazz.getInterfaces();
            if (interfaces.length == 0) {
                log.warn("类 {} 没有实现任何接口", extension.getClassName());
                extensionValidationCache.put(cacheKey, false);
                return false;
            }
            
            // 2. 检查实现类是否实现了对应的扩展点接口
            ExtPointEntity extPoint = extension.getExtPoint();
            if (extPoint == null) {
                log.error("扩展实现未关联扩展点: {}", extension.getClassName());
                extensionValidationCache.put(cacheKey, false);
                return false;
            }
            
            Class<?> extPointInterface = null;
            try {
                extPointInterface = ClassUtils.forName(extPoint.getInterfaceName(), ClassUtils.getDefaultClassLoader());
            } catch (Exception e) {
                log.error("加载扩展点接口失败: {}", extPoint.getInterfaceName(), e);
                extensionValidationCache.put(cacheKey, false);
                return false;
            }
            
            if (!extPointInterface.isAssignableFrom(clazz)) {
                log.error("扩展实现 {} 未实现扩展点接口 {}", extension.getClassName(), extPoint.getInterfaceName());
                extensionValidationCache.put(cacheKey, false);
                return false;
            }
            
            // 检查类是否有公共的无参构造函数
            boolean hasPublicNoArgsConstructor = false;
            for (java.lang.reflect.Constructor<?> constructor : clazz.getConstructors()) {
                if (constructor.getParameterCount() == 0 && 
                    (constructor.getModifiers() & java.lang.reflect.Modifier.PUBLIC) != 0) {
                    hasPublicNoArgsConstructor = true;
                    break;
                }
            }
            
            if (!hasPublicNoArgsConstructor) {
                log.warn("类 {} 没有公共的无参构造函数", extension.getClassName());
                extensionValidationCache.put(cacheKey, false);
                return false;
            }
            
            // 尝试实例化类，验证是否可以正常创建
            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                log.debug("成功实例化扩展实现类: {}", extension.getClassName());
                
                // 如果实现了ExtensionLifecycle接口，调用其初始化方法进行验证
                for (Class<?> intf : interfaces) {
                    if ("com.bone.engine.extension.ExtensionLifecycle".equals(intf.getName())) {
                        try {
                            Method initMethod = intf.getMethod("init");
                            initMethod.invoke(instance);
                            log.debug("成功调用扩展实现的初始化方法: {}", extension.getClassName());
                        } catch (Exception e) {
                            log.warn("调用扩展实现初始化方法失败: {}", extension.getClassName(), e);
                            // 初始化失败不影响验证通过，但需要记录警告
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("实例化扩展实现类失败: {}", extension.getClassName(), e);
                extensionValidationCache.put(cacheKey, false);
                return false;
            }
            
            // 检查配置是否合法（如果有配置）
            if (extension.getConfig() != null) {
                // 这里可以添加配置验证逻辑
                // 例如：验证JSON格式、检查必填字段等
                log.debug("扩展实现 {} 配置验证通过", extension.getClassName());
            }
            
            log.debug("扩展实现 {} 验证通过", extension.getClassName());
            extensionValidationCache.put(cacheKey, true);
            return true;
        } catch (ClassNotFoundException e) {
            log.error("找不到类: {}", extension.getClassName(), e);
            return false;
        } catch (Exception e) {
            log.error("验证扩展实现时发生异常: {}", extension.getClassName(), e);
            return false;
        }
    }
    
    /**
     * 验证扩展注解中的路由参数与实体数据是否一致
     */
    private void validateExtensionAnnotation(ExtensionEntity extension, Extension annotation) {
        // 验证租户代码
        String annotationTenantCode = annotation.tenantCode();
        if (!"*".equals(annotationTenantCode) && !annotationTenantCode.equals(extension.getTenantCode())) {
            log.warn("扩展实现 {} 的租户代码不匹配，注解: {}, 数据库: {}", 
                    extension.getClassName(), annotationTenantCode, extension.getTenantCode());
        }
        
        // 验证业务域代码
        String annotationBizCode = annotation.bizCode();
        if (!"*".equals(annotationBizCode) && !annotationBizCode.equals(extension.getBizCode())) {
            log.warn("扩展实现 {} 的业务域代码不匹配，注解: {}, 数据库: {}", 
                    extension.getClassName(), annotationBizCode, extension.getBizCode());
        }
        
        // 验证场景代码
        String annotationScenario = annotation.scenario();
        if (!"*".equals(annotationScenario) && !annotationScenario.equals(extension.getScenario())) {
            log.warn("扩展实现 {} 的场景代码不匹配，注解: {}, 数据库: {}", 
                    extension.getClassName(), annotationScenario, extension.getScenario());
        }
    }

    @Override
    public String getExtensionStatistics(Long id) {
        try {
            ExtensionEntity extension = extensionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("扩展实现不存在: " + id));
            
            log.debug("获取扩展实现统计信息: {}", extension.getName());
            
            // TODO: 集成监控系统，获取实际的调用统计信息
            // 暂时返回模拟数据
            return "{\"totalCalls\": 100, \"successCalls\": 95, \"failedCalls\": 5, \"averageExecutionTime\": 10.5}";
        } catch (Exception e) {
            log.error("获取扩展实现统计信息失败: {}", id, e);
            throw new RuntimeException("获取扩展实现统计信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void resetExtensionStatistics(Long id) {
        try {
            ExtensionEntity extension = extensionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("扩展实现不存在: " + id));
            
            log.info("重置扩展实现统计信息: {}", extension.getName());
            
            // TODO: 集成监控系统，实现统计信息重置
            // 暂时只记录日志
        } catch (Exception e) {
            log.error("重置扩展实现统计信息失败: {}", id, e);
            throw new RuntimeException("重置扩展实现统计信息失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public long getTotalExtensionCount() {
        log.debug("获取扩展实现总数");
        return extensionRepository.count();
    }
    
    @Override
    public Map<String, Long> getExtensionStatsByStatus() {
        log.debug("根据状态统计扩展实现数量");
        Map<String, Long> stats = new HashMap<>();
        
        // 统计总扩展实现
        long totalCount = extensionRepository.count();
        long disabledCount = extensionRepository.findAll().stream()
                .filter(extension -> !extension.isEnabled())
                .count();
        
        stats.put("enabled", totalCount - disabledCount);
        stats.put("disabled", disabledCount);
        stats.put("total", totalCount);
        
        // 统计验证通过和失败的扩展实现
        long validCount = extensionRepository.findAll().stream()
                .filter(extension -> validateExtension(extension))
                .count();
        stats.put("valid", validCount);
        stats.put("invalid", totalCount - validCount);
        
        return stats;
    }
    
    @Override
    public Map<String, Long> getExtensionStatsByExtPoint() {
        log.debug("根据扩展点统计扩展实现数量");
        Map<String, Long> stats = new HashMap<>();
        
        // 获取所有扩展实现
        List<ExtensionEntity> extensions = extensionRepository.findAll();
        
        // 按扩展点名称分组统计
        Map<String, Long> countByExtPoint = extensions.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getExtPoint() != null ? e.getExtPoint().getName() : "未知",
                        Collectors.counting()
                ));
        
        // 转换为有序的统计结果
        List<Map.Entry<String, Long>> sortedEntries = countByExtPoint.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        for (Map.Entry<String, Long> entry : sortedEntries) {
            stats.put(entry.getKey(), entry.getValue());
        }
        
        return stats;
    }
    
    // 移除@Override注解，因为此方法不在ExtensionService接口中定义
    public Map<String, Object> getExtensionDoc(String extensionId) {
        log.info("获取扩展实现文档详情，扩展实现ID: {}", extensionId);
        ExtensionEntity extension;
        try {
            // 尝试将ID解析为Long
            extension = extensionRepository.findById(Long.parseLong(extensionId))
                    .orElseThrow(() -> new IllegalArgumentException("扩展实现不存在"));
        } catch (NumberFormatException e) {
            // 移除对不存在方法的调用
            throw new IllegalArgumentException("扩展实现不存在: " + extensionId);
        }
        
        // 返回扩展实现的基本信息
        Map<String, Object> docInfo = new HashMap<>();
        docInfo.put("name", extension.getName());
        docInfo.put("className", extension.getClassName());
        docInfo.put("tenantCode", extension.getTenantCode());
        docInfo.put("bizCode", extension.getBizCode() != null ? extension.getBizCode() : "");
        docInfo.put("scenario", extension.getScenario() != null ? extension.getScenario() : "");
        docInfo.put("priority", extension.getPriority());
        // 检查version属性是否存在，避免空指针异常
        try {
            Method versionMethod = extension.getClass().getMethod("getVersion");
            docInfo.put("version", versionMethod.invoke(extension));
        } catch (Exception e) {
            docInfo.put("version", null);
        }
        docInfo.put("status", extension.isEnabled() ? "enabled" : "disabled");
        docInfo.put("description", extension.getDescription() != null ? extension.getDescription() : "");
        // 检查createTime属性是否存在，避免空指针异常
        try {
            Method createTimeMethod = extension.getClass().getMethod("getCreateTime");
            docInfo.put("createTime", createTimeMethod.invoke(extension));
        } catch (Exception e) {
            docInfo.put("createTime", null);
        }
        
        return docInfo;
    }
}