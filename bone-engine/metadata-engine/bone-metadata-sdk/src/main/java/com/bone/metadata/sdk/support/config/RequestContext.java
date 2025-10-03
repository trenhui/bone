package com.bone.metadata.sdk.support.config;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

public class RequestContext {
    private static final String REQUEST_ID_KEY = "X-Request-ID";
    private final ThreadLocal<String> requestId = new ThreadLocal<>();

    public void init() {
        if (requestId.get() == null) {
            requestId.set(UUID.randomUUID().toString());
        }
        MDC.put(REQUEST_ID_KEY, requestId.get());
    }

    public void clear() {
        MDC.remove(REQUEST_ID_KEY);
        requestId.remove();
    }

    public String getRequestId() {
        String id = requestId.get();
        if (id == null) {
            id = UUID.randomUUID().toString();
            requestId.set(id);
            MDC.put(REQUEST_ID_KEY, id);
        }
        return id;
    }

    public Runnable wrap(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }

    public <T> Callable<T> wrap(Callable<T> callable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                return callable.call();
            } finally {
                MDC.clear();
            }
        };
    }
}