package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 映射批量导入的 Excel 解析与模板生成（.xlsx）。
 *
 * <p><b>列契约</b>：A=保司码值，B=保司名称，C=标准码。首行固定表头；模板含一行示例 （保司码值以 {@code SAMPLE-} 开头），导入时跳过——示例行混进真实数据是最常见的
 * 「导入 100 行实际 99 行」事故来源。
 *
 * <p><b>内容指纹</b>：对「主体+域+逐行三元组（排序后）」做 SHA-256，作为 {@code version_no} 发布批次。重复上传内容完全相同的文件会命中同一批次 →
 * 幂等返回首次导入结果， 不产生新版本（复用 version_no 而不建指纹表，发布批次与导入批次天然合一）。
 */
final class MappingImportExcel {

  static final int MAX_ROWS = 2000;
  static final String SAMPLE_PREFIX = "SAMPLE-";
  private static final String[] HEADERS = {"保司码值", "保司名称", "标准码"};

  private MappingImportExcel() {}

  /** 一行原始数据（保司名称仅留档，不参与解析）。 */
  record ImportRow(
      int rowNo, String insurerItemCode, String insurerItemName, String standardCode) {}

  static List<ImportRow> parse(String insurerCode, String domain, byte[] bytes) {
    List<ImportRow> rows = new ArrayList<>();
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = workbook.getSheetAt(0);
      for (Row row : sheet) {
        if (row.getRowNum() == 0) {
          continue; // 表头
        }
        String itemCode = cellString(row.getCell(0));
        if (itemCode.isEmpty()) {
          continue; // 空行
        }
        if (itemCode.startsWith(SAMPLE_PREFIX)) {
          continue; // 模板示例行
        }
        rows.add(
            new ImportRow(
                row.getRowNum() + 1,
                itemCode,
                cellString(row.getCell(1)),
                cellString(row.getCell(2))));
      }
    } catch (IOException | RuntimeException e) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_MAPPING_IMPORT_REJECTED, "文件解析失败: " + e.getMessage());
    }
    if (rows.size() > MAX_ROWS) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_MAPPING_IMPORT_REJECTED,
          "单批导入上限 " + MAX_ROWS + " 行，当前 " + rows.size() + " 行，请拆分文件");
    }
    return rows;
  }

  private static String cellString(Cell cell) {
    if (cell == null) {
      return "";
    }
    if (cell.getCellType() == CellType.NUMERIC) {
      // 码值被 Excel 识别为数字时按整数字符串读回，避免 "00123" 变 "123"
      double v = cell.getNumericCellValue();
      return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
    return cell.getCellType() == CellType.STRING ? cell.getStringCellValue().trim() : "";
  }

  /** 模板：列头 + 一行示例。 */
  static byte[] template() {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("映射导入模板");
      Row header = sheet.createRow(0);
      for (int i = 0; i < HEADERS.length; i++) {
        header.createCell(i).setCellValue(HEADERS[i]);
      }
      Row sample = sheet.createRow(1);
      sample.createCell(0).setCellValue(SAMPLE_PREFIX + "001");
      sample.createCell(1).setCellValue("示例：保司侧码值名称（导入时自动跳过本行）");
      sample.createCell(2).setCellValue("STD001");
      for (int i = 0; i < HEADERS.length; i++) {
        sheet.setColumnWidth(i, 20 * 256);
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_MAPPING_IMPORT_REJECTED, "模板生成失败");
    }
  }

  /** 内容指纹（导入批次 = 发布批次）：同文件重复上传命中同一 version_no，即幂等。 排序后哈希，行顺序不同的相同内容视为同一文件。 */
  static String fingerprint(String insurerCode, String domain, List<ImportRow> rows) {
    List<String> lines = new ArrayList<>();
    for (ImportRow row : rows) {
      lines.add(row.insurerItemCode() + "\t" + row.standardCode());
    }
    lines.sort(String::compareTo);
    String payload = insurerCode + "\n" + domain + "\n" + String.join("\n", lines);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return "IMP-"
          + HexFormat.of()
              .formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)))
              .substring(0, 24);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }
}
