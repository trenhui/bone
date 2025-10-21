package com.bone.engine.extension.studio.service.impl;

import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.repository.ExtPointRepository;
import com.bone.engine.extension.studio.repository.ExtensionRepository;
import com.bone.engine.extension.studio.service.ExtensionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 扩展实现服务实现类
 */
@Service
public class ExtensionServiceImpl implements ExtensionService {

    @Autowired
    private ExtensionRepository extensionRepository;

    @Autowired
    private ExtPointRepository extPointRepository;

    @Override
    public Page<ExtensionEntity> findAllExtensions(Pageable pageable) {
        return extensionRepository.findAll(pageable);
    }

    @Override
    public Optional<ExtensionEntity> findExtensionById(Long id) {
        return extensionRepository.findById(id);
    }

    @Override
    public List<ExtensionEntity> findExtensionsByExtPointId(Long extPointId) {
        return extensionRepository.findByExtPointId(extPointId);
    }

    @Override
    public List<ExtensionEntity> findExtensionsByExtPointIdAndTenantCode(Long extPointId, String tenantCode) {
        return extensionRepository.findByExtPointIdAndTenantCode(extPointId, tenantCode);
    }

    @Override
    public ExtensionEntity saveExtension(ExtensionEntity extension) {
        // 验证扩展点是否存在
        if (!extPointRepository.existsById(extension.getExtPointId())) {
            throw new RuntimeException("扩展点不存在: " + extension.getExtPointId());
        }
        
        // 检查实现类名是否已存在（针对同一扩展点）
        extensionRepository.findByExtPointIdAndImplementationClassName(
                extension.getExtPointId(), extension.getImplementationClassName())
                .ifPresent(existing -> {
                    throw new RuntimeException("扩展实现已存在: " + extension.getImplementationClassName());
                });
        
        // 如果未指定优先级，设置默认值
        if (extension.getPriority() == null) {
            extension.setPriority(100);
        }
        
        return extensionRepository.save(extension);
    }

    @Override
    public ExtensionEntity updateExtension(Long id, ExtensionEntity extension) {
        ExtensionEntity existing = extensionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("扩展实现不存在: " + id));
        
        // 验证扩展点是否存在
        if (!extPointRepository.existsById(extension.getExtPointId())) {
            throw new RuntimeException("扩展点不存在: " + extension.getExtPointId());
        }
        
        // 检查实现类名是否与其他记录冲突
        extensionRepository.findByExtPointIdAndImplementationClassNameAndIdNot(
                extension.getExtPointId(), extension.getImplementationClassName(), id)
                .ifPresent(existingExt -> {
                    throw new RuntimeException("扩展实现已存在: " + extension.getImplementationClassName());
                });
        
        // 更新字段
        existing.setExtPointId(extension.getExtPointId());
        existing.setName(extension.getName());
        existing.setDescription(extension.getDescription());
        existing.setImplementationClassName(extension.getImplementationClassName());
        existing.setTenantCode(extension.getTenantCode());
        existing.setPriority(extension.getPriority());
        existing.setConfiguration(extension.getConfiguration());
        existing.setEnabled(extension.isEnabled());
        existing.setStartupPhase(extension.getStartupPhase());
        
        return extensionRepository.save(existing);
    }

    @Override
    public void deleteExtension(Long id) {
        if (!extensionRepository.existsById(id)) {
            throw new RuntimeException("扩展实现不存在: " + id);
        }
        extensionRepository.deleteById(id);
    }

    @Override
    public ExtensionEntity enableExtension(Long id, boolean enabled) {
        ExtensionEntity extension = extensionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("扩展实现不存在: " + id));
        extension.setEnabled(enabled);
        return extensionRepository.save(extension);
    }

    @Override
    public ExtensionEntity updateExtensionPriority(Long id, int priority) {
        ExtensionEntity extension = extensionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("扩展实现不存在: " + id));
        extension.setPriority(priority);
        return extensionRepository.save(extension);
    }

    @Override
    public List<ExtensionEntity> findExtensionsByTenantCode(String tenantCode) {
        return extensionRepository.findByTenantCode(tenantCode);
    }

    @Override
    public Page<ExtensionEntity> searchExtensions(String keyword, Pageable pageable) {
        if (StringUtils.hasText(keyword)) {
            // 使用Specification进行复杂查询
            Specification<ExtensionEntity> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.or(
                        cb.like(root.get("name"), "%" + keyword + "%"),
                        cb.like(root.get("description"), "%" + keyword + "%"),
                        cb.like(root.get("implementationClassName"), "%" + keyword + "%"),
                        cb.like(root.get("tenantCode"), "%" + keyword + "%")
                ));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            return extensionRepository.findAll(spec, pageable);
        }
        return extensionRepository.findAll(pageable);
    }

    @Override
    public int scanAndRegisterExtensions() {
        int registeredCount = 0;
        // TODO: 实现类扫描逻辑，这里暂时返回模拟数据
        // 实际实现应该使用Spring的类扫描机制或自定义类加载器扫描指定包下的所有带@Extension注解的类
        return registeredCount;
    }

    @Override
    public boolean validateExtension(ExtensionEntity extension) {
        // TODO: 实现扩展实现的有效性验证
        // 验证内容包括：
        // 1. 检查实现类是否存在
        // 2. 检查实现类是否实现了对应的扩展点接口
        // 3. 检查配置是否合法
        return true;
    }

    @Override
    public String getExtensionStatistics(Long id) {
        // TODO: 实现扩展实现的调用统计信息获取
        // 暂时返回模拟数据
        return "{\"totalCalls\": 100, \"successCalls\": 95, \"failedCalls\": 5, \"averageExecutionTime\": 10.5}";
    }

    @Override
    public void resetExtensionStatistics(Long id) {
        // TODO: 实现扩展实现的调用统计信息重置
        // 暂时不做处理
    }
}