package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.gateway.ExtPointReadPort;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
    prefix = "bone.extension.studio.persistence",
    name = "mode",
    havingValue = "in-memory",
    matchIfMissing = true)
public class InMemoryStudioExtPointRepository implements ExtPointRepository, ExtPointReadPort {

  private final Map<Long, ExtPoint> storage = new ConcurrentHashMap<>();
  private final AtomicLong idSequence = new AtomicLong(1);

  @Override
  public List<ExtPoint> findAll() {
    return storage.values().stream()
        .sorted(Comparator.comparing(ExtPoint::getId, Comparator.nullsLast(Long::compareTo)))
        .collect(Collectors.toList());
  }

  @Override
  @Nullable
  public ExtPoint findById(Long id) {
    return id == null ? null : storage.get(id);
  }

  @Override
  @Nullable
  public ExtPoint findByInterfaceName(String interfaceName) {
    if (interfaceName == null) {
      return null;
    }
    return storage.values().stream()
        .filter(e -> interfaceName.equals(e.getInterfaceName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public List<ExtPoint> search(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return findAll();
    }
    String lower = keyword.toLowerCase();
    return storage.values().stream()
        .filter(
            e ->
                contains(e.getName(), lower)
                    || contains(e.getInterfaceName(), lower)
                    || contains(e.getDescription(), lower))
        .collect(Collectors.toList());
  }

  @Override
  public ExtPoint save(ExtPoint extPoint) {
    if (extPoint.getId() == null) {
      extPoint.setId(idSequence.getAndIncrement());
    }
    storage.put(extPoint.getId(), extPoint);
    return extPoint;
  }

  @Override
  public boolean remove(Long id) {
    return id != null && storage.remove(id) != null;
  }

  @Override
  public long count() {
    return storage.size();
  }

  private static boolean contains(@Nullable String value, String lowerKeyword) {
    return value != null && value.toLowerCase().contains(lowerKeyword);
  }
}
