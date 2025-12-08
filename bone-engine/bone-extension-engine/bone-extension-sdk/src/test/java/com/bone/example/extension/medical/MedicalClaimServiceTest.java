package com.bone.example.extension.medical;

import com.bone.engine.extension.support.context.BizContext;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.bone.example.extension.medical.MedicalClaimRequest.ClaimItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 医疗保险理赔服务企业级完整测试套件
 * <p>
 * 对MedicalClaimService的核心功能进行全面测试，确保服务能够正确处理各种理赔场景。
 */
@SpringBootTest(classes = com.bone.example.extension.config.TestConfig.class)
@ActiveProfiles("test")
@Slf4j
@DisplayName("MedicalClaimService 企业级医疗保险理赔服务完整测试套件")
class MedicalClaimServiceTest {

    @Autowired
    private MedicalClaimService medicalClaimService;

    private static MedicalClaimRequest outpatientRequest;
    private static MedicalClaimRequest inpatientRequest;
    private static MedicalClaimRequest specialDiseaseRequest;

    @BeforeAll
    static void initTestData() {
        // 创建测试用的理赔项目
        List<ClaimItem> outpatientItems = new ArrayList<>();
        outpatientItems.add(ClaimItem.builder()
                .itemCode("ITEM-001")
                .itemName("药品费")
                .category("MEDICINE")
                .unitPrice(new BigDecimal("300.00"))
                .quantity(1)
                .covered(true)
                .build());
        outpatientItems.add(ClaimItem.builder()
                .itemCode("ITEM-002")
                .itemName("检查费")
                .category("EXAMINATION")
                .unitPrice(new BigDecimal("200.00"))
                .quantity(1)
                .covered(true)
                .build());

        List<ClaimItem> inpatientItems = new ArrayList<>();
        inpatientItems.add(ClaimItem.builder()
                .itemCode("IN-ITEM-001")
                .itemName("住院费")
                .category("HOSPITALIZATION")
                .unitPrice(new BigDecimal("8000.00"))
                .quantity(1)
                .covered(true)
                .build());
        inpatientItems.add(ClaimItem.builder()
                .itemCode("IN-ITEM-002")
                .itemName("手术费")
                .category("OPERATION")
                .unitPrice(new BigDecimal("2000.00"))
                .quantity(1)
                .covered(true)
                .build());

        List<ClaimItem> specialDiseaseItems = new ArrayList<>();
        specialDiseaseItems.add(ClaimItem.builder()
                .itemCode("SPECIAL-ITEM-001")
                .itemName("特殊病种治疗费")
                .category("SPECIAL_TREATMENT")
                .unitPrice(new BigDecimal("3000.00"))
                .quantity(1)
                .covered(true)
                .build());

        // 设置当前日期作为就诊日期（确保在90天有效期内）
        Date currentDate = new Date();
        Date yesterday = new Date(currentDate.getTime() - 24 * 60 * 60 * 1000);
        Date threeDaysAgo = new Date(currentDate.getTime() - 3 * 24 * 60 * 60 * 1000);

        outpatientRequest = MedicalClaimRequest.builder()
                .claimId("OUT-20251208-001")
                .userId("USER_123456")
                .claimType(MedicalClaimRequest.ClaimType.OUTPATIENT)
                .totalAmount(new BigDecimal("500.00"))
                .medicalDate(currentDate)
                .hospitalName("人民医院")
                .items(outpatientItems)
                .build();

        inpatientRequest = MedicalClaimRequest.builder()
                .claimId("IN-20251208-001")
                .userId("USER_123456")
                .claimType(MedicalClaimRequest.ClaimType.INPATIENT)
                .totalAmount(new BigDecimal("10000.00"))
                .medicalDate(currentDate)
                .hospitalName("人民医院")
                .admissionDate(threeDaysAgo)
                .dischargeDate(yesterday)
                .admissionType("急诊")
                .items(inpatientItems)
                .build();

        specialDiseaseRequest = MedicalClaimRequest.builder()
                .claimId("SPECIAL-20251208-001")
                .userId("USER_123456")
                .claimType(MedicalClaimRequest.ClaimType.SPECIAL_TREATMENT)
                .totalAmount(new BigDecimal("3000.00"))
                .medicalDate(currentDate)
                .hospitalName("人民医院")
                .diagnosis("糖尿病")
                .items(specialDiseaseItems)
                .build();
    }

