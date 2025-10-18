package com.bone.smartmeta.processor;

import com.bone.smartmeta.metadata.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 组合式元数据处理器，实现多处理器协同工作的组合模式
 * <p>
 * 该处理器能够将多个元数据处理器组合在一起，按照优先级顺序执行处理逻辑，
 * 实现元数据处理的责任链模式，便于功能扩展和自定义处理逻辑
 * </p>
 *
 * @author SmartMeta Team
 */
@Component
public class CompositeMetadataProcessor implements MetadataProcessor {

    private static final Logger log = LoggerFactory.getLogger(CompositeMetadataProcessor.class);

    private final List<MetadataProcessor> processors = new CopyOnWriteArrayList<>();

    /**
     * 自动注入所有MetadataProcessor实现类
     *
     * @param processors 所有元数据处理器实现
     */
    @Autowired(required = false)
    public void setProcessors(List<MetadataProcessor> processors) {
        if (processors != null && !processors.isEmpty()) {
            // 按照@Order注解排序，确保处理顺序正确
            List<MetadataProcessor> sortedProcessors = processors.stream()
                    .filter(processor -> processor != this) // 排除自身，避免递归
                    .sorted(Comparator.comparingInt(processor -> {
                        Order order = processor.getClass().getAnnotation(Order.class);
                        return order != null ? order.value() : Ordered.LOWEST_PRECEDENCE;
                    }))
                    .collect(Collectors.toList());
            
            this.processors.addAll(sortedProcessors);
            log.info("已注册{}个元数据处理器: {}", sortedProcessors.size(), 
                    sortedProcessors.stream()
                            .map(processor -> processor.getClass().getSimpleName())
                            .collect(Collectors.joining(", ")));
        }
    }

    /**
     * 注册单个元数据处理器
     *
     * @param processor 元数据处理器
     */
    public void registerProcessor(MetadataProcessor processor) {
        if (processor != null && processor != this) {
            this.processors.add(processor);
            // 重新排序，确保顺序正确
            sortProcessors();
            log.info("已注册元数据处理器: {}", processor.getClass().getSimpleName());
        }
    }

    /**
     * 移除指定的元数据处理器
     *
     * @param processor 元数据处理器
     * @return 是否成功移除
     */
    public boolean removeProcessor(MetadataProcessor processor) {
        boolean removed = this.processors.remove(processor);
        if (removed) {
            log.info("已移除元数据处理器: {}", processor.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * 获取所有已注册的元数据处理器
     *
     * @return 处理器列表的不可变副本
     */
    public List<MetadataProcessor> getProcessors() {
        return Collections.unmodifiableList(processors);
    }

    @Override
    public EntityMetadata process(EntityMetadata metadata) {
        if (metadata == null) {
            log.warn("处理空的实体元数据");
            return null;
        }

        log.debug("开始处理实体元数据: {}", metadata.getEntityName());
        EntityMetadata processedMetadata = metadata;

        // 按顺序执行所有处理器
        for (MetadataProcessor processor : processors) {
            try {
                log.debug("使用处理器[{}]处理实体: {}", processor.getClass().getSimpleName(), metadata.getEntityName());
                processedMetadata = processor.process(processedMetadata);
                
                // 如果处理器返回null，终止处理链
                if (processedMetadata == null) {
                    log.warn("处理器[{}]返回null，终止处理链", processor.getClass().getSimpleName());
                    break;
                }
            } catch (Exception e) {
                // 记录异常但继续执行下一个处理器
                log.error("处理器[{}]处理实体[{}]时发生异常: {}", 
                        processor.getClass().getSimpleName(), 
                        metadata.getEntityName(), 
                        e.getMessage(), e);
                
                // 根据配置决定是否继续处理
                if (shouldStopOnError()) {
                    log.error("错误处理策略为中断，终止处理链");
                    break;
                }
            }
        }

        log.debug("完成实体元数据处理: {}", metadata.getEntityName());
        return processedMetadata;
    }

    @Override
    public boolean supports(EntityMetadata metadata) {
        if (metadata == null) {
            return false;
        }
        
        // 只要有一个处理器支持，就认为支持
        return processors.stream().anyMatch(processor -> processor.supports(metadata));
    }

    /**
     * 对处理器列表进行排序
     */
    private void sortProcessors() {
        this.processors.sort(Comparator.comparingInt(processor -> {
            Order order = processor.getClass().getAnnotation(Order.class);
            return order != null ? order.value() : Ordered.LOWEST_PRECEDENCE;
        }));
    }

    /**
     * 判断处理器发生错误时是否应该停止处理链
     * 
     * @return 是否在错误时停止处理
     */
    private boolean shouldStopOnError() {
        // 这里可以配置化，暂时返回false，表示继续处理
        return false;
    }
}