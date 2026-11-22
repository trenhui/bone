package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.liability.FactorMatrix;
import com.bone.claim.domain.model.liability.FactorMatrix.Cell;
import com.bone.claim.domain.repository.FactorMatrixRepository;
import com.bone.core.annotation.NoDomainEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 因子矩阵用例：保存（按行 merge）/ 发布（冻结）/ 加载 / 受控公式生成。 */
@Service
@NoDomainEvent
public class FactorMatrixApplicationService {

  /** 受控 token：因子标识只能是大写标识符，杜绝自由文本进入公式。 */
  private static final Pattern FACTOR_TOKEN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  private static final String DEFAULT_LEAF = "DEFAULT";

  public record CellInput(String rowKey, String colKey, BigDecimal value) {}

  public record MatrixView(
      Long liabilityId, String dimensionSet, Integer version, String status, List<Cell> cells) {}

  public record FormulaView(String expression, boolean valid) {}

  private final FactorMatrixRepository repository;
  private final TenantPort tenantPort;

  public FactorMatrixApplicationService(FactorMatrixRepository repository, TenantPort tenantPort) {
    this.repository = repository;
    this.tenantPort = tenantPort;
  }

  /** 保存矩阵：按 (rowKey,colKey) merge，已填值保留；已发布的版本拒绝改动。 */
  @Transactional
  public MatrixView save(
      Long liabilityId, String dimensionSet, Integer version, List<CellInput> inputs) {
    long tenantId = tenantPort.currentTenantId();
    List<FactorMatrix> existing =
        repository.findByLiabilityAndVersion(tenantId, liabilityId, dimensionSet, version);
    for (FactorMatrix row : existing) {
      row.ensureEditable();
    }

    FactorMatrix matrix =
        existing.isEmpty()
            ? FactorMatrix.draft(tenantId, liabilityId, "", dimensionSet, version)
            : existing.get(0);
    matrix.replaceCells(existing.stream().map(FactorMatrix::asCell).toList());
    List<Cell> incoming = new ArrayList<>();
    if (inputs != null) {
      for (CellInput input : inputs) {
        if (input != null) {
          incoming.add(Cell.of(input.rowKey(), input.colKey(), input.value()));
        }
      }
    }
    matrix.replaceCells(incoming);

    Map<String, FactorMatrix> rowsByKey = new LinkedHashMap<>();
    for (FactorMatrix row : existing) {
      rowsByKey.put(cellKey(row.getRowKey(), row.getColKey()), row);
    }
    for (Cell cell : matrix.cells()) {
      FactorMatrix row = rowsByKey.get(cellKey(cell.rowKey(), cell.colKey()));
      if (row == null) {
        row =
            FactorMatrix.of(
                null,
                tenantId,
                liabilityId,
                "",
                dimensionSet,
                cell.rowKey(),
                cell.colKey(),
                cell.value(),
                version);
      } else {
        row.updateCellValue(cell.value());
      }
      repository.save(row);
    }
    return toView(matrix);
  }

  /** 发布：整版 DRAFT → PUBLISHED，之后该版本不可再改。 */
  @Transactional
  public int publish(Long liabilityId, String dimensionSet, Integer version) {
    long tenantId = tenantPort.currentTenantId();
    List<FactorMatrix> rows =
        repository.findByLiabilityAndVersion(tenantId, liabilityId, dimensionSet, version);
    int published = 0;
    for (FactorMatrix row : rows) {
      if (!row.isPublished()) {
        row.publish();
        repository.save(row);
        published++;
      }
    }
    return published;
  }

