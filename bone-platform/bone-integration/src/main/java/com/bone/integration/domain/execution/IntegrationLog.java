package com.bone.integration.domain.execution;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.execution.event.ExecutionCompletedEvent;
import com.bone.integration.domain.model.execution.event.ExecutionStartedEvent;
import com.bone.integration.domain.model.execution.vo.ExecutionStatus;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("int_execution_log")
public class IntegrationLog extends AggregateRoot<Long> {
    private Long id;
    private Long flowId;
    private ExecutionStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String inputData;
    private String outputData;
    private String errorMessage;

    public static IntegrationLog create(Long id, Long flowId, String inputData) {
        if (flowId == null) {
            throw new DomainException("流程ID不能为空");
        }

        IntegrationLog log = new IntegrationLog();
        log.id = id;
        log.flowId = flowId;
        log.status = ExecutionStatus.PENDING;
        log.inputData = inputData;
        log.addDomainEvent(new ExecutionStartedEvent(id, flowId, inputData));
        return log;
    }

    public void start() {
        if (this.status != ExecutionStatus.PENDING) {
            throw new DomainException("执行状态不是待处理，无法开始");
        }
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(String outputData) {
        if (this.status != ExecutionStatus.RUNNING) {
            throw new DomainException("执行状态不是运行中，无法完成");
        }
        this.status = ExecutionStatus.SUCCESS;
        this.endedAt = LocalDateTime.now();
        this.outputData = outputData;
        this.addDomainEvent(new ExecutionCompletedEvent(this.id, this.flowId, true, outputData, null));
    }

    public void fail(String errorMessage) {
        if (this.status != ExecutionStatus.RUNNING) {
            throw new DomainException("执行状态不是运行中，无法标记失败");
        }
        this.status = ExecutionStatus.FAILED;
        this.endedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
        this.addDomainEvent(new ExecutionCompletedEvent(this.id, this.flowId, false, null, errorMessage));
    }

    public void timeout() {
        if (this.status != ExecutionStatus.RUNNING) {
            throw new DomainException("执行状态不是运行中，无法标记超时");
        }
        this.status = ExecutionStatus.TIMEOUT;
        this.endedAt = LocalDateTime.now();
        this.errorMessage = "执行超时";
        this.addDomainEvent(new ExecutionCompletedEvent(this.id, this.flowId, false, null, "执行超时"));
    }
}
