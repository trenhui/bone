package com.bone.platform.alert;

import lombok.extern.slf4j.Slf4j;
import com.bone.platform.alert.autoconfigure.AlertProperties;
import com.bone.platform.alert.autoconfigure.AlertProperties.ChannelPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CompositeAlertService implements AlertService {
    private final AlertProperties properties;
    private final List<AlertChannel> channels;
    private final ExecutorService executor;
    private final RetryTemplate retryTemplate;

    public CompositeAlertService(AlertProperties properties,
                                 List<AlertChannel> channels,
                                 RetryTemplate retryTemplate) {
        this.properties = properties;
        this.channels = channels;
        this.retryTemplate = retryTemplate;
        this.executor = createThreadPool();
    }

    private ExecutorService createThreadPool() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                properties.getAsync().getCorePoolSize(),
                properties.getAsync().getMaxPoolSize(),
                properties.getAsync().getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getAsync().getQueueCapacity()),
                new AlertThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Override
    public void sendAlert(AlertLevel level,String message,String businessId){
        AlertMessage alertMessage = AlertMessage.createAlertMessage(
                AlertLevel.HIGH,
                "系统异常",
                message,
                businessId,
                null
        );
        sendAlert(alertMessage);
    }


    @Override
    public void sendAlert(AlertMessage message) {
        AlertLevel level = Optional.ofNullable(message.getLevel())
                .orElse(AlertLevel.INFO);

        List<AlertChannelType> channelTypes = Optional.ofNullable(properties.getPolicies().get(level))
                .map(ChannelPolicy::getChannels)
                .orElse(Collections.emptyList());

        channelTypes.forEach(channelType ->
                executor.execute(() -> processChannel(channelType, message))
        );
    }

    private void processChannel(AlertChannelType channelType, AlertMessage message) {
        findChannel(channelType).ifPresent(channel -> {
            try {
                retryTemplate.execute(context -> {
                    channel.send(message);
                    return null;
                });
                log.info("Alert [{}] sent via {}", message.getId(), channelType);
            } catch (Exception e) {
                log.error("Failed to send alert [{}] via {} ",
                        message.getId(), channelType, e);
            }
        });
    }

    private Optional<AlertChannel> findChannel(AlertChannelType type) {
        return channels.stream()
                .filter(c -> c.channelType().equals(type))
                .findFirst();
    }

    private static class AlertThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("alert-executor-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
