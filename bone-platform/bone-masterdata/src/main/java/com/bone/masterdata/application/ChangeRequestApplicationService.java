package com.bone.masterdata.application;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.application.command.ApplyChangeRequestCommand;
import com.bone.masterdata.application.command.CreateChangeRequestCommand;
import com.bone.masterdata.application.command.DecideSegmentCommand;
import com.bone.masterdata.application.command.SubmitChangeRequestCommand;
import com.bone.masterdata.application.query.dto.ChangeRequestStateDTO;
import com.bone.masterdata.application.query.dto.ChangeRequestStateDTO.SegmentState;
import com.bone.masterdata.domain.gateway.ChangeRequestActivationPort;
import com.bone.masterdata.domain.gateway.ChangeRequestPort;
import com.bone.masterdata.domain.gateway.ChangeRequestWorkflowPort;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest.ApprovalState;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest.Segment;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest.Status;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分段并行变更请求的应用编排（平台共享服务层用例）。
 *
 * <p><b>链路</b>：创建（三段具名路由）→ 提交并行审批 → 各段 specialist 裁决 → 全部通过 → 应用并原子发布 Active 区。 四个环节都标注 {@link
 * Transactional}：{@code CR 状态落库} 与 {@link ChangeRequestActivationPort#activate} 的 Active 写入 +
 * Outbox 追加必须同事务，绝不留下「CR 已应用但生效区没变 / 变了却没分发」的半成品。
 *
 * <p><b>N1 硬约束</b>：{@link ChangeRequestActivationPort#activate} <b>只允许</b>在领域内核 {@code apply()} 返回
 * {@code APPLIED} 后调用；拒绝、转人工、未齐三段的路径零激活、零分发。编排层不自行判断「是否可以发布」， 避免把状态判断复制成第二真源。
 *
 * <p><b>越权防护</b>：命令里的 tenantId 与登录态逐笔比对；操作人（提交人 / 审批人 / 应用人）必须与 JWT 登录账号 一致，禁止在请求体里代签他人姓名。上一轮
 * Golden Record 回滚允许 body 自报 {@code operator}，本用例收紧为登录态绑定。
 *
 * <p><b>能力开关</b>：生产 {@code mdm_change_request*} 端口属 L3 未交付，默认不装配；开关是终局的部署态治理构件，
 * 不是「先内存后换库」的过渡实现（端口契约与领域语义在终局图中原位存在）。
 */
@Service
@ConditionalOnProperty(
    prefix = "bone.masterdata.change-request",
    name = "enabled",
    havingValue = "true")
public class ChangeRequestApplicationService {

  private final ChangeRequestPort changeRequestPort;
  private final ChangeRequestActivationPort activationPort;
  private final ChangeRequestWorkflowPort workflowPort;
  private final CurrentUserPort currentUserPort;

  public ChangeRequestApplicationService(
      ChangeRequestPort changeRequestPort,
      ChangeRequestActivationPort activationPort,
      ChangeRequestWorkflowPort workflowPort,
      CurrentUserPort currentUserPort) {
    this.changeRequestPort = changeRequestPort;
    this.activationPort = activationPort;
    this.workflowPort = workflowPort;
    this.currentUserPort = currentUserPort;
  }

  @Transactional
  public ChangeRequestStateDTO create(CreateChangeRequestCommand cmd) {
    if (cmd == null) {
      throw new DomainException("创建变更请求的命令为空");
    }
    long tenantId = requireSameTenant(cmd.getTenantId());
    String createdBy = requireActor(cmd.getCreatedBy());
    Instant at = cmd.getCreatedAt() == null ? Instant.now() : cmd.getCreatedAt();
    Map<Segment, String> owners = parseOwners(cmd.getSegmentOwners(), createdBy);
    if (changeRequestPort.existsAppliedFor(tenantId, cmd.getPartyId(), cmd.getTargetVersion())) {
      throw new DomainException("同一目标版本已有已应用变更请求，禁止并行重复发布");
    }
    ChangeRequest cr =
        ChangeRequest.create(
            cmd.getChangeRequestId(),
            tenantId,
            cmd.getPartyId(),
            cmd.getTargetVersion(),
            cmd.getChangeDescription(),
            createdBy,
            at,
            owners);
    changeRequestPort.save(cr);
    return toDto(cr);
  }

  @Transactional
  public ChangeRequestStateDTO submit(SubmitChangeRequestCommand cmd) {
    if (cmd == null) {
      throw new DomainException("提交命令为空");
    }
    long tenantId = requireSameTenant(cmd.getTenantId());
    String operator = requireActor(cmd.getOperator());
    Instant at = cmd.getOccurredAt() == null ? Instant.now() : cmd.getOccurredAt();
    ChangeRequest next = load(tenantId, cmd.getChangeRequestId()).submit(operator, at);
    changeRequestPort.save(next);
    workflowPort.onSubmitted(next);
    return toDto(next);
  }

  @Transactional
  public ChangeRequestStateDTO decide(DecideSegmentCommand cmd) {
    if (cmd == null) {
      throw new DomainException("裁决命令为空");
    }
    long tenantId = requireSameTenant(cmd.getTenantId());
    String approver = requireActor(cmd.getApprover());
    Segment segment = Segment.of(cmd.getSegment());
    ApprovalState target = cmd.isApproved() ? ApprovalState.APPROVED : ApprovalState.REJECTED;
    Instant at = cmd.getOccurredAt() == null ? Instant.now() : cmd.getOccurredAt();
    ChangeRequest next =
        load(tenantId, cmd.getChangeRequestId())
            .decide(segment, target, approver, at, cmd.getReason());
    changeRequestPort.save(next);
    workflowPort.onSegmentDecided(next, segment, target);
    return toDto(next);
  }

  /** 唯一允许写 Active 区的入口；领域内核拒绝时激活端口零调用。 */
  @Transactional
  public ChangeRequestStateDTO apply(ApplyChangeRequestCommand cmd) {
    if (cmd == null) {
      throw new DomainException("应用命令为空");
    }
    long tenantId = requireSameTenant(cmd.getTenantId());
    String applier = requireActor(cmd.getApplier());
    Instant at = cmd.getOccurredAt() == null ? Instant.now() : cmd.getOccurredAt();
    ChangeRequest applied = load(tenantId, cmd.getChangeRequestId()).apply(applier, at);
    changeRequestPort.save(applied);
    activationPort.activate(applied);
    workflowPort.onApplied(applied);
    return toDto(applied);
  }

  /** 只读治理态（租户取登录态，API 不接受自报租户）。 */
  public Optional<ChangeRequestStateDTO> currentForCaller(String changeRequestId) {
    Long caller = currentUserPort.currentTenantId();
    if (caller == null) {
      throw new DomainException("缺少登录租户上下文，禁止读取变更请求");
    }
    return changeRequestPort.findById(caller, changeRequestId).map(this::toDto);
  }

  private ChangeRequest load(long tenantId, String changeRequestId) {
    if (isBlank(changeRequestId)) {
      throw new DomainException("变更请求号为空");
    }
    return changeRequestPort
        .findById(tenantId, changeRequestId)
        .orElseThrow(() -> new DomainException("变更请求不存在或不属于当前租户: " + changeRequestId));
  }

  /** 三段路由齐全、Owner 具名且不得是提交人（否则该段永远无法被合法审批，形成死 CR）。 */
  private Map<Segment, String> parseOwners(Map<String, String> raw, String createdBy) {
    if (raw == null || raw.size() != Segment.values().length) {
      throw new DomainException("三段 specialist 路由必须齐全，禁止缺省分段审批");
    }
    Map<Segment, String> owners = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      Segment segment = Segment.of(e.getKey());
      if (owners.containsKey(segment)) {
        throw new DomainException("分段路由重复: " + segment);
      }
      if (isBlank(e.getValue())) {
        throw new DomainException("分段 Owner 不得为空: " + segment);
      }
      if (e.getValue().equals(createdBy)) {
        throw new DomainException("SoD 违规：提交人不得兼任分段审批 Owner: " + segment);
      }
      owners.put(segment, e.getValue());
    }
    return owners;
  }

  private long requireSameTenant(Long requested) {
    Long caller = currentUserPort.currentTenantId();
    if (caller == null) {
      throw new DomainException("缺少登录租户上下文，禁止变更请求治理操作");
    }
    if (requested == null || requested.longValue() != caller.longValue()) {
      throw new DomainException("命令 tenantId 与登录租户不一致，禁止跨租户治理");
    }
    return caller;
  }

  /** 具名操作人必须等于登录账号，杜绝请求体代签（N2 的越权面）。 */
  private String requireActor(String actor) {
    Long uid = currentUserPort.currentUserId();
    if (uid == null) {
      throw new DomainException("缺少登录账号上下文，禁止匿名治理操作");
    }
    String expected = String.valueOf(uid);
    if (isBlank(actor) || !expected.equals(actor)) {
      throw new DomainException("操作人必须与登录账号一致，禁止代签");
    }
    return actor;
  }

  private ChangeRequestStateDTO toDto(ChangeRequest cr) {
    Map<String, SegmentState> segments = new LinkedHashMap<>();
    cr.getSegments()
        .forEach(
            (segment, approval) ->
                segments.put(
                    segment.name(),
                    SegmentState.builder()
                        .assignedOwner(approval.getAssignedOwner())
                        .state(approval.getState().name())
                        .decidedBy(approval.getDecidedBy())
                        .build()));
    return ChangeRequestStateDTO.builder()
        .changeRequestId(cr.getChangeRequestId())
        .partyId(cr.getPartyId())
        .targetVersion(cr.getTargetVersion())
        .status(cr.getStatus().name())
        .segments(segments)
        .distinctApprovers(cr.approvers().size())
        .appliedBy(cr.getAppliedBy())
        .trailSize(cr.getTrail().size())
        .activated(cr.getStatus() == Status.APPLIED)
        .build();
  }

  private static boolean isBlank(String v) {
    return v == null || v.trim().isEmpty();
  }
}
