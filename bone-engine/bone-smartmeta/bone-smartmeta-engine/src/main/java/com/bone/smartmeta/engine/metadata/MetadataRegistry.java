package com.bone.smartmeta.engine.metadata;

import com.bone.smartmeta.engine.metadata.processor.CompositeMetadataProcessor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 元数据注册中心，管理所有实体元数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataRegistry {

    private final CompositeMetadataProcessor metadataProcessor;
    
    // 实体元数据映射（API名称 -> 实体元数据）
    private final Map<String, EntityMetadata> entityMetadataMap = new ConcurrentHashMap<>();
    
    // 按业务域分组的实体元数据
    private final Map<String, List<EntityMetadata>> entitiesByDomain = new ConcurrentHashMap<>();
    
    // 工作流元数据映射
    private final Map<String, WorkflowMetadata> workflowMap = new ConcurrentHashMap<>();
    
    // 包定义映射
    private final Map<String, PackageDefinition> packageDefinitions = new ConcurrentHashMap<>();

    /**
     * 初始化：加载并注册所有元数据
     */
    @PostConstruct
    public void initialize() {
        log.info("开始初始化元数据注册中心...");
        
        // 处理并加载所有元数据
        List<EntityMetadata> allMetadata = metadataProcessor.processAllMetadata();
        
        // 注册实体元数据
        for (EntityMetadata metadata : allMetadata) {
            registerEntity(metadata);
        }
        
        log.info("元数据注册中心初始化完成，已注册实体数: {}", entityMetadataMap.size());
    }

    /**
     * 注册实体元数据
     */
    public void registerEntity(EntityMetadata metadata) {
        if (metadata == null || metadata.getApiName() == null) {
            log.warn("无法注册无效的实体元数据");
            return;
        }
        
        String apiName = metadata.getApiName();
        
        // 注册实体元数据
        entityMetadataMap.put(apiName, metadata);
        log.info("注册实体元数据: {} ({}) - 字段数: {}", 
                metadata.getApiName(), metadata.getLabel(), metadata.getFields().size());
        
        // 更新按域分组的映射
        String domain = metadata.getDomain();
        entitiesByDomain.computeIfAbsent(domain, k -> new ArrayList<>()).add(metadata);
    }

    /**
     * 获取实体元数据
     */
    public EntityMetadata getEntityMetadata(String apiName) {
        EntityMetadata metadata = entityMetadataMap.get(apiName);
        if (metadata == null) {
            throw new MetadataNotFoundException("实体元数据未找到: " + apiName);
        }
        return metadata;
    }

    /**
     * 获取所有实体元数据
     */
    public List<EntityMetadata> getAllEntityMetadata() {
        return new ArrayList<>(entityMetadataMap.values());
    }

    /**
     * 按业务域获取实体元数据
     */
    public List<EntityMetadata> getEntitiesByDomain(String domain) {
        return entitiesByDomain.getOrDefault(domain, Collections.emptyList());
    }

    /**
     * 获取所有业务域
     */
    public Set<String> getAllDomains() {
        return entitiesByDomain.keySet();
    }

    /**
     * 注册工作流元数据
     */
    public void registerWorkflow(WorkflowMetadata workflow) {
        if (workflow == null || workflow.getName() == null) {
            log.warn("无法注册无效的工作流元数据");
            return;
        }
        
        workflowMap.put(workflow.getName(), workflow);
        log.info("注册工作流: {} - {}", workflow.getName(), workflow.getLabel());
    }

    /**
     * 获取工作流元数据
     */
    public WorkflowMetadata getWorkflow(String name) {
        WorkflowMetadata workflow = workflowMap.get(name);
        if (workflow == null) {
            throw new WorkflowNotFoundException("工作流未找到: " + name);
        }
        return workflow;
    }

    /**
     * 获取所有工作流元数据
     */
    public List<WorkflowMetadata> getAllWorkflows() {
        return new ArrayList<>(workflowMap.values());
    }

    /**
     * 注册包定义
     */
    public void registerPackage(PackageDefinition packageDefinition) {
        if (packageDefinition == null || packageDefinition.getPackageName() == null) {
            log.warn("无法注册无效的包定义");
            return;
        }
        
        packageDefinitions.put(packageDefinition.getPackageName(), packageDefinition);
        log.info("注册包定义: {} v{}", packageDefinition.getPackageName(), packageDefinition.getVersion());
    }

    /**
     * 获取包定义
     */
    public PackageDefinition getPackage(String packageName) {
        PackageDefinition packageDefinition = packageDefinitions.get(packageName);
        if (packageDefinition == null) {
            throw new PackageNotFoundException("包定义未找到: " + packageName);
        }
        return packageDefinition;
    }

    /**
     * 按实体查找所属包
     */
    public PackageDefinition findPackageForEntity(String entityApiName) {
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        String packageName = entityMetadata.getPackageName();
        
        if (packageName != null && packageDefinitions.containsKey(packageName)) {
            return packageDefinitions.get(packageName);
        }
        
        // 如果未找到，尝试通过名称匹配查找
        return packageDefinitions.values().stream()
                .filter(pkg -> pkg.getEntities().contains(entityApiName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 刷新元数据
     */
    public void refreshMetadata() {
        log.info("开始刷新元数据...");
        
        // 清空现有元数据
        entityMetadataMap.clear();
        entitiesByDomain.clear();
        
        // 重新加载所有元数据
        List<EntityMetadata> allMetadata = metadataProcessor.processAllMetadata();
        
        // 重新注册实体元数据
        for (EntityMetadata metadata : allMetadata) {
            registerEntity(metadata);
        }
        
        log.info("元数据刷新完成，当前实体数: {}", entityMetadataMap.size());
    }
}
