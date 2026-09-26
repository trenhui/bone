package com.bone.masterdata.application;

import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.CreateReferenceSetCommand;
import com.bone.masterdata.application.command.CreateReferenceValueCommand;
import com.bone.masterdata.application.command.UpdateReferenceSetCommand;
import com.bone.masterdata.application.command.UpdateReferenceValueCommand;
import com.bone.masterdata.application.query.dto.ReferenceValueView;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.model.reference.ReferenceSet;
import com.bone.masterdata.domain.model.reference.ReferenceValue;
import com.bone.masterdata.domain.model.reference.TenantReferenceValue;
import com.bone.masterdata.domain.repository.ReferenceSetRepository;
import com.bone.masterdata.domain.repository.ReferenceValueRepository;
import com.bone.masterdata.domain.repository.TenantReferenceValueRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参考数据应用服务（G15，§2.3）——overlay 模式（2026-09-26 裁决，多租户规范 §8 约束 6）：
 *
 * <ul>
 *   <li><strong>值域（set）是平台全局目录</strong>：只有平台管理员（当前租户 = 0）可建/改/归档；租户越权写 抛 {@code
 *       MD_REF_PLATFORM_SET_IMMUTABLE}（403）。
 *   <li><strong>值按当前租户分发</strong>：平台管理员写平台值表 {@code mdm_reference_value}（对全体租户可见）； 租户写私有扩展值表 {@code
 *       mdm_reference_value_tenant}（SDK 严格租户过滤，他租户不可见）。
 *   <li><strong>读取按值域合并</strong>：平台值 + 当前租户私有值 = 纯并集（{@code value_code} 在值域内跨两层查重， 不允许租户值覆盖平台值编码），无
 *       shadowing 歧义。
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReferenceDataApplicationService {

  private static final long PLATFORM_TENANT = 0L;

  private final ReferenceSetRepository setRepository;
  private final ReferenceValueRepository valueRepository;
  private final TenantReferenceValueRepository tenantValueRepository;
  private final CurrentUserPort currentUser;

  // ---------- 值域：平台全局目录，仅平台管理员可写 ----------

  @Transactional
  public Long createSet(CreateReferenceSetCommand cmd) {
    requirePlatformScope("值域为平台目录，仅平台管理员可创建");
    if (setRepository.countBySetCode(cmd.getSetCode()) > 0) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.REF_SET_DUPLICATE, "值域编码已存在: " + cmd.getSetCode());
    }
    return setRepository.insert(
        ReferenceSet.create(
            DistributedIdGenerator.generateLongId(),
            cmd.getSetCode(),
            cmd.getSetName(),
            cmd.getExternalStandard(),
            cmd.getDescription()));
  }

  @Transactional
  public void updateSet(UpdateReferenceSetCommand cmd) {
    requirePlatformScope("值域为平台目录，仅平台管理员可修改");
    ReferenceSet set = requireSet(cmd.getId());
    set.update(cmd.getSetName(), cmd.getExternalStandard(), cmd.getDescription());
    setRepository.update(set);
  }

  @Transactional
  public void archiveSet(Long id) {
    requirePlatformScope("值域为平台目录，仅平台管理员可归档");
    ReferenceSet set = requireSet(id);
    set.archive();
    setRepository.update(set);
  }

  // ---------- 值：按当前租户分发到平台层 / 租户层 ----------

  @Transactional
  public Long createValue(CreateReferenceValueCommand cmd) {
    Long tenantId = currentUser.requireTenantId();
    requireSet(cmd.getSetId());
    ensureValueCodeAvailable(cmd.getSetId(), cmd.getValueCode());
    if (tenantId == PLATFORM_TENANT) {
      return valueRepository.insert(
          ReferenceValue.create(
              DistributedIdGenerator.generateLongId(),
              cmd.getSetId(),
              cmd.getValueCode(),
              cmd.getValueName(),
              cmd.getExternalCode(),
              cmd.getSortOrder()));
    }
    return tenantValueRepository.insert(
        TenantReferenceValue.create(
            DistributedIdGenerator.generateLongId(),
            cmd.getSetId(),
            cmd.getValueCode(),
            cmd.getValueName(),
            cmd.getExternalCode(),
            cmd.getSortOrder()));
  }

  @Transactional
  public void updateValue(UpdateReferenceValueCommand cmd) {
    Long tenantId = currentUser.requireTenantId();
    if (tenantId == PLATFORM_TENANT) {
      ReferenceValue value = requirePlatformValue(cmd.getId());
      value.update(cmd.getValueName(), cmd.getExternalCode(), cmd.getSortOrder());
      valueRepository.update(value);
      return;
    }
    TenantReferenceValue value = tenantValueRepository.findById(cmd.getId());
    if (value != null) {
      value.update(cmd.getValueName(), cmd.getExternalCode(), cmd.getSortOrder());
      tenantValueRepository.update(value);
      return;
    }
    throw platformValueGuardOrNotFound(cmd.getId());
  }

  @Transactional
  public void disableValue(Long id) {
    Long tenantId = currentUser.requireTenantId();
    if (tenantId == PLATFORM_TENANT) {
      ReferenceValue value = requirePlatformValue(id);
      value.disable();
      valueRepository.update(value);
      return;
    }
    TenantReferenceValue value = tenantValueRepository.findById(id);
    if (value != null) {
      value.disable();
      tenantValueRepository.update(value);
      return;
    }
    throw platformValueGuardOrNotFound(id);
  }

  // ---------- 读取：值域列表全局可见；值按值域合并两层 ----------

  @Transactional(readOnly = true)
  public List<ReferenceSet> sets() {
    return setRepository.findByStatus("PUBLISHED");
  }

  @Transactional(readOnly = true)
  public List<ReferenceValueView> values(Long setId) {
    requireSet(setId);
    List<ReferenceValueView> merged = new ArrayList<>();
    for (ReferenceValue value : valueRepository.findBySetId(setId)) {
      merged.add(ReferenceValueView.fromPlatform(value));
    }
    // 租户上下文缺失（系统任务）时只返回平台值；租户通道是严格过滤的，不能在无上下文下查询
    Long tenantId = currentUser.currentTenantId();
    if (tenantId != null && tenantId != PLATFORM_TENANT) {
      for (TenantReferenceValue value : tenantValueRepository.findBySetId(setId)) {
        merged.add(ReferenceValueView.fromTenant(value));
      }
    }
    merged.sort(
        Comparator.comparing(ReferenceValueView::getSortOrder)
            .thenComparing(ReferenceValueView::getValueCode));
    return merged;
  }

  // ---------- 内部 ----------

  private void requirePlatformScope(String message) {
    Long tenantId = currentUser.requireTenantId();
    if (tenantId != PLATFORM_TENANT) {
      throw MasterDataErrors.of(MasterDataErrorCodes.REF_PLATFORM_SET_IMMUTABLE, message);
    }
  }

  /** 跨两层查重：平台值表 + 租户私有值表都不允许同值域下重码（值编码在值域内全局唯一）。 */
  private void ensureValueCodeAvailable(Long setId, String valueCode) {
    if (valueRepository.countBySetIdAndValueCode(setId, valueCode) > 0
        || tenantValueRepository.countBySetIdAndValueCode(setId, valueCode) > 0) {
      throw MasterDataErrors.of(MasterDataErrorCodes.REF_VALUE_DUPLICATE, "值编码已存在: " + valueCode);
    }
  }

  private ReferenceValue requirePlatformValue(Long id) {
    ReferenceValue value = valueRepository.findById(id);
    if (value == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.REF_SET_NOT_FOUND, "参考数据值不存在: " + id);
    }
    return value;
  }

  /** 租户通道未命中时：目标若是平台值给出明确越权语义，否则按不存在处理。 */
  private BizException platformValueGuardOrNotFound(Long id) {
    if (valueRepository.findById(id) != null) {
      return MasterDataErrors.of(
          MasterDataErrorCodes.REF_PLATFORM_VALUE_IMMUTABLE, "平台值为平台域维护，租户不可修改: " + id);
    }
    return MasterDataErrors.of(MasterDataErrorCodes.REF_SET_NOT_FOUND, "参考数据值不存在: " + id);
  }

  private ReferenceSet requireSet(Long id) {
    ReferenceSet set = setRepository.findById(id);
    if (set == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.REF_SET_NOT_FOUND, "值域不存在: " + id);
    }
    return set;
  }
}
