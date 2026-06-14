package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.gateway.PluginExecutionLogReadPort;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.repository.PluginExecutionLogRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnProperty(
    prefix = "bone.extension.studio.persistence",
    name = "mode",
    havingValue = "in-memory",
    matchIfMissing = true)
public class InMemoryStudioPluginExecutionLogRepository
    implements PluginExecutionLogRepository, PluginExecutionLogReadPort {

  private final Map<Long, PluginExecutionLog> storage = new ConcurrentHashMap<>();
  private final AtomicLong idSequence = new AtomicLong(1);

  @Override
  public PluginExecutionLog save(PluginExecutionLog log) {
    if (log.getId() == null) {
      log.setId(idSequence.getAndIncrement());
    }
    storage.put(log.getId(), log);
    return log;
  }

  @Override
  public List<PluginExecutionLog> findAll() {
    return storage.values().stream()
        .sorted(Comparator.comparing(PluginExecutionLog::getCreatedAt).reversed())
        .collect(Collectors.toList());
  }

  @Override
  public List<PluginExecutionLog> findByPluginId(Long pluginId) {
    return storage.values().stream()
        .filter(l -> pluginId.equals(l.getPluginId()))
        .sorted(Comparator.comparing(PluginExecutionLog::getCreatedAt).reversed())
        .collect(Collectors.toList());
  }

  @Override
  public List<PluginExecutionLog> findByStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return findAll();
    }
    return storage.values().stream()
        .filter(l -> status.equalsIgnoreCase(l.getStatus()))
        .sorted(Comparator.comparing(PluginExecutionLog::getCreatedAt).reversed())
        .collect(Collectors.toList());
  }

  @Override
  public long count() {
    return storage.size();
  }

  @Override
  public long countByStatus(String status) {
    return storage.values().stream().filter(l -> status.equalsIgnoreCase(l.getStatus())).count();
  }
}
