package com.bone.claim.domain.model.liability;

import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * 理算责任因子矩阵（{@code t_claim_factor_matrix}，一行一格）。
 *
 * <p><b>结构变更保留已填值</b>：{@link #replaceCells(List)} 以 {@code (rowKey, colKey)} 为自然键做 merge——新增行列带入新值，
 * 已填过的格子沿用旧值，绝不因为重画结构而清空。这是该聚合的核心不变量， 判别用例见 {@code FactorMatrixTest}。
 *
 * <p><b>发布即冻结</b>：{@link #publish()} 后矩阵进入 PUBLISHED，任何 {@code replaceCells}/{@code
 * updateCellValue} 都被拒绝，避免「预览确认的」与「理算消费的」分叉。
 */
@Getter
@Table("t_claim_factor_matrix")
public class FactorMatrix extends TenantAggregateRoot<Long> {

  public static final String STATUS_DRAFT = "DRAFT";
  public static final String STATUS_PUBLISHED = "PUBLISHED";

  public static final String DEFAULT_COL_KEY = "DEFAULT";

  @Column(name = "liability_id", nullable = false)
  private Long liabilityId;

  @Column(name = "liability_uuid", nullable = false)
  private String liabilityUuid;

  @Column(name = "dimension_set", nullable = false)
  private String dimensionSet;

  @Column(name = "row_key", nullable = false)
  private String rowKey;

  @Column(name = "col_key", nullable = false)
  private String colKey = DEFAULT_COL_KEY;

  @Column(name = "cell_value", nullable = false)
  private BigDecimal cellValue;

  /** 矩阵版本号（业务版本，随唯一键的一部分；非乐观锁，故不加 {@code @Version}）。 */
  @Column(name = "version", nullable = false)
  private Integer version = 1;

  @Column(name = "status", nullable = false)
  private String status = STATUS_DRAFT;

  @Column(name = "created_by")
  private String createdBy = "";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_by")
  private String updatedBy = "";

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted", nullable = false)
  private Integer deleted = 0;

  /** 内存矩阵视图（不落库；持久化时由 application 按行 save）。 */
  @Transient private final List<Cell> cells = new ArrayList<>();

  /** 单元格：自然键 (rowKey, colKey) + 格值。 */
  public record Cell(String rowKey, String colKey, BigDecimal value) {

    public static Cell of(String rowKey, String colKey, BigDecimal value) {
      if (rowKey == null || rowKey.isBlank()) {
        throw new IllegalArgumentException("矩阵行键不能为空");
      }
      return new Cell(
          rowKey.trim(),
          colKey == null || colKey.isBlank() ? DEFAULT_COL_KEY : colKey.trim(),
          value);
    }

    private String naturalKey() {
      return rowKey + "\u0000" + colKey;
    }
  }

  /** 一行一格落库实体工厂。 */
  public static FactorMatrix of(
      Long id,
      Long tenantId,
      Long liabilityId,
      String liabilityUuid,
      String dimensionSet,
      String rowKey,
      String colKey,
      BigDecimal cellValue,
      Integer version) {
    if (liabilityId == null) {
      throw new IllegalArgumentException("因子矩阵必须挂责任");
    }
    if (dimensionSet == null || dimensionSet.isBlank()) {
      throw new IllegalArgumentException("因子组合码不能为空");
    }
    if (rowKey == null || rowKey.isBlank()) {
      throw new IllegalArgumentException("矩阵行键不能为空");
    }
    if (cellValue == null) {
      throw new IllegalArgumentException("矩阵格值不能为空");
    }
    FactorMatrix m = new FactorMatrix();
    m.setId(id);
    m.setTenantId(tenantId);
    m.liabilityId = liabilityId;
    m.liabilityUuid = liabilityUuid == null ? "" : liabilityUuid.trim();
    m.dimensionSet = dimensionSet.trim();
    Cell cell = Cell.of(rowKey, colKey, cellValue);
    m.rowKey = cell.rowKey();
    m.colKey = cell.colKey();
    m.cellValue = cell.value();
    m.version = version == null ? 1 : version;
    m.status = STATUS_DRAFT;
    Instant now = Instant.now();
    m.createdAt = now;
    m.updatedAt = now;
    return m;
  }

  /** 矩阵壳工厂（仅承载内存 cells，不直接落库）。 */
  public static FactorMatrix draft(
      Long tenantId, Long liabilityId, String liabilityUuid, String dimensionSet, Integer version) {
    FactorMatrix m = new FactorMatrix();
    m.setTenantId(tenantId);
    m.liabilityId = liabilityId;
    m.liabilityUuid = liabilityUuid == null ? "" : liabilityUuid.trim();
    m.dimensionSet = dimensionSet == null ? "" : dimensionSet.trim();
    m.version = version == null ? 1 : version;
    m.status = STATUS_DRAFT;
    Instant now = Instant.now();
    m.createdAt = now;
    m.updatedAt = now;
    return m;
  }

  /** 当前行对应的单元格（供 application 组装内存矩阵）。 */
  public Cell asCell() {
    return Cell.of(rowKey, colKey, cellValue);
  }

  /** 只读内存视图。 */
  public List<Cell> cells() {
    return Collections.unmodifiableList(cells);
  }

  /**
   * 按 {@code (rowKey, colKey)} 合并单元格：结构变更时保留已填值。
   *
   * <p>自然键已存在且旧值非 null ⇒ 保留旧值（新结构只负责「画线」，不覆盖人工填过的格）； 自然键不存在 ⇒ 新增格。发布后的矩阵拒绝任何改动。
   */
  public void replaceCells(List<Cell> incoming) {
    ensureEditable();
    if (incoming == null || incoming.isEmpty()) {
      return;
    }
    Map<String, Cell> merged = new LinkedHashMap<>();
    for (Cell existing : cells) {
      merged.put(existing.naturalKey(), existing);
    }
    for (Cell cell : incoming) {
      if (cell == null) {
        continue;
      }
      Cell old = merged.get(cell.naturalKey());
      if (old != null && old.value() != null) {
        continue;
      }
      merged.put(cell.naturalKey(), cell);
    }
    cells.clear();
    cells.addAll(merged.values());
    Cell first = cells.get(0);
    this.rowKey = first.rowKey();
    this.colKey = first.colKey();
    this.cellValue = first.value();
    this.updatedAt = Instant.now();
  }

  /** 单行格值更新（同样受发布冻结约束）。 */
  public void updateCellValue(BigDecimal value) {
    ensureEditable();
    if (value == null) {
      throw new IllegalArgumentException("矩阵格值不能为空");
    }
    this.cellValue = value;
    this.updatedAt = Instant.now();
  }

  public void publish() {
    this.status = STATUS_PUBLISHED;
    this.updatedAt = Instant.now();
  }

  public boolean isPublished() {
    return STATUS_PUBLISHED.equals(this.status);
  }

  /** 领域守卫：发布后不可改。 */
  public void ensureEditable() {
    if (isPublished()) {
      throw new IllegalStateException("已发布的因子矩阵不可修改，请新建版本");
    }
  }
}
