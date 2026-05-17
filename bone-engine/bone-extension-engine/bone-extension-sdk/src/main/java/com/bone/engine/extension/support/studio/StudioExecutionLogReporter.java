package com.bone.engine.extension.support.studio;

import com.bone.engine.extension.support.config.ExtensionProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 将运行时扩展调用结果异步上报至 bone-extension-studio。
 */
@Component
@ConditionalOnProperty(prefix = "bone.extension.studio.report", name = "enabled", havingValue = "true")
public class StudioExecutionLogReporter {

    private static final Logger log = LoggerFactory.getLogger(StudioExecutionLogReporter.class);

    private final RestTemplate restTemplate;
    private final String ingestUrl;

    public StudioExecutionLogReporter(
            ExtensionProperties properties, RestTemplateBuilder restTemplateBuilder) {
        ExtensionProperties.StudioReportConfig report = properties.getStudio().getReport();
        String base = StringUtils.hasText(report.getBaseUrl()) ? report.getBaseUrl().trim() : "http://localhost:8088";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.ingestUrl = base + "/api/v1/extension/execution-logs/ingest";
        this.restTemplate = restTemplateBuilder.build();
        log.info("Studio execution log reporter enabled, ingest={}", ingestUrl);
    }

    public void reportSuccess(String className, String methodName, long durationMs) {
        report(className, methodName, "SUCCESS", durationMs, null);
    }

    public void reportFailure(String className, String methodName, long durationMs, String errorMessage) {
        report(className, methodName, "FAILED", durationMs, errorMessage);
    }

    private void report(
            String className, String methodName, String status, long durationMs, String errorMessage) {
        if (!StringUtils.hasText(className)) {
            return;
        }
        CompletableFuture.runAsync(
                () -> {
                    try {
                        Map<String, Object> body = new HashMap<>();
                        body.put("className", className);
                        body.put("methodName", methodName);
                        body.put("status", status);
                        body.put("durationMs", durationMs);
                        if (StringUtils.hasText(errorMessage)) {
                            body.put("errorMessage", errorMessage);
                        }
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        restTemplate.postForEntity(ingestUrl, new HttpEntity<>(body, headers), String.class);
                    } catch (Exception ex) {
                        log.debug("Failed to report execution log to studio: {}", ex.getMessage());
                    }
                });
    }
}
