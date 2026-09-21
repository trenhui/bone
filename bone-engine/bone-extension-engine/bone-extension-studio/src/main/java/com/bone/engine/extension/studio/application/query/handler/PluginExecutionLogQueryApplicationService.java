package com.bone.engine.extension.studio.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.domain.gateway.PluginExecutionLogReadPort;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.util.CursorCodec;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 插件执行日志读侧。 */
@Component
@RequiredArgsConstructor
public class PluginExecutionLogQueryApplicationService {

  private final PluginExecutionLogReadPort logReadPort;

  public List<PluginExecutionLog> query(Long pluginId, String status, int page, int size) {
    List<PluginExecutionLog> base;
    if (pluginId != null) {
      base = logReadPort.findByPluginId(pluginId);
    } else if (StringUtils.hasText(status)) {
      base = logReadPort.findByStatus(status);
    } else {
      base = logReadPort.findAll();
    }
    if (pluginId != null && StringUtils.hasText(status)) {
      base =
          base.stream()
              .filter(l -> status.equalsIgnoreCase(l.getStatus()))
              .collect(Collectors.toList());
    }
    int from = Math.max(0, (page - 1) * size);
    int to = Math.min(base.size(), from + size);
    if (from >= base.size()) {
      return List.of();
    }
    return base.subList(from, to);
  }

  public PageResult<PluginExecutionLog> queryByCursor(
      Long pluginId, String status, String cursor, int limit) {
    int safeLimit = Math.min(100, Math.max(1, limit));
    List<PluginExecutionLog> sorted = sortedFiltered(pluginId, status);
    Long afterId = CursorCodec.decode(cursor);
    List<PluginExecutionLog> page =
        sorted.stream()
            .filter(log -> afterId == null || (log.getId() != null && log.getId() < afterId))
            .limit(safeLimit + 1L)
            .toList();
    String nextCursor = null;
    List<PluginExecutionLog> records;
    if (page.size() > safeLimit) {
      records = page.subList(0, safeLimit);
      Long lastId = records.get(records.size() - 1).getId();
      nextCursor = CursorCodec.encode(lastId);
    } else {
      records = page;
    }
    return PageResult.cursorOf(records, nextCursor, safeLimit);
  }

  private List<PluginExecutionLog> sortedFiltered(Long pluginId, String status) {
    List<PluginExecutionLog> base;
    if (pluginId != null) {
      base = logReadPort.findByPluginId(pluginId);
    } else if (StringUtils.hasText(status)) {
      base = logReadPort.findByStatus(status);
    } else {
      base = logReadPort.findAll();
    }
    if (pluginId != null && StringUtils.hasText(status)) {
      base =
          base.stream()
              .filter(l -> status.equalsIgnoreCase(l.getStatus()))
              .collect(Collectors.toList());
    }
    return base.stream()
        .sorted(
            Comparator.comparing(
                    PluginExecutionLog::getId, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PluginExecutionLog::getCreatedAt, Comparator.reverseOrder()))
        .collect(Collectors.toList());
  }

  public long count(Long pluginId, String status) {
    if (pluginId != null) {
      List<PluginExecutionLog> list = logReadPort.findByPluginId(pluginId);
      if (StringUtils.hasText(status)) {
        return list.stream().filter(l -> status.equalsIgnoreCase(l.getStatus())).count();
      }
      return list.size();
    }
    if (StringUtils.hasText(status)) {
      return logReadPort.countByStatus(status);
    }
    return logReadPort.count();
  }

  public Map<String, Object> overview(long extPointCount, long pluginCount, boolean syncEnabled) {
    long total = logReadPort.count();
    long success = logReadPort.countByStatus("SUCCESS");
    long failed = logReadPort.countByStatus("FAILED");
    long running = logReadPort.countByStatus("RUNNING");

    Map<String, Object> stats = new HashMap<>();
    stats.put("extPointCount", extPointCount);
    stats.put("pluginCount", pluginCount);
    stats.put("executionTotal", total);
    stats.put("executionSuccess", success);
    stats.put("executionFailed", failed);
    stats.put("executionRunning", running);
    stats.put("successRate", total == 0 ? 100.0 : Math.round(success * 1000.0 / total) / 10.0);
    stats.put("runtimeSyncEnabled", syncEnabled);
    return stats;
  }
}