    @Test
    @DisplayName("门诊理赔 → 精确匹配门诊实现 + 按比例报销")
    void shouldProcessOutpatientClaimSuccessfully() {
        MedicalClaimResult result = medicalClaimService.processClaim(outpatientRequest);

        assertThat(result.getStatus()).isEqualTo(MedicalClaimResult.ClaimStatus.APPROVED);
        assertThat(result.getClaimId()).isEqualTo("OUT-20251208-001");
        assertThat(result.getTotalClaimAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(result.getApprovedAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getRejectedAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        log.info("门诊理赔成功 | 理赔ID={} | 总金额={} | 批准金额={} | 拒绝金额={}",
                result.getClaimId(), result.getTotalClaimAmount(), result.getApprovedAmount(), result.getRejectedAmount());
    }

    @Test
    @DisplayName("住院理赔 → 精确匹配住院实现 + 按比例报销")
    void shouldProcessInpatientClaimSuccessfully() {
        MedicalClaimResult result = medicalClaimService.processClaim(inpatientRequest);

        assertThat(result.getStatus()).isEqualTo(MedicalClaimResult.ClaimStatus.APPROVED);
        assertThat(result.getClaimId()).isEqualTo("IN-20251208-001");
        assertThat(result.getTotalClaimAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(result.getApprovedAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getRejectedAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        log.info("住院理赔成功 | 理赔ID={} | 总金额={} | 批准金额={} | 拒绝金额={}",
                result.getClaimId(), result.getTotalClaimAmount(), result.getApprovedAmount(), result.getRejectedAmount());
    }

    @DisplayName("多理赔类型参数化测试（数据驱动）")
    @ParameterizedTest(name = "[{index}] 理赔类型={0} | 总金额={1}")
    @CsvSource({
            "OUTPATIENT,        500.00",
            "INPATIENT,         10000.00",
            "SPECIAL_TREATMENT, 3000.00"
    })
    void shouldProcessDifferentClaimTypes(String claimType, BigDecimal totalAmount) {
        MedicalClaimRequest.ClaimType type = MedicalClaimRequest.ClaimType.valueOf(claimType);
        MedicalClaimRequest request;

        // 根据理赔类型选择对应的请求对象
        if (type == MedicalClaimRequest.ClaimType.OUTPATIENT) {
            request = outpatientRequest;
        } else if (type == MedicalClaimRequest.ClaimType.INPATIENT) {
            request = inpatientRequest;
        } else {
            request = specialDiseaseRequest;
        }

        MedicalClaimResult result = medicalClaimService.processClaim(request);

        assertThat(result.getStatus()).isEqualTo(MedicalClaimResult.ClaimStatus.APPROVED);
        assertThat(result.getClaimId()).isNotNull();
        assertThat(result.getTotalClaimAmount()).isEqualByComparingTo(totalAmount);
        assertThat(result.getApprovedAmount().add(result.getRejectedAmount()))
                .isEqualByComparingTo(totalAmount);

        log.info("{}理赔处理成功 | 理赔ID={} | 总金额={} | 批准金额={}",
                claimType, result.getClaimId(), result.getTotalClaimAmount(), result.getApprovedAmount());
    }

    @Test
    @DisplayName("理赔验证功能 → 参数验证正常工作")
    void shouldValidateClaimSuccessfully() {
        // 执行测试
        ValidationResult result = medicalClaimService.validateClaim(outpatientRequest);
        
        // 验证结果
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
  
        log.info("理赔验证成功 | 是否有效={}", result.isSuccess());
    }

    @Test
    @DisplayName("无效理赔请求 → 参数验证失败")
    void shouldRejectInvalidClaimRequest() {
        // 准备无效的测试数据 - 缺少必要字段
        MedicalClaimRequest invalidRequest = MedicalClaimRequest.builder()
                .claimId("INVALID-001")
                // 缺少userId和claimType
                .totalAmount(BigDecimal.TEN)
                .items(Collections.emptyList())
                .build();
        
        // 执行测试
        ValidationResult result = medicalClaimService.validateClaim(invalidRequest);
        
        // 验证结果
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
  
        log.info("无效理赔请求被正确拦截 | 错误信息={}", result.getErrorMessage());
    }
}