package com.bone.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.tenant.TenantAbstractDTO;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * DTO 基类测试：{@link AbstractDTO} 审计字段默认值 / 全参构造 / 值相等，以及 {@link TenantAbstractDTO} 的租户扩展。
 *
 * <p>这两个基类被各模块 DTO 广泛继承，{@code deleted} 默认值与 {@code callSuper} 相等语义一旦变化， 会影响软删除判定与集合去重，故固化。
 */
/**
 * 注意：本类<b>不可命名为 {@code AbstractDtoTest}</b>——Maven Surefire 默认排除 {@code
 * Abstract*Test.java}，会导致用例静默不执行（历史上 {@code AbstractEntityTest} 即因此从未运行）。
 */
class DtoBaseTest {

  private static final Date FIXED_TIME = new Date(1_700_000_000_000L);

  @Test
  void defaultConstructor_initializesSoftDeleteFalse() {
    SampleDto dto = new SampleDto();

    assertThat(dto.getId()).isNull();
    assertThat(dto.getCreatedAt()).isNull();
    assertThat(dto.getCreatedBy()).isNull();
    assertThat(dto.getUpdatedAt()).isNull();
    assertThat(dto.getUpdatedBy()).isNull();
    // 关键契约：软删标记默认为 false，否则新对象会被误判为已删除
    assertThat(dto.getDeleted()).isFalse();
  }

  @Test
  void allArgsConstructor_populatesAuditFields() {
    Date now = new Date();
    SampleDto dto = new SampleDto(1L, now, 100L, now, 200L, true);

    assertThat(dto.getId()).isEqualTo(1L);
    assertThat(dto.getCreatedAt()).isEqualTo(now);
    assertThat(dto.getCreatedBy()).isEqualTo(100L);
    assertThat(dto.getUpdatedAt()).isEqualTo(now);
    assertThat(dto.getUpdatedBy()).isEqualTo(200L);
    assertThat(dto.getDeleted()).isTrue();
  }

  @Test
  void setters_updateAuditFields() {
    SampleDto dto = new SampleDto();
    Date now = new Date();

    dto.setId(9L);
    dto.setCreatedAt(now);
    dto.setCreatedBy(1L);
    dto.setUpdatedAt(now);
    dto.setUpdatedBy(2L);
    dto.setDeleted(true);

    assertThat(dto.getId()).isEqualTo(9L);
    assertThat(dto.getCreatedAt()).isEqualTo(now);
    assertThat(dto.getCreatedBy()).isEqualTo(1L);
    assertThat(dto.getUpdatedAt()).isEqualTo(now);
    assertThat(dto.getUpdatedBy()).isEqualTo(2L);
    assertThat(dto.getDeleted()).isTrue();
  }

  @Test
  void equalityIsValueBased() {
    Date now = new Date();
    SampleDto a = new SampleDto(1L, now, 1L, now, 1L, false);
    SampleDto b = new SampleDto(1L, now, 1L, now, 1L, false);
    SampleDto c = new SampleDto(2L, now, 1L, now, 1L, false);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a).isEqualTo(a);
  }

  @Test
  void toStringContainsKeyFields() {
    assertThat(new SampleDto(1L, null, null, null, null, false).toString()).contains("1");
  }

  @Test
  void tenantDtoAddsTenantFields() {
    TenantAbstractDTO<Long> dto = new TenantAbstractDTO<Long>();
    dto.setId(1L);
    dto.setTenantId(1001L);
    dto.setTenantCode("T001");

    assertThat(dto.getTenantId()).isEqualTo(1001L);
    assertThat(dto.getTenantCode()).isEqualTo("T001");
    assertThat(dto.getDeleted()).isFalse();
  }

  /**
   * {@code callSuper=true}：相等判定须包含基类审计字段，不能只看租户字段。
   *
   * <p><b>Lombok 注意</b>：{@code @AllArgsConstructor} <b>只覆盖本类字段</b>（此处仅 tenantId / tenantCode），
   * 不含继承的审计字段，因此审计字段只能用 setter 赋值——这也是本用例不使用全参构造的原因。
   */
  @Test
  void tenantDtoEqualityIncludesInheritedFields() {
    TenantAbstractDTO<Long> a = tenantDto(1L, 1001L, "T001");
    TenantAbstractDTO<Long> b = tenantDto(1L, 1001L, "T001");
    TenantAbstractDTO<Long> diffTenant = tenantDto(1L, 1002L, "T002");
    TenantAbstractDTO<Long> diffBase = tenantDto(2L, 1001L, "T001");

    assertThat(a.getTenantId()).isEqualTo(1001L);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(diffTenant);
    assertThat(a).isNotEqualTo(diffBase);
    assertThat(a.toString()).contains("T001");
  }

  private static TenantAbstractDTO<Long> tenantDto(Long id, Long tenantId, String tenantCode) {
    TenantAbstractDTO<Long> dto = new TenantAbstractDTO<>();
    dto.setId(id);
    dto.setCreatedAt(FIXED_TIME);
    dto.setCreatedBy(1L);
    dto.setUpdatedAt(FIXED_TIME);
    dto.setUpdatedBy(1L);
    dto.setDeleted(false);
    dto.setTenantId(tenantId);
    dto.setTenantCode(tenantCode);
    return dto;
  }

  /** 测试用 DTO：暴露 AbstractDTO 的全参构造器以便覆盖审计字段装配。 */
  static class SampleDto extends AbstractDTO<Long> {
    private String name;

    public SampleDto() {}

    public SampleDto(
        Long id, Date createdAt, Long createdBy, Date updatedAt, Long updatedBy, Boolean deleted) {
      super(id, createdAt, createdBy, updatedAt, updatedBy, deleted);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
