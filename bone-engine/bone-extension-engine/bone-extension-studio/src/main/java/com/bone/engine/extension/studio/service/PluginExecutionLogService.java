package com.bone.engine.extension.studio.service;

import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.store.ExtensionStore;
import com.bone.engine.extension.studio.domain.store.PluginExecutionLogStore;
import com.bone.engine.extension.studio.util.CursorCodec;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PluginExecutionLogService {

    @Autowired
    private PluginExecutionLogStore logStore;

    @Autowired
    private ExtensionStore extensionStore;

    /**
     * 运行时 SDK 上报执行结果（按实现类全限定名匹配 Studio 插件）。
     *
     * @return 写入的日志；未匹配到插件时返回 empty
     */
    public java.util.Optional<PluginExecutionLog> ingestFromRuntime(
            String className, String methodName, String status, Long durationMs, String errorMessage) {
        if (!StringUtils.hasText(className)) {
            return java.util.Optional.empty();
        }
        Extension extension = extensionStore.findByClassName(className.trim());
        if (extension == null) {
            return java.util.Optional.empty();
        }
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : "SUCCESS";
        String input =
                "{\"source\":\"runtime-sdk\",\"className\":\""
                        + className
                        + "\",\"method\":\""
                        + (methodName != null ? methodName : "")
                        + "\"}";
        return java.util.Optional.of(record(
                extension,
                "INVOKE",
                normalizedStatus,
                input,
                null,
                errorMessage,
                durationMs != null ? durationMs : 0L));
    }

    public PluginExecutionLog record(
            Extension extension, String action, String status, String input, String output, String error, long durationMs) {
        PluginExecutionLog log = new PluginExecutionLog();
        log.setPluginId(extension.getId());
        log.setExtensionPointId(extension.getExtPointId());
        log.setExecutionId(UUID.randomUUID().toString().replace("-", ""));
        log.setStatus(status);
        log.setInputData(input != null ? input : "{\"action\":\"" + action + "\"}");
        log.setOutputData(output);
        log.setErrorMessage(error);
        log.setDurationMs(durationMs);
        return logStore.save(log);
    }

    public List<PluginExecutionLog> query(Long pluginId, String status, int page, int size) {
        List<PluginExecutionLog> base;
        if (pluginId != null) {
            base = logStore.findByPluginId(pluginId);
        } else if (StringUtils.hasText(status)) {
            base = logStore.findByStatus(status);
        } else {
            base = logStore.findAll();
        }
        if (pluginId != null && StringUtils.hasText(status)) {
            base = base.stream()
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

    public PageResult<PluginExecutionLog> queryByCursor(Long pluginId, String status, String cursor, int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        List<PluginExecutionLog> sorted = sortedFiltered(pluginId, status);
        Long afterId = CursorCodec.decode(cursor);
        List<PluginExecutionLog> page = sorted.stream()
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
            base = logStore.findByPluginId(pluginId);
        } else if (StringUtils.hasText(status)) {
            base = logStore.findByStatus(status);
        } else {
            base = logStore.findAll();
        }
        if (pluginId != null && StringUtils.hasText(status)) {
            base = base.stream()
                    .filter(l -> status.equalsIgnoreCase(l.getStatus()))
                    .collect(Collectors.toList());
        }
        return base.stream()
                .sorted(Comparator.comparing(PluginExecutionLog::getId, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PluginExecutionLog::getCreatedAt, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    public long count(Long pluginId, String status) {
        if (pluginId != null) {
            List<PluginExecutionLog> list = logStore.findByPluginId(pluginId);
            if (StringUtils.hasText(status)) {
                return list.stream().filter(l -> status.equalsIgnoreCase(l.getStatus())).count();
            }
            return list.size();
        }
        if (StringUtils.hasText(status)) {
            return logStore.countByStatus(status);
        }
        return logStore.count();
    }

    public Map<String, Object> overview(long extPointCount, long pluginCount, boolean syncEnabled) {
        long total = logStore.count();
        long success = logStore.countByStatus("SUCCESS");
        long failed = logStore.countByStatus("FAILED");
        long running = logStore.countByStatus("RUNNING");

        Map<String, Object> stats = new HashMap<>();
        stats.put("extPointCount", extPointCount);
        stats.put("pluginCount", pluginCount);
        stats.put("executionTotal", total);
        stats.put("executionSuccess", success);
        stats.put("executionFailed", failed);
        stats.put("executionRunning", running);
        stats.put(
                "successRate",
                total == 0 ? 100.0 : Math.round(success * 1000.0 / total) / 10.0);
        stats.put("runtimeSyncEnabled", syncEnabled);
        return stats;
    }
}
