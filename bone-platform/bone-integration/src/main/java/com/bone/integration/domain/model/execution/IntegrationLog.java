package com.bone.integration.domain.model.execution;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.execution.event.ExecutionCompletedEvent;
import com.bone.integration.domain.model.execution.event.ExecutionStartedEvent;
import com.bone.integration.domain.model.execution.vo.ExecutionLogId;
import com.bone.integration.domain.model.execution.vo.ExecutionStatus;
import com.bone.integration.domain.model.flow.vo.FlowId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IntegrationLog extends AggregateRoot<Long> {
    private ExecutionLogId id;
    private FlowId flowId;
    private ExecutionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String inputData;
    private String outputData;
    private String errorMessage;

    public static IntegrationLog create(FlowId flowId, String inputData) {
        if (flowId == null) {
            throw new DomainException("流程ID不能为空");
        }

        IntegrationLog log = new IntegrationLog();
        log.flowId = flowId;
        log.status = ExecutionStatus.PENDING;
        log.inputData = inputData;
        log.addDomainEvent(new ExecutionStartedEvent(null, flowId.value(), inputData));
        return log;
    }

    public void start() {
        if (this.status != ExecutionStatus.PENDING) {
            throw new DomainException("执行状态不是待处理，无法开始");
        }
        this.status = ExecutionStatus.RUNNING;
        this.startTime = LocalDateTime.now();
    }

    public void complete(String outputData) {
        if (this.status != ExecutionStatus.RUNNING) {
            throw new DomainException("执行状态不是运行中，无法完成");
        }
        this.status = ExecutionStatus.SUCCESS;
        this.endTime = LocalDateTime.now();
        this.outputData = outputData;
        this.addDomainEvent(new ExecutionCompletedEvent(this.id.value(), this.flowId.value(), true, outputData, null));
    }

    public void fail(String errorMessage) {
        if (this.status != ExecutionStatus.RUNNING) {
            throw new DomainException("执行状态不是运行中，无法标记失败");
        }
        this.status = ExecutionStatus.FAILED;
        this.endTime = LocalDateTime.now();
        this.errorMessage = errorMessage;
        this.addDomainEvent(new ExecutionCompletedEvent(this.id.value(), this.flowId.value(), false, null, errorMessage));
    }

    public void timeout() {
        if (this.status != ExecutionStatus.RUNNING) {
            throw new DomainException("执行状态不是运行中，无法标记超时");
        }
        this.status = ExecutionStatus.TIMEOUT;
        this.endTime = LocalDateTime.now();
        this.errorMessage = "执行超时";
        this.addDomainEvent(new ExecutionCompletedEvent(this.id.value(), this.flowId.value(), false, null, "执行超时"));
    }

    void setId(Long id) {
        this.id = ExecutionLogId.of(id);
    }
}