  /**
   * 加载矩阵视图。
   *
   * <p><b>状态取自真实行而非 {@code draft()} 壳</b>：发布是整版操作，全部行 PUBLISHED 才对外显示 PUBLISHED；任一行仍 DRAFT 则显示
   * DRAFT ——如实暴露"有未发布改动"，避免用户 publish 后刷新仍看到 DRAFT 误判发布失败而重复发布。此前用 {@code draft()} 造壳（status 恒
   * DRAFT）+ {@code replaceCells()} 只并格不同步状态，导致已发布版本 load 出来恒 DRAFT，是确定性缺陷。
   */
  @Transactional(readOnly = true)
  public MatrixView load(Long liabilityId, String dimensionSet, Integer version) {
    long tenantId = tenantPort.currentTenantId();
    List<FactorMatrix> rows =
        repository.findByLiabilityAndVersion(tenantId, liabilityId, dimensionSet, version);
    FactorMatrix matrix = FactorMatrix.draft(tenantId, liabilityId, "", dimensionSet, version);
    matrix.replaceCells(rows.stream().map(FactorMatrix::asCell).toList());
    String status = deriveStatus(rows);
    return new MatrixView(
        matrix.getLiabilityId(),
        matrix.getDimensionSet(),
        matrix.getVersion(),
        status,
        matrix.cells());
  }

  /** 全部行已发布才算 PUBLISHED；空矩阵或任一行未发布均为 DRAFT（不虚报生效）。 */
  private static String deriveStatus(List<FactorMatrix> rows) {
    if (rows.isEmpty()) {
      return FactorMatrix.STATUS_DRAFT;
    }
    return rows.stream().allMatch(FactorMatrix::isPublished)
        ? FactorMatrix.STATUS_PUBLISHED
        : FactorMatrix.STATUS_DRAFT;
  }

  /**
   * 受控公式生成：仅用受控 token 拼装 IF/AND 表达式，括号配对失败或出现非法因子标识一律 400（不新增错误码）。
   *
   * <p>入参 cell.rowKey 形态为 {@code FACTOR=value;FACTOR=value}；只接受已声明因子且不含引号/括号的值，
   * 因此返回表达式中绝无用户自由文本可直接落库——本方法也不写库。
   */
  @Transactional(readOnly = true)
  public FormulaView generateFormula(List<String> factors, List<CellInput> cells) {
    if (factors == null || factors.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT, "因子组合不能为空");
    }
    List<String> allowed = new ArrayList<>();
    for (String factor : factors) {
      if (factor == null || !FACTOR_TOKEN.matcher(factor.trim()).matches()) {
        throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT, "非法因子标识: " + factor);
      }
      allowed.add(factor.trim());
    }
    boolean valid = cells != null && !cells.isEmpty();
    List<String> conditions = new ArrayList<>();
    List<String> values = new ArrayList<>();
    if (cells != null) {
      for (CellInput cell : cells) {
        if (cell == null || cell.value() == null) {
          valid = false;
          continue;
        }
        List<String> assignments = new ArrayList<>();
        String rowKey = cell.rowKey() == null ? "" : cell.rowKey();
        for (String part : rowKey.split(";")) {
          int eq = part.indexOf('=');
          if (eq <= 0) {
            valid = false;
            continue;
          }
          String key = part.substring(0, eq).trim();
          String value = part.substring(eq + 1).trim();
          if (!FACTOR_TOKEN.matcher(key).matches()
              || !allowed.contains(key)
              || value.isEmpty()
              || value.contains("'")
              || value.contains("(")
              || value.contains(")")) {
            valid = false;
            continue;
          }
          assignments.add(key + "='" + value + "'");
        }
        if (assignments.isEmpty()) {
          valid = false;
          continue;
        }
        conditions.add(
            assignments.size() == 1
                ? assignments.get(0)
                : "AND(" + String.join(",", assignments) + ")");
        values.add(cell.value().toPlainString());
      }
    }
    String expression = DEFAULT_LEAF;
    for (int i = conditions.size() - 1; i >= 0; i--) {
      expression =
          "IF(" + conditions.get(i) + "," + values.get(i) + "," + expression + ")"; // 受控 token 拼装
    }
    if (!balanced(expression)) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT, "公式括号不配对");
    }
    return new FormulaView(expression, valid);
  }

  private static boolean balanced(String expression) {
    int depth = 0;
    for (int i = 0; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth < 0) {
          return false;
        }
      }
    }
    return depth == 0;
  }

  private static String cellKey(String rowKey, String colKey) {
    return rowKey + "\u0000" + colKey;
  }

  private static MatrixView toView(FactorMatrix matrix) {
    return new MatrixView(
        matrix.getLiabilityId(),
        matrix.getDimensionSet(),
        matrix.getVersion(),
        matrix.getStatus(),
        matrix.cells());
  }
}
