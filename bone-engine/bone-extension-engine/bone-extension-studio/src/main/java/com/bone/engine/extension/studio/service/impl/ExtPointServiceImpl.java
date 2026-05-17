package com.bone.engine.extension.studio.service.impl;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.store.ExtPointStore;
import com.bone.engine.extension.studio.domain.store.ExtensionStore;
import com.bone.engine.extension.studio.service.ExtPointService;
import com.bone.engine.extension.studio.service.StudioVersionSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExtPointServiceImpl implements ExtPointService {

    private static final Logger log = LoggerFactory.getLogger(ExtPointServiceImpl.class);

    @Autowired
    private ExtPointStore extPointStore;

    @Autowired
    private ExtensionStore extensionStore;

    @Override
    public List<ExtPoint> findAllExtPoints() {
        return extPointStore.findAll();
    }

    @Override
    public ExtPoint findExtPointById(Long id) {
        return extPointStore.findById(id);
    }

    @Override
    public ExtPoint saveExtPoint(ExtPoint extPoint) {
        if (extPoint.getVersion() == null) {
            extPoint.setVersion(0);
        }
        return extPointStore.save(extPoint);
    }

    @Override
    public ExtPoint updateExtPoint(Long id, ExtPoint extPoint) {
        return updateExtPoint(id, extPoint, null);
    }

    @Override
    public ExtPoint updateExtPoint(Long id, ExtPoint extPoint, Integer expectedVersion) {
        ExtPoint existing = extPointStore.findById(id);
        if (existing == null) {
            return null;
        }
        StudioVersionSupport.assertExpected(expectedVersion, existing.getVersion());
        existing.setName(extPoint.getName());
        existing.setDescription(extPoint.getDescription());
        existing.setInterfaceName(extPoint.getInterfaceName());
        existing.setDomain(extPoint.getDomain());
        existing.setCategory(extPoint.getCategory());
        existing.setEnabled(extPoint.isEnabled());
        existing.setVersion(StudioVersionSupport.nextVersion(existing.getVersion()));
        extPointStore.update(existing);
        return existing;
    }

    @Override
    public void deleteExtPoint(Long id) {
        extPointStore.deleteById(id);
    }

    @Override
    public ExtPoint enableExtPoint(Long id, boolean enabled) {
        ExtPoint extPoint = extPointStore.findById(id);
        if (extPoint == null) {
            return null;
        }
        extPoint.setEnabled(enabled);
        extPointStore.update(extPoint);
        return extPoint;
    }

    @Override
    public List<Extension> findExtensionsByExtPointId(Long extPointId) {
        return extensionStore.findByExtPointId(extPointId);
    }

    @Override
    public List<String> findAllDomains() {
        return extPointStore.findAll().stream()
                .map(ExtPoint::getDomain)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> findAllCategories() {
        return extPointStore.findAll().stream()
                .map(ExtPoint::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public long getTotalExtPointCount() {
        return extPointStore.count();
    }

    @Override
    public Map<String, Long> getExtPointStatsByDomain() {
        return extPointStore.findAll().stream()
                .collect(Collectors.groupingBy(
                        p -> StringUtils.hasText(p.getDomain()) ? p.getDomain() : "unknown",
                        Collectors.counting()));
    }

    @Override
    public Map<String, Long> getExtPointStatsByCategory() {
        return extPointStore.findAll().stream()
                .collect(Collectors.groupingBy(
                        p -> StringUtils.hasText(p.getCategory()) ? p.getCategory() : "unknown",
                        Collectors.counting()));
    }

    @Override
    public ExtPoint findExtPointByInterfaceName(String interfaceName) {
        return extPointStore.findByInterfaceName(interfaceName);
    }

    @Override
    public List<ExtPoint> findExtPointsByDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return List.of();
        }
        return extPointStore.findAll().stream()
                .filter(p -> domain.equals(p.getDomain()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ExtPoint> findExtPointsByCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return List.of();
        }
        return extPointStore.findAll().stream()
                .filter(p -> category.equals(p.getCategory()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ExtPoint> searchExtPoints(String keyword) {
        return extPointStore.search(keyword);
    }

    @Override
    public int scanAndRegisterExtPoints() {
        log.info("扩展点 classpath 扫描暂未接入，返回 0");
        return 0;
    }
}
