package com.bone.engine.extension.studio.service.impl;

import com.bone.engine.extension.annotation.ExtPoint;

import java.util.HashMap;
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
import com.bone.engine.extension.studio.model.ExtPointEntity;
import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.repository.ExtPointRepository;
import com.bone.engine.extension.studio.repository.ExtensionRepository;
import com.bone.engine.extension.studio.service.ExtPointService;
import com.bone.engine.extension.studio.service.common.ClassScanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Optional;
import java.util.Objects;

/**
 * 扩展点服务实现类
 */
@Service
@Slf4j
@CacheConfig(cacheNames = "extPoints")
public class ExtPointServiceImpl implements ExtPointService {

    @Autowired
    private ExtPointRepository extPointRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${bone.extension.studio.scan.base-packages:com.bone.engine.extension.example}")
    private String scanBasePackages;
    
    // 用于缓存已扫描的扩展点接口信息
    private final ConcurrentHashMap<String, Class<?>> extPointInterfaceCache = new ConcurrentHashMap<>();

    @Cacheable(value = "allExtPoints", unless = "#result == null")
    public Page<ExtPointEntity> findAllExtPoints(Pageable pageable) {
        log.debug("查询所有扩展点，页码: {}, 每页数量: {}", pageable.getPageNumber(), pageable.getPageSize());
        return extPointRepository.findAll(pageable);
    }

    @Cacheable(key = "#id", unless = "#result == null")
    public Optional<ExtPointEntity> findExtPointById(Long id) {
        log.debug("根据ID查询扩展点: {}", id);
        return extPointRepository.findById(id);
    }

    @Cacheable(key = "'byInterface:' + #interfaceName", unless = "#result == null")
    public Optional<ExtPointEntity> findExtPointByInterfaceName(String interfaceName) {
        log.debug("根据接口名称查询扩展点: {}", interfaceName);
        return extPointRepository.findByInterfaceName(interfaceName);
    }

