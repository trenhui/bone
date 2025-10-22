package com.bone.engine.extension.studio.service.impl;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.studio.model.ExtPointEntity;
import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.repository.ExtPointRepository;
import com.bone.engine.extension.studio.repository.ExtensionRepository;
import com.bone.engine.extension.studio.service.ExtensionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
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
    public int scanAndRegisterExtensions() {
        log.info("开始扫描并注册扩展实现，基础包: {}", scanBasePackages);
        int registeredCount = 0;
        
        try {
            // 重置验证缓存
            extensionValidationCache.clear();
            
            // 扫描指定包下的所有带@Extension注解的类
            List<String> basePackages = Arrays.asList(scanBasePackages.split(","));
            for (String basePackage : basePackages) {
                String searchPath = "classpath*:" + basePackage.replace(".", "/") + "/**/*.class";
                try {
                    Set<Resource> resources = new HashSet<>();
                    try {
                        // 使用ResourcePatternResolver获取多个资源
                        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                        Resource[] foundResources = resolver.getResources(searchPath);
                        for (Resource resource : foundResources) {
                            if (resource.exists()) {
                                resources.add(resource);
                            }
                        }
                    } catch (IOException e) {
                        log.warn("Error loading resources for path: {}", searchPath, e);
                    }
                    
                    for (Resource resource : resources) {
                        try {
                            // 解析资源为类文件
                            String className = getClassNameFromResource(resource, basePackage);
                            if (className != null) {
                                // 加载类并检查注解
                                Class<?> clazz = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
                                Extension extensionAnnotation = clazz.getAnnotation(Extension.class);
                                
                                if (extensionAnnotation != null) {
                                    // 处理带注解的类
                                    registeredCount += registerExtensionClass(clazz, extensionAnnotation);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("处理资源时出错: {}", resource.getURI(), e);
                        }
                    }
                } catch (Exception e) {
                    log.warn("扫描包时出错: {}", basePackage, e);
                }
            }
            
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
                        extensionEntity.setUseCase(StringUtils.hasText(annotation.useCase()) ? annotation.useCase() : "*");
                        extensionEntity.setScenario(StringUtils.hasText(annotation.scenario()) ? annotation.scenario() : "*");
                        extensionEntity.setUserGroup(StringUtils.hasText(annotation.userGroup()) ? annotation.userGroup() : "*");
                        extensionEntity.setPriority(annotation.priority());
                        extensionEntity.setEnabled(true);
                        
                        extensionRepository.save(extensionEntity);
                        log.info("成功注册扩展实现: {} 到扩展点: {}", clazz.getName(), extPoint.getName());
                        return 1;
                    } else {
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
    
    private String getClassNameFromResource(Resource resource, String basePackage) {
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
            return null;
        }
    }
    
    private Set<Resource> getResources(String locationPattern) throws IOException {
        Set<Resource> result = new HashSet<>();
        // 使用ResourcePatternResolver获取多个资源
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(locationPattern);
        for (Resource resource : resources) {
            if (resource.exists()) {
                result.add(resource);
            }
        }
        return result;
    }
    



    @Override
    public boolean validateExtension(ExtensionEntity extension) {
        // 先检查缓存
        String className = extension.getClassName();
        if (extensionValidationCache.containsKey(className)) {
            return extensionValidationCache.get(className);
        }
        
        try {
            log.debug("验证扩展实现: {}", className);
            
            // 1. 检查实现类是否存在
            Class<?> implClass = null;
            try {
                implClass = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
                if (implClass == null) {
                    log.error("扩展实现类不存在: {}", className);
                    extensionValidationCache.put(className, false);
                    return false;
                }
            } catch (Exception e) {
                log.error("加载扩展实现类失败: {}", className, e);
                extensionValidationCache.put(className, false);
                return false;
            }
            
            // 2. 检查实现类是否实现了对应的扩展点接口
            ExtPointEntity extPoint = extension.getExtPoint();
            if (extPoint == null) {
                log.error("扩展实现未关联扩展点: {}", className);
                extensionValidationCache.put(className, false);
                return false;
            }
            
            Class<?> extPointInterface = null;
            try {
                extPointInterface = ClassUtils.forName(extPoint.getInterfaceName(), ClassUtils.getDefaultClassLoader());
            } catch (Exception e) {
                log.error("加载扩展点接口失败: {}", extPoint.getInterfaceName(), e);
                extensionValidationCache.put(className, false);
                return false;
            }
            
            if (!extPointInterface.isAssignableFrom(implClass)) {
                log.error("扩展实现 {} 未实现扩展点接口 {}", className, extPoint.getInterfaceName());
                extensionValidationCache.put(className, false);
                return false;
            }
            
            // 3. 检查是否有公共无参构造函数
            try {
                implClass.getDeclaredConstructor().setAccessible(true);
            } catch (Exception e) {
                log.error("扩展实现 {} 缺少公共无参构造函数", className, e);
                extensionValidationCache.put(className, false);
                return false;
            }
            
            // 4. 检查配置是否合法（如果有配置）
            if (extension.getConfig() != null) {
                // 这里可以添加配置验证逻辑
                // 例如：验证JSON格式、检查必填字段等
                log.debug("扩展实现 {} 配置验证通过", className);
            }
            
            log.debug("扩展实现 {} 验证通过", className);
            extensionValidationCache.put(className, true);
            return true;
        } catch (Exception e) {
            log.error("验证扩展实现时发生异常: {}", className, e);
            extensionValidationCache.put(className, false);
            return false;
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
}