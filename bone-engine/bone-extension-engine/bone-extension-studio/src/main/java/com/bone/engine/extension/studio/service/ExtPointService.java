package com.bone.engine.extension.studio.service;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;

import java.util.List;
import java.util.Map;

public interface ExtPointService {
    List<ExtPoint> findAllExtPoints();
    ExtPoint findExtPointById(Long id);
    ExtPoint saveExtPoint(ExtPoint extPoint);
    ExtPoint updateExtPoint(Long id, ExtPoint extPoint);

    ExtPoint updateExtPoint(Long id, ExtPoint extPoint, Integer expectedVersion);
    void deleteExtPoint(Long id);
    ExtPoint enableExtPoint(Long id, boolean enabled);
    List<Extension> findExtensionsByExtPointId(Long extPointId);
    List<String> findAllDomains();
    List<String> findAllCategories();
    long getTotalExtPointCount();
    Map<String, Long> getExtPointStatsByDomain();
    Map<String, Long> getExtPointStatsByCategory();
    ExtPoint findExtPointByInterfaceName(String interfaceName);
    List<ExtPoint> findExtPointsByDomain(String domain);
    List<ExtPoint> findExtPointsByCategory(String category);
    List<ExtPoint> searchExtPoints(String keyword);
    int scanAndRegisterExtPoints();
}
