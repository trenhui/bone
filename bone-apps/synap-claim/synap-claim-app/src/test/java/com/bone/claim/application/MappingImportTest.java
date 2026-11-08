package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.CodeMappingApplicationService.ImportResultView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.mapping.ClaimInsurer;
import com.bone.claim.domain.model.mapping.CodeMapping;
import com.bone.claim.domain.model.mapping.MappingSource;
import com.bone.claim.domain.model.mapping.MappingType;
import com.bone.claim.domain.model.mapping.MasterDataDomain;
import com.bone.claim.domain.model.mapping.MatchRule;
import com.bone.claim.domain.model.reference.RefHospital;
import com.bone.claim.domain.repository.CodeMappingRepository;
import com.bone.claim.domain.repository.FieldBindingRepository;
import com.bone.claim.domain.repository.InsurerCodeItemRepository;
import com.bone.claim.domain.repository.MappingMissRepository;
import com.bone.claim.domain.repository.RefDiseaseRepository;
import com.bone.claim.domain.repository.RefDrugRepository;
import com.bone.claim.domain.repository.RefFeeCategoryRepository;
import com.bone.claim.domain.repository.RefHospitalRepository;
import com.bone.claim.domain.repository.StdCodeAliasRepository;
import com.bone.claim.domain.repository.StdCodeSplitRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 映射批量导入四分支 + 模板（task 2.2）：
 *
 * <ul>
 *   <li>IMPORTED：全合法 → 发布走 IMPORT 源 + 指纹批次，created/unchanged 分开统计；
 *   <li>REJECTED（行错误）：任一行标准码不存在 → 整批拒绝、零写入（变异杀：删 errors 判空即红）；
 *   <li>REJECTED（坏文件）：非 xlsx 字节 → 结果对象而非异常打摆前端；
 *   <li>IDEMPOTENT_DUPLICATE：指纹命中既有批次 → 不再写行。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MappingImportTest {

  @Mock CodeMappingRepository codeMappingRepository;
  @Mock MappingMissRepository mappingMissRepository;
  @Mock StdCodeAliasRepository stdCodeAliasRepository;
  @Mock StdCodeSplitRepository stdCodeSplitRepository;
  @Mock com.bone.claim.domain.repository.ClaimInsurerRepository claimInsurerRepository;
  @Mock InsurerCodeItemRepository insurerCodeItemRepository;
  @Mock FieldBindingRepository fieldBindingRepository;
  @Mock RefHospitalRepository refHospitalRepository;
  @Mock RefDrugRepository refDrugRepository;
  @Mock RefDiseaseRepository refDiseaseRepository;
  @Mock RefFeeCategoryRepository refFeeCategoryRepository;
  @Mock TenantPort tenantPort;

  @InjectMocks CodeMappingApplicationService service;

  private void stubBase() {
    when(tenantPort.currentTenantId()).thenReturn(1L);
    when(claimInsurerRepository.findByInsurerCode(eq(1L), eq("YCBX")))
        .thenReturn(ClaimInsurer.of(9L, 1L, "YCBX", "永诚财险", "永诚", "DIRECT", "", "", Instant.EPOCH));
  }

  private RefHospital hospital(String code, String name) {
    // 真实构造而非 mock：mockito 对该类的 getter 拦截会触发 UnfinishedStubbing
    return RefHospital.of(1L, 1L, code, name, "", 1, "", "");
  }

  /** 三列：保司码值 / 保司名称 / 标准码。首行为表头。 */
  private byte[] workbook(List<String[]> dataRows) {
    try (XSSFWorkbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      var sheet = wb.createSheet("映射");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("保司码值");
      header.createCell(1).setCellValue("保司名称");
      header.createCell(2).setCellValue("标准码");
      int r = 1;
      for (String[] cells : dataRows) {
        Row row = sheet.createRow(r++);
        for (int i = 0; i < cells.length; i++) {
          row.createCell(i).setCellValue(cells[i]);
        }
      }
      wb.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void import_imported_publishesWithImportSourceAndFingerprintBatch() {
    stubBase();
    when(refHospitalRepository.findByHospitalCode(1L, "H001")).thenReturn(hospital("H001", "协和医院"));
    when(refHospitalRepository.findByHospitalCode(1L, "H002"))
        .thenReturn(hospital("H002", "301医院"));
    when(codeMappingRepository.findByRelease(anyLong(), anyString())).thenReturn(List.of());
    when(codeMappingRepository.findActive(
            anyLong(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(null);

    ImportResultView result =
        service.importMappings(
            "YCBX",
            "HOSPITAL",
            workbook(
                List.of(new String[] {"XHYY", "协和", "H001"}, new String[] {"WJMY", "武警", "H002"})));

    assertThat(result.status()).isEqualTo("IMPORTED");
    assertThat(result.totalRows()).isEqualTo(2);
    assertThat(result.createdCount()).isEqualTo(2);
    assertThat(result.unchangedCount()).isZero();
    assertThat(result.versionNo()).startsWith("IMP-");

    ArgumentCaptor<CodeMapping> captor = ArgumentCaptor.forClass(CodeMapping.class);
    verify(codeMappingRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .allSatisfy(
            m -> {
              assertThat(m.getSource()).isEqualTo(MappingSource.IMPORT.code());
              assertThat(m.getVersionNo()).isEqualTo(result.versionNo());
              assertThat(m.getMappingType()).isEqualTo(MappingType.EXACT.code());
              assertThat(m.getMatchRule()).isEqualTo(MatchRule.MANUAL.code());
            });
  }

  @Test
  void import_rowWithUnknownStandardCode_rejectsWholeBatchWithoutWrite() {
    stubBase();
    when(refHospitalRepository.findByHospitalCode(1L, "H001")).thenReturn(hospital("H001", "协和医院"));
    when(refHospitalRepository.findByHospitalCode(1L, "H404")).thenReturn(null);

    ImportResultView result =
        service.importMappings(
            "YCBX",
            "HOSPITAL",
            workbook(
                List.of(
                    new String[] {"XHYY", "协和", "H001"}, new String[] {"GHOST", "幽灵", "H404"})));

    assertThat(result.status()).isEqualTo("REJECTED");
    assertThat(result.totalRows()).isEqualTo(2);
    assertThat(result.createdCount()).isZero();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).rowNo())
        .isEqualTo(3); // 1-based 物理行号（第 1 行表头、第 2 行数据 OK、第 3 行出错）
    assertThat(result.errors().get(0).reason()).contains("H404");
    verify(codeMappingRepository, never()).save(any(CodeMapping.class));
  }

  @Test
  void import_unparseableFile_returnsRejectedResult() {
    stubBase();

    ImportResultView result = service.importMappings("YCBX", "HOSPITAL", "not-an-xlsx".getBytes());

    assertThat(result.status()).isEqualTo("REJECTED");
    verify(codeMappingRepository, never()).save(any(CodeMapping.class));
  }

  @Test
  void import_sameContentReuploaded_hitsFingerprintBatchWithoutRewrite() {
    stubBase();
    when(refHospitalRepository.findByHospitalCode(1L, "H001")).thenReturn(hospital("H001", "协和医院"));
    CodeMapping existing =
        CodeMapping.of(
            77L,
            1L,
            "YCBX",
            MasterDataDomain.HOSPITAL,
            "XHYY",
            "",
            "H001",
            MappingType.EXACT,
            MatchRule.MANUAL,
            null,
            null,
            MappingSource.IMPORT,
            "IMP-AAAA",
            Instant.EPOCH,
            Instant.EPOCH);
    // 指纹取决于文件内容；不重算，直接以「同批次已有行」模拟命中
    when(codeMappingRepository.findByRelease(anyLong(), anyString())).thenReturn(List.of(existing));

    ImportResultView result =
        service.importMappings(
            "YCBX",
            "HOSPITAL",
            workbook(java.util.Collections.singletonList(new String[] {"XHYY", "协和", "H001"})));

    assertThat(result.status()).isEqualTo("IDEMPOTENT_DUPLICATE");
    assertThat(result.createdCount()).isZero();
    // 幂等命中即「计算指纹 == 批次 version_no」；此处 mock 不校验指纹相等，只断言批次统计透传
    assertThat(result.versionNo()).startsWith("IMP-");
    assertThat(result.unchangedCount()).isEqualTo(1);
    verify(codeMappingRepository, never()).save(any(CodeMapping.class));
  }

  @Test
  void import_existingActiveRowWithDifferentBatch_countsAsUnchanged() {
    stubBase();
    when(refHospitalRepository.findByHospitalCode(1L, "H001")).thenReturn(hospital("H001", "协和医院"));
    when(codeMappingRepository.findByRelease(anyLong(), anyString())).thenReturn(List.of());
    CodeMapping current =
        CodeMapping.of(
            55L,
            1L,
            "YCBX",
            MasterDataDomain.HOSPITAL,
            "XHYY",
            "",
            "H001",
            MappingType.EXACT,
            MatchRule.MANUAL,
            null,
            null,
            MappingSource.MANUAL,
            "B0",
            Instant.EPOCH,
            Instant.EPOCH);
    when(codeMappingRepository.findActive(
            nullable(Long.class), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(current);

    ImportResultView result =
        service.importMappings(
            "YCBX",
            "HOSPITAL",
            workbook(java.util.Collections.singletonList(new String[] {"XHYY", "协和", "H001"})));

    // 同码同标准码：publishCore 幂等返回旧行（旧版本号）→ unchanged，不产生新行
    assertThat(result.status()).isEqualTo("IMPORTED");
    assertThat(result.createdCount()).isZero();
    assertThat(result.unchangedCount()).isEqualTo(1);
  }

  @Test
  void template_containsHeaderAndSampleRow() throws IOException {
    byte[] bytes = service.importTemplate();
    try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("保司码值");
      assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).startsWith("SAMPLE-");
    }
  }
}
