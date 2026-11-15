package com.bone.masterdata.application;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.application.command.ResolveAndMergeCommand;
import com.bone.masterdata.application.command.RollbackMergeCommand;
import com.bone.masterdata.application.query.dto.GoldenRecordMergeResultDTO;
import com.bone.masterdata.application.query.dto.GoldenRecordStateDTO;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.gateway.EntityKeyMappingPort;
import com.bone.masterdata.domain.gateway.GoldenRecordPort;
import com.bone.masterdata.domain.gateway.MasterDataOutboxPort;
import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import com.bone.masterdata.domain.model.goldenrecord.FieldSurvivorshipRule;
import com.bone.masterdata.domain.model.goldenrecord.FieldSurvivorshipRule.Strategy;
import com.bone.masterdata.domain.model.goldenrecord.GoldenRecord;
import com.bone.masterdata.domain.model.goldenrecord.GoldenRecordMerger;
import com.bone.masterdata.domain.model.goldenrecord.MergeAudit;
import com.bone.masterdata.domain.model.goldenrecord.MergeCommand;
import com.bone.masterdata.domain.model.goldenrecord.MergeResult;
import com.bone.masterdata.domain.model.goldenrecord.SourceRecord;
import com.bone.masterdata.domain.model.goldenrecord.SurvivorshipPolicy;
import com.bone.masterdata.domain.model.identity.EntityKeyMapping;
import com.bone.masterdata.domain.model.identity.EntityKeyMappingRegistry;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Golden Record 端到端编排（平台共享服务层的应用用例）。
 *
 * <p><b>链路</b>：xref 解析本地编码 → survivorship 合并 → 快照+审计原子落库 → 事务性 Outbox 幂等分发。
 * 五个领域内核在此被串成一条可联调的真实链路，而不是各自孤立的纯对象。
 *
 * <p><b>终局分工</b>：领域内核负责内容正确性（跨租户、身份冲突、lineage、具名裁决、并发防覆盖）， 编排层负责对外契约（版本单调、幂等键、补偿事件、PII 脱敏）。后续替换
 * ApplicationService / DDL / API adapter 时，领域语义零变化。
 *
 * <p><b>能力开关</b>：生产端口（{@code mdm_golden_record} / {@code mdm_xref} / Outbox 表）属 L3 尚未落地，
 * 因此默认不装配；端点与编排同时由 {@code bone.masterdata.golden-record.enabled=true} 显式开启。
 * 这不是过渡实现——开关是终局的部署态治理构件，端口契约与领域语义在终局图中原位存在。
 *
 * <p><b>领域事件</b>：本用例不发布 Spring DomainEvent；分发通过 {@link MasterDataOutboxPort#append} 事务性落地，等价于 E-5.4
 * 要求的「写入必配发布」，且比进程内事件更可靠（可重放、可幂等）。写路径整体标注 {@link Transactional}，保证 xref 登记、快照+审计、Outbox
 * 追加三者同事务：任一失败全部回滚，绝不留「映射已建但黄金记录未落」的半成品。
 *
 * <p><b>租户隔离</b>：命令里的 {@code tenantId} 必须与登录态一致，缺失或不一致直接 fail-closed；API 不接受 调用方自报租户。平台管理员（tenant
 * 0）同样只作用于自己的租户上下文。
 *
 * <p><b>PII 最小化</b>：事件信封只携带 {@code payloadHash}，且摘要为<b>平台密钥参与的 HMAC-SHA256</b> 而非裸哈希。
 * 税号/统一社会信用代码等低熵标识符的裸 SHA-256 可被离线字典反推，等同于泄露；HMAC 使离线反推不可行。 密钥由 {@code
 * masterdata.golden-record.digest-pepper} 注入（注意：与 {@code @ConditionalOnProperty} 的 {@code
 * bone.masterdata.golden-record.enabled} 前缀不同，二者是两棵配置树，不可混写）。配置缺省为空串、 不含任何可用默认值；本类构造器在能力启用时校验 blank
 * 与最小长度并抛 {@link DomainException}（fail-closed）， 确保生产漏配时不会静默使用公开弱盐值。
 */
@Service
@ConditionalOnProperty(
    prefix = "bone.masterdata.golden-record",
    name = "enabled",
    havingValue = "true")
public class GoldenRecordApplicationService {

  static final String EVENT_MERGED = "GOLDEN_RECORD_MERGED";
  static final String EVENT_ROLLED_BACK = "GOLDEN_RECORD_ROLLED_BACK";
  private static final int SCHEMA_VERSION = 1;

  private final GoldenRecordPort goldenRecordPort;
  private final EntityKeyMappingPort keyMappingPort;
  private final MasterDataOutboxPort outboxPort;
  private final CurrentUserPort currentUserPort;
  private final byte[] digestPepper;
  private final GoldenRecordMerger merger = new GoldenRecordMerger();
  private final EntityKeyMappingRegistry registry = new EntityKeyMappingRegistry();

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  public GoldenRecordApplicationService(
      GoldenRecordPort goldenRecordPort,
      EntityKeyMappingPort keyMappingPort,
      MasterDataOutboxPort outboxPort,
      CurrentUserPort currentUserPort,
      @Value("${masterdata.golden-record.digest-pepper}") String digestPepper) {
    if (isBlank(digestPepper) || digestPepper.length() < 16) {
      throw new DomainException("Golden Record 摘要密钥缺失或过短，禁止产出可反推的事件摘要");
    }
    this.goldenRecordPort = goldenRecordPort;
    this.keyMappingPort = keyMappingPort;
    this.outboxPort = outboxPort;
    this.currentUserPort = currentUserPort;
    this.digestPepper = digestPepper.getBytes(StandardCharsets.UTF_8);
  }

  /** 解析 → 合并 → 落库 → 分发；只有 APPLIED 允许写入与分发，其余一律转人工且不产生任何副作用。 */
  @Transactional
  public GoldenRecordMergeResultDTO resolveAndMerge(ResolveAndMergeCommand cmd) {
    if (cmd == null || cmd.getTenantId() == null || isBlank(cmd.getMergeId())) {
      throw new DomainException("合并命令缺少 tenantId/mergeId");
    }
    long tenantId = requireSameTenant(cmd.getTenantId());
    Instant at = cmd.getMergedAt() == null ? Instant.now() : cmd.getMergedAt();
    ResolvedIdentity identity = resolveIdentity(tenantId, cmd);
    String partyId = identity.partyId();
    GoldenRecord before = goldenRecordPort.findCurrent(tenantId, partyId).orElse(null);
    long nextVersion = before == null ? 1L : before.getVersion() + 1;

    MergeResult result =
        merger.merge(toDomainCommand(cmd, tenantId, partyId, before, nextVersion, at));
    if (!result.isApplied()) {
      // 硬约束：转人工/拒绝时零落库、零分发，避免「部分应用」的黄金记录污染消费侧。
      return GoldenRecordMergeResultDTO.builder()
          .outcome(result.getOutcome().name())
          .mergeId(cmd.getMergeId())
          .partyId(partyId)
          .version(before == null ? 0L : before.getVersion())
          .reason(result.getReason())
          .dispatched(false)
          .fieldCount(0)
          .build();
    }

    GoldenRecord after = result.getGolden();
    // xref 登记必须晚于裁决：转人工/拒绝时不得留下任何映射，否则下次同本地键会被错绑到未成立的主体。
    if (identity.pendingRegistration() != null) {
      keyMappingPort.register(identity.pendingRegistration());
    }
    goldenRecordPort.appendSnapshot(after, result.getAudit());
    MasterDataRecordDispatchedEvent event =
        MasterDataRecordDispatchedEvent.of(
            "EVT-" + cmd.getMergeId(),
            tenantId,
            partyId,
            after.getVersion(),
            EVENT_MERGED,
            SCHEMA_VERSION,
            at,
            payloadHash(after));
    boolean dispatched = dispatchIfAbsent(event);
    return GoldenRecordMergeResultDTO.builder()
        .outcome(result.getOutcome().name())
        .mergeId(cmd.getMergeId())
        .partyId(partyId)
        .version(after.getVersion())
        .idempotencyKey(event.idempotencyKey())
        .payloadHash(event.getPayloadHash())
        .fieldCount(after.getValues().size())
        .dispatched(dispatched)
        .build();
  }

  /**
   * 整笔撤销误合并。
   *
   * <p><b>为什么补偿版本必须递增</b>：领域内核返回的是合并前完整快照（含旧版本）。若直接把它当当前状态落库，
   * 版本会倒退，消费侧按版本单调去重时会把补偿事件当陈旧事件丢弃。因此编排层恢复<b>内容</b>后重新签发一个 更高的版本戳；内容完整性与并发防覆盖仍由 {@link
   * GoldenRecordMerger#rollback} 裁决。
   */
  @Transactional
  public GoldenRecordMergeResultDTO rollback(RollbackMergeCommand cmd) {
    if (cmd == null
        || cmd.getTenantId() == null
        || isBlank(cmd.getMergeId())
        || isBlank(cmd.getReason())
        || isBlank(cmd.getOperator())) {
      throw new DomainException("回滚命令缺少 tenantId/mergeId/reason/operator");
    }
    long tenantId = requireSameTenant(cmd.getTenantId());
    Instant at = Instant.now();
    MergeAudit audit =
        goldenRecordPort
            .findAudit(tenantId, cmd.getMergeId())
            .orElseThrow(() -> new DomainException("合并审计不存在，无法回滚: " + cmd.getMergeId()));
    GoldenRecord current =
        goldenRecordPort
            .findCurrent(tenantId, audit.getPartyId())
            .orElseThrow(() -> new DomainException("当前 Golden Record 不存在，无法回滚"));

    Optional<GoldenRecord> restored =
        merger.rollback(audit, current, cmd.getReason(), cmd.getOperator(), at);
    long compensationVersion = current.getVersion() + 1;
    GoldenRecord forward = null;
    if (restored.isPresent()) {
      GoldenRecord before = restored.get();
      forward =
          GoldenRecord.of(
              tenantId,
              audit.getPartyId(),
              compensationVersion,
              before.getValues(),
              before.getProvenance(),
              at);
      goldenRecordPort.appendSnapshot(forward, audit);
    } else {
      // 首次建记录被撤销：回到「无」，审计墓碑（含撤销标记）必须同事务持久化，否则可重复回滚。
      goldenRecordPort.clearCurrent(tenantId, audit.getPartyId(), audit);
    }
    MasterDataRecordDispatchedEvent event =
        MasterDataRecordDispatchedEvent.of(
            "EVT-ROLLBACK-" + cmd.getMergeId(),
            tenantId,
            audit.getPartyId(),
            compensationVersion,
            EVENT_ROLLED_BACK,
            SCHEMA_VERSION,
            at,
            forward == null ? hashOf("TOMBSTONE|" + cmd.getMergeId()) : payloadHash(forward));
    boolean dispatched = dispatchIfAbsent(event);
    return GoldenRecordMergeResultDTO.builder()
        .outcome(EVENT_ROLLED_BACK)
        .mergeId(cmd.getMergeId())
        .partyId(audit.getPartyId())
        .version(compensationVersion)
        .reason(cmd.getReason())
        .idempotencyKey(event.idempotencyKey())
        .payloadHash(event.getPayloadHash())
        .fieldCount(forward == null ? 0 : forward.getValues().size())
        .dispatched(dispatched)
        .build();
  }

  /** 只读当前快照的脱敏状态（租户取登录态，API 不接受调用方自报租户）。 */
  public Optional<GoldenRecordStateDTO> currentForCaller(String partyId) {
    Long caller = currentUserPort.currentTenantId();
    if (caller == null) {
      throw new DomainException("缺少登录租户上下文，禁止读取 Golden Record");
    }
    return current(caller, partyId);
  }

  /** 只读当前快照的脱敏状态，供联调与治理后台核对落库结果。 */
  public Optional<GoldenRecordStateDTO> current(long tenantId, String partyId) {
    return goldenRecordPort
        .findCurrent(tenantId, partyId)
        .map(
            r ->
                GoldenRecordStateDTO.builder()
                    .partyId(r.getPartyId())
                    .version(r.getVersion())
                    .fieldCount(r.getValues().size())
                    .fieldCodes(new ArrayList<>(r.fieldCodes()))
                    .build());
  }

  /** 解析结果：统一身份 + 待登记映射（仅在 APPLIED 后由编排层落库）。 */
  private record ResolvedIdentity(String partyId, EntityKeyMapping pendingRegistration) {}

  /** 命令租户与登录态一致才放行；不允许调用方自报租户读写他人黄金记录。 */
  private long requireSameTenant(Long requested) {
    Long caller = currentUserPort.currentTenantId();
    if (caller == null) {
      throw new DomainException("缺少登录租户上下文，禁止 Golden Record 治理操作");
    }
    if (requested == null || requested.longValue() != caller.longValue()) {
      throw new DomainException("命令 tenantId 与登录租户不一致，禁止跨租户治理");
    }
    return caller;
  }

  /** xref 解析；本地编码只是查表键，绝不成为身份真源。冲突、无映射、跨租户一律 fail-closed。 */
  private ResolvedIdentity resolveIdentity(long tenantId, ResolveAndMergeCommand cmd) {
    boolean hasLocal = !isBlank(cmd.getSourceSystem()) && !isBlank(cmd.getLocalId());
    if (!hasLocal) {
      if (isBlank(cmd.getPartyId())) {
        throw new DomainException("缺少 partyId 且无本地编码可供 xref 解析");
      }
      return new ResolvedIdentity(cmd.getPartyId(), null);
    }
    List<EntityKeyMapping> existing =
        keyMappingPort.findByLocalKey(tenantId, cmd.getSourceSystem(), cmd.getLocalId());
    Optional<String> resolved =
        registry.resolve(tenantId, cmd.getSourceSystem(), cmd.getLocalId(), existing);
    if (resolved.isPresent()) {
      if (!isBlank(cmd.getPartyId()) && !resolved.get().equals(cmd.getPartyId())) {
        throw new DomainException("xref 解析出的 partyId 与命令显式 partyId 不一致，禁止错并主体");
      }
      return new ResolvedIdentity(resolved.get(), null);
    }
    if (isBlank(cmd.getPartyId())) {
      throw new DomainException("本地编码无有效映射且未提供 partyId，fail-closed");
    }
    EntityKeyMapping candidate =
        EntityKeyMapping.active(
            tenantId, cmd.getPartyId(), cmd.getSourceSystem(), cmd.getLocalId());
    EntityKeyMapping registered = registry.register(existing, candidate);
    return new ResolvedIdentity(registered.getPartyId(), registered);
  }

  private MergeCommand toDomainCommand(
      ResolveAndMergeCommand cmd,
      long tenantId,
      String partyId,
      GoldenRecord before,
      long nextVersion,
      Instant at) {
    List<SourceRecord> sources = new ArrayList<>();
    if (cmd.getSources() != null) {
      for (ResolveAndMergeCommand.SourceRecordSpec spec : cmd.getSources()) {
        sources.add(
            SourceRecord.of(
                spec.getRecordId(),
                spec.getSourceSystem(),
                tenantId,
                spec.getTrustRank(),
                spec.getObservedAt() == null ? at : spec.getObservedAt(),
                spec.getValues() == null ? Map.of() : spec.getValues()));
      }
    }
    Map<String, FieldSurvivorshipRule> rules = new LinkedHashMap<>();
    if (cmd.getStrategies() != null) {
      for (Map.Entry<String, String> e : cmd.getStrategies().entrySet()) {
        final Strategy strategy;
        try {
          strategy = Strategy.valueOf(e.getValue());
        } catch (RuntimeException ex) {
          throw new DomainException("非法胜出策略: " + e.getValue());
        }
        rules.put(e.getKey(), FieldSurvivorshipRule.of(e.getKey(), strategy));
      }
    }
    return MergeCommand.of(
        cmd.getMergeId(),
        tenantId,
        partyId,
        before,
        nextVersion,
        sources,
        SurvivorshipPolicy.of(rules),
        cmd.getStewardOverrides() == null ? Map.of() : cmd.getStewardOverrides(),
        cmd.getStewardAccountId(),
        at);
  }

  private boolean dispatchIfAbsent(MasterDataRecordDispatchedEvent event) {
    if (outboxPort.existsByIdempotencyKey(event.idempotencyKey())) {
      return false;
    }
    outboxPort.append(event);
    return true;
  }

  /** 规范化摘要：字段按码排序拼接后取带密钥 HMAC-SHA256；字段值不进入消息总线，低熵 PII 亦不可离线反推。 */
  private String payloadHash(GoldenRecord record) {
    StringBuilder canonical = new StringBuilder();
    record.getValues().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> canonical.append(e.getKey()).append('=').append(e.getValue()).append('\n'));
    canonical.append("@version=").append(record.getVersion());
    return hashOf(canonical.toString());
  }

  private String hashOf(String input) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(digestPepper, HMAC_ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException ex) {
      throw new DomainException("HMAC-SHA256 不可用，禁止产出无摘要分发事件");
    }
  }

  private static boolean isBlank(String v) {
    return v == null || v.trim().isEmpty();
  }
}