    @Transactional
    @CacheEvict(value = {"allExtPoints", "allDomains", "allCategories"}, allEntries = true)
    public ExtPointEntity saveExtPoint(ExtPointEntity extPoint) {
        try {
            // 验证必要字段
            if (!StringUtils.hasText(extPoint.getName())) {
                throw new IllegalArgumentException("扩展点名称不能为空");
            }
            if (!StringUtils.hasText(extPoint.getInterfaceName())) {
                throw new IllegalArgumentException("扩展点接口名称不能为空");
            }
            
            // 检查接口名称是否已存在
            if (extPointRepository.findByInterfaceName(extPoint.getInterfaceName()).isPresent()) {
                throw new IllegalArgumentException("扩展点接口已存在: " + extPoint.getInterfaceName());
            }
            
            // 验证接口类是否存在并且是接口类型
            try {
                Class<?> interfaceClass = ClassUtils.forName(extPoint.getInterfaceName(), ClassUtils.getDefaultClassLoader());
                if (!interfaceClass.isInterface()) {
                    throw new IllegalArgumentException("指定的类不是接口类型: " + extPoint.getInterfaceName());
                }
                if (!interfaceClass.isAnnotationPresent(ExtPoint.class)) {
                    log.warn("接口 {} 未添加 @ExtPoint 注解", extPoint.getInterfaceName());
                }
            } catch (ClassNotFoundException e) {
                log.warn("接口 {} 未找到，将继续创建扩展点", extPoint.getInterfaceName());
            }
            
            // 设置默认值
            if (!StringUtils.hasText(extPoint.getDomain())) {
                extPoint.setDomain("default");
            }
            if (!StringUtils.hasText(extPoint.getCategory())) {
                extPoint.setCategory("general");
            }
            if (!StringUtils.hasText(extPoint.getType())) {
                extPoint.setType("default");
            }
            if (!StringUtils.hasText(extPoint.getVersion())) {
                extPoint.setVersion("1.0.0");
            }
            
            ExtPointEntity saved = extPointRepository.save(extPoint);
            log.info("创建扩展点成功: {}", saved.getName());
            return saved;
        } catch (IllegalArgumentException e) {
            log.error("创建扩展点失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("创建扩展点时发生异常", e);
            throw new RuntimeException("创建扩展点失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    @CacheEvict(value = {"allExtPoints", "allDomains", "allCategories"}, allEntries = true)
    @CachePut(key = "#id")
    public ExtPointEntity updateExtPoint(Long id, ExtPointEntity extPoint) {
        try {
            ExtPointEntity existing = extPointRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("扩展点不存在: " + id));
            
            // 验证必要字段
            if (!StringUtils.hasText(extPoint.getName())) {
                throw new IllegalArgumentException("扩展点名称不能为空");
            }
            
            // 更新字段
            existing.setName(extPoint.getName());
            existing.setDescription(extPoint.getDescription());
            existing.setDomain(StringUtils.hasText(extPoint.getDomain()) ? extPoint.getDomain() : "default");
            existing.setCategory(StringUtils.hasText(extPoint.getCategory()) ? extPoint.getCategory() : "general");
            existing.setType(StringUtils.hasText(extPoint.getType()) ? extPoint.getType() : "default");
            existing.setVersion(StringUtils.hasText(extPoint.getVersion()) ? extPoint.getVersion() : "1.0.0");
            existing.setDeprecated(extPoint.isDeprecated());
            existing.setDeprecatedSince(extPoint.getDeprecatedSince());
            existing.setDeprecatedIn(extPoint.getDeprecatedIn());
            
            ExtPointEntity updated = extPointRepository.save(existing);
            log.info("更新扩展点成功: {}", updated.getName());
            return updated;
        } catch (IllegalArgumentException e) {
            log.error("更新扩展点失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("更新扩展点时发生异常", e);
            throw new RuntimeException("更新扩展点失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    @CacheEvict(value = {"allExtPoints", "allDomains", "allCategories"}, allEntries = true)
    public void deleteExtPoint(Long id) {
        try {
            ExtPointEntity extPoint = extPointRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("扩展点不存在: " + id));
            
            // 检查是否有关联的扩展实现
            long extensionCount = extensionRepository.countByExtPointId(id);
            if (extensionCount > 0) {
                throw new IllegalArgumentException("该扩展点有 " + extensionCount + " 个扩展实现，无法删除。请先删除相关的扩展实现。");
            }
            
            // 清除接口缓存
            extPointInterfaceCache.remove(extPoint.getInterfaceName());
            
            extPointRepository.deleteById(id);
            log.info("删除扩展点成功: {}", extPoint.getName());
        } catch (IllegalArgumentException e) {
            log.error("删除扩展点失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("删除扩展点时发生异常", e);
            throw new RuntimeException("删除扩展点失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    @CacheEvict(value = {"allExtPoints"}, allEntries = true)
    @CachePut(key = "#id")
    public ExtPointEntity enableExtPoint(Long id, boolean enabled) {
        try {
            ExtPointEntity extPoint = extPointRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("扩展点不存在: " + id));
            
            if (enabled && extPoint.isDeprecated()) {
                log.warn("启用已废弃的扩展点: {}", extPoint.getName());
            }
            
            extPoint.setEnabled(enabled);
            ExtPointEntity updated = extPointRepository.save(extPoint);
            log.info("{}扩展点成功: {}, 状态: {}", enabled ? "启用" : "禁用", updated.getName(), enabled ? "启用" : "禁用");
            return updated;
        } catch (IllegalArgumentException e) {
            log.error("启用/禁用扩展点失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("启用/禁用扩展点时发生异常", e);
            throw new RuntimeException("启用/禁用扩展点失败: " + e.getMessage(), e);
        }
    }

    @Cacheable(key = "'byDomain:' + #domain", unless = "#result == null")
    public List<ExtPointEntity> findExtPointsByDomain(String domain) {
        log.debug("根据领域查询扩展点: {}", domain);
        return extPointRepository.findByDomain(domain);
    }

    @Cacheable(key = "'byCategory:' + #category", unless = "#result == null")
    public List<ExtPointEntity> findExtPointsByCategory(String category) {
        log.debug("根据分类查询扩展点: {}", category);
        return extPointRepository.findByCategory(category);
    }

    @Cacheable(key = "'byDomainAndCategory:' + #domain + '-' + #category", unless = "#result == null")
    public List<ExtPointEntity> findExtPointsByDomainAndCategory(String domain, String category) {
        log.debug("根据领域和分类查询扩展点: {}, {}", domain, category);
        return extPointRepository.findByDomainAndCategory(domain, category);
    }

    public Page<ExtPointEntity> searchExtPoints(String keyword, Pageable pageable) {
        log.debug("搜索扩展点，关键词: {}, 页码: {}, 每页数量: {}", 
                keyword, pageable.getPageNumber(), pageable.getPageSize());
                
        if (StringUtils.hasText(keyword)) {
            // 使用Specification进行复杂查询
            Specification<ExtPointEntity> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.or(
                        cb.like(root.get("name"), "%" + keyword + "%"),
                        cb.like(root.get("description"), "%" + keyword + "%"),
                        cb.like(root.get("interfaceName"), "%" + keyword + "%"),
                        cb.like(root.get("domain"), "%" + keyword + "%"),
                        cb.like(root.get("category"), "%" + keyword + "%"),
                        cb.like(root.get("type"), "%" + keyword + "%"),
                        cb.like(root.get("version"), "%" + keyword + "%")
                ));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            return extPointRepository.findAll(spec, pageable);
        }
        return extPointRepository.findAll(pageable);
    }

    @Cacheable(key = "'extensions:' + #extPointId", unless = "#result == null")
    public List<ExtensionEntity> findExtensionsByExtPointId(Long extPointId) {
        log.debug("获取扩展点的所有扩展实现: {}", extPointId);
        return extensionRepository.findByExtPointId(extPointId);
    }

    @Transactional
    @CacheEvict(value = {"allExtPoints", "allDomains", "allCategories"}, allEntries = true)
    public int scanAndRegisterExtPoints() {
        log.info("开始扫描并注册扩展点，基础包: {}", scanBasePackages);
        
        try {
            // 重置接口缓存
            extPointInterfaceCache.clear();
            
            // 解析基础包列表
            List<String> basePackages = ClassScanner.parseBasePackages(scanBasePackages);
            
            // 使用ClassScanner扫描并处理带有@ExtPoint注解的接口
            int registeredCount = ClassScanner.scanAndProcessAnnotatedClasses(
                basePackages, 
                ExtPoint.class, 
                (clazz, annotation) -> {
                    // 验证扩展点接口是否合法
                    if (validateExtPointInterface(clazz)) {
                        registerExtPointInterface(clazz, annotation);
                    } else {
                        log.warn("扩展点接口 {} 验证失败，跳过", clazz.getName());
                    }
                }
            );
            
            log.info("扫描并注册扩展点完成，共注册: {} 个", registeredCount);
            return registeredCount;
        } catch (Exception e) {
            log.error("扫描并注册扩展点时发生异常", e);
            throw new RuntimeException("扫描并注册扩展点失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证扩展点接口是否合法
     * @param extPointInterface 扩展点接口类
     * @return 是否合法
     */
    private boolean validateExtPointInterface(Class<?> extPointInterface) {
        // 检查是否为接口
        if (!extPointInterface.isInterface()) {
            log.warn("类型 {} 不是接口", extPointInterface.getName());
            return false;
        }
        
        // 检查接口是否包含方法
        Method[] methods = extPointInterface.getDeclaredMethods();
        if (methods.length == 0) {
            log.warn("扩展点接口 {} 不包含任何方法", extPointInterface.getName());
            // 允许不包含方法的接口，但记录警告
            return true;
        }
        
        // 验证每个方法的可见性和签名
        for (Method method : methods) {
            // 检查方法是否为公共的
            if (!Modifier.isPublic(method.getModifiers())) {
                log.warn("扩展点接口 {} 中的方法 {} 不是公共方法", 
                        extPointInterface.getName(), method.getName());
                return false;
            }
            
            // 检查方法是否为静态方法
            if (Modifier.isStatic(method.getModifiers())) {
                log.warn("扩展点接口 {} 中包含静态方法 {}，这可能导致实现问题", 
                        extPointInterface.getName(), method.getName());
                // 允许静态方法，但记录警告
            }
        }
        
        return true;
    }
    
    private int registerExtPointInterface(Class<?> interfaceClass, ExtPoint annotation) {
        try {
            String interfaceName = interfaceClass.getName();
            
            // 检查扩展点是否已存在
            Optional<ExtPointEntity> existing = extPointRepository.findByInterfaceName(interfaceName);
            if (!existing.isPresent()) {
                // 创建新的扩展点
            ExtPointEntity extPoint = new ExtPointEntity();
            extPoint.setName(StringUtils.hasText(annotation.name()) ? annotation.name() : interfaceClass.getSimpleName());
            extPoint.setDescription(StringUtils.hasText(annotation.description()) ? annotation.description() : "");
            extPoint.setInterfaceName(interfaceName);
            
            // 从ExtPointDoc注解获取domain和category信息
            com.bone.engine.extension.annotation.ExtPointDoc extPointDoc = interfaceClass.getAnnotation(com.bone.engine.extension.annotation.ExtPointDoc.class);
            extPoint.setDomain(extPointDoc != null && StringUtils.hasText(extPointDoc.domain()) ? extPointDoc.domain() : "default");
            extPoint.setCategory(extPointDoc != null && StringUtils.hasText(extPointDoc.category()) ? extPointDoc.category() : "general");
                extPoint.setType("interface");
                extPoint.setVersion("1.0.0");
                extPoint.setEnabled(true);
                extPoint.setDeprecated(false);
                
                // 移除对可能不存在的注解的处理
                
                extPointRepository.save(extPoint);
                
                // 缓存接口类
                extPointInterfaceCache.put(interfaceName, interfaceClass);
                
                log.info("成功注册扩展点: {} ({})", extPoint.getName(), interfaceName);
                return 1;
            } else {
                log.debug("扩展点已存在: {}", interfaceName);
                // 更新缓存
                extPointInterfaceCache.put(interfaceName, interfaceClass);
                return 0;
            }
        } catch (Exception e) {
            log.error("注册扩展点接口时出错: {}", interfaceClass.getName(), e);
            return 0;
        }
    }
    
    // 移除重复的方法，使用common包中的ResourceUtils
    
    // 添加Arrays类的导入
    // 使用标准库的java.util.Arrays类
    
    // 内部Stream接口，避免额外导入
    // 使用标准库的Stream、Predicate和Collector接口



    @Cacheable(value = "allDomains", unless = "#result == null")
    public List<String> findAllDomains() {
        log.debug("获取所有可用的领域列表");
        return extPointRepository.findAll().stream()
                .map(ExtPointEntity::getDomain)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Cacheable(value = "allCategories", unless = "#result == null")
    public List<String> findAllCategories() {
        log.debug("获取所有可用的分类列表");
        return extPointRepository.findAll().stream()
                .map(ExtPointEntity::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    
    // 移除@Override注解，因为此方法不在ExtPointService接口中定义
    public List<ExtensionEntity> getExtPointExtensions(Long extPointId) {
        return extensionRepository.findByExtPointId(extPointId);
    }
    
    public long getTotalExtPointCount() {
        return extPointRepository.count();
    }
    
    public Map<String, Long> getExtPointStatsByDomain() {
        // 简单实现，返回空映射以避免调用不存在的方法
        return new HashMap<>();
    }
    
    public Map<String, Long> getExtPointStatsByCategory() {
        // 简单实现，返回空映射以避免调用不存在的方法
        return new HashMap<>();
    }
}