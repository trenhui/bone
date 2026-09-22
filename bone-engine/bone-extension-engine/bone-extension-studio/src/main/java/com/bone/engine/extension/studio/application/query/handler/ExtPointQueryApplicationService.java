package com.bone.engine.extension.studio.application.query.handler;

import com.bone.engine.extension.studio.domain.gateway.ExtPointReadPort;
import com.bone.engine.extension.studio.domain.gateway.ExtensionReadPort;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 扩展点读侧。 */
@Component
@RequiredArgsConstructor
public class ExtPointQueryApplicationService {

  private final ExtPointReadPort extPointReadPort;
  private final ExtPointRepository extPointRepository;
  private final ExtensionReadPort extensionReadPort;

  public List<ExtPoint> findAllExtPoints() {
    return extPointReadPort.findAll();
  }

  public ExtPoint findExtPointById(Long id) {
    return extPointRepository.findById(id);
  }

  public List<Extension> findExtensionsByExtPointId(Long extPointId) {
    return extensionReadPort.findByExtPointId(extPointId);
  }

  public List<String> findAllDomains() {
    return extPointReadPort.findAll().stream()
        .map(ExtPoint::getDomain)
        .filter(StringUtils::hasText)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  public List<String> findAllCategories() {
    return extPointReadPort.findAll().stream()
        .map(ExtPoint::getCategory)
        .filter(StringUtils::hasText)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  public long getTotalExtPointCount() {
    return extPointReadPort.count();
  }

  public Map<String, Long> getExtPointStatsByDomain() {
    return extPointReadPort.findAll().stream()
        .collect(
            Collectors.groupingBy(
                p -> StringUtils.hasText(p.getDomain()) ? p.getDomain() : "unknown",
                Collectors.counting()));
  }

  public Map<String, Long> getExtPointStatsByCategory() {
    return extPointReadPort.findAll().stream()
        .collect(
            Collectors.groupingBy(
                p -> StringUtils.hasText(p.getCategory()) ? p.getCategory() : "unknown",
                Collectors.counting()));
  }

  public ExtPoint findExtPointByInterfaceName(String interfaceName) {
    return extPointRepository.findByInterfaceName(interfaceName);
  }

  public List<ExtPoint> findExtPointsByDomain(String domain) {
    if (!StringUtils.hasText(domain)) {
      return List.of();
    }
    return extPointReadPort.findAll().stream()
        .filter(p -> domain.equals(p.getDomain()))
        .collect(Collectors.toList());
  }

  public List<ExtPoint> findExtPointsByCategory(String category) {
    if (!StringUtils.hasText(category)) {
      return List.of();
    }
    return extPointReadPort.findAll().stream()
        .filter(p -> category.equals(p.getCategory()))
        .collect(Collectors.toList());
  }

  public List<ExtPoint> searchExtPoints(String keyword) {
    return extPointReadPort.search(keyword);
  }
}
