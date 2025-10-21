package com.bone.engine.extension.studio.service.impl;

// import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.studio.model.ExtPointEntity;
import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.repository.ExtPointRepository;
import com.bone.engine.extension.studio.repository.ExtensionRepository;
import com.bone.engine.extension.studio.service.ExtPointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Predicate;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 扩展点服务实现类
 */
@Service
public class ExtPointServiceImpl implements ExtPointService {

    @Autowired
    private ExtPointRepository extPointRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Value("${bone.extension.studio.scan.base-packages:com.bone.engine.extension.example}")
    private String scanBasePackages;

    @Override
    public Page<ExtPointEntity> findAllExtPoints(Pageable pageable) {
        return extPointRepository.findAll(pageable);
    }

    @Override
    public Optional<ExtPointEntity> findExtPointById(Long id) {
        return extPointRepository.findById(id);
    }

    @Override
    public Optional<ExtPointEntity> findExtPointByInterfaceName(String interfaceName) {
        return extPointRepository.findByInterfaceName(interfaceName);
    }

    @Override
    public ExtPointEntity saveExtPoint(ExtPointEntity extPoint) {
        // 检查接口名称是否已存在
        if (extPointRepository.findByInterfaceName(extPoint.getInterfaceName()).isPresent()) {
            throw new RuntimeException("扩展点接口已存在: " + extPoint.getInterfaceName());
        }
        return extPointRepository.save(extPoint);
    }

    @Override
    public ExtPointEntity updateExtPoint(Long id, ExtPointEntity extPoint) {
        ExtPointEntity existing = extPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("扩展点不存在: " + id));
        
        // 更新字段
        existing.setName(extPoint.getName());
        existing.setDescription(extPoint.getDescription());
        existing.setDomain(extPoint.getDomain());
        existing.setCategory(extPoint.getCategory());
        existing.setType(extPoint.getType());
        existing.setVersion(extPoint.getVersion());
        existing.setDeprecated(extPoint.isDeprecated());
        existing.setDeprecatedSince(extPoint.getDeprecatedSince());
        existing.setDeprecatedIn(extPoint.getDeprecatedIn());
        
        return extPointRepository.save(existing);
    }

    @Override
    public void deleteExtPoint(Long id) {
        // 检查是否有关联的扩展实现
        long extensionCount = extensionRepository.countByExtPointId(id);
        if (extensionCount > 0) {
            throw new RuntimeException("该扩展点有 " + extensionCount + " 个扩展实现，无法删除");
        }
        extPointRepository.deleteById(id);
    }

    @Override
    public ExtPointEntity enableExtPoint(Long id, boolean enabled) {
        ExtPointEntity extPoint = extPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("扩展点不存在: " + id));
        extPoint.setEnabled(enabled);
        return extPointRepository.save(extPoint);
    }

    @Override
    public List<ExtPointEntity> findExtPointsByDomain(String domain) {
        return extPointRepository.findByDomain(domain);
    }

    @Override
    public List<ExtPointEntity> findExtPointsByCategory(String category) {
        return extPointRepository.findByCategory(category);
    }

    @Override
    public List<ExtPointEntity> findExtPointsByDomainAndCategory(String domain, String category) {
        return extPointRepository.findByDomainAndCategory(domain, category);
    }

    @Override
    public Page<ExtPointEntity> searchExtPoints(String keyword, Pageable pageable) {
        if (StringUtils.hasText(keyword)) {
            // 使用Specification进行复杂查询
            Specification<ExtPointEntity> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.or(
                        cb.like(root.get("name"), "%" + keyword + "%"),
                        cb.like(root.get("description"), "%" + keyword + "%"),
                        cb.like(root.get("interfaceName"), "%" + keyword + "%"),
                        cb.like(root.get("domain"), "%" + keyword + "%"),
                        cb.like(root.get("category"), "%" + keyword + "%")
                ));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            return extPointRepository.findAll(spec, pageable);
        }
        return extPointRepository.findAll(pageable);
    }

    @Override
    public List<ExtensionEntity> findExtensionsByExtPointId(Long extPointId) {
        return extensionRepository.findByExtPointId(extPointId);
    }

    @Override
    public int scanAndRegisterExtPoints() {
        int registeredCount = 0;
        // TODO: 实现类扫描逻辑，这里暂时返回模拟数据
        // 实际实现应该使用Spring的类扫描机制或自定义类加载器扫描指定包下的所有带@ExtPoint注解的接口
        return registeredCount;
    }

    @Override
    public List<String> findAllDomains() {
        return extPointRepository.findAll().stream()
                .map(ExtPointEntity::getDomain)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> findAllCategories() {
        return extPointRepository.findAll().stream()
                .map(ExtPointEntity::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}