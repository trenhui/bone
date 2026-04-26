package com.bone.tpa.test.adjust.tpaAdjustData;

import java.time.format.DateTimeFormatter;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class TpaSqlParser {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CSV_SPLIT_REGEX = ",(?=([^\"]*\"[^\"]*\")*[^\"]*$)";

    /**
     * 从txt文件读取Liability数据
     */
    public static List<TpaLiability> readLiabilityFromFile(String filePath) throws IOException {
        List<TpaLiability> liabilities = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            // 读取表头
            String headerLine = br.readLine();
            if (headerLine == null) {
                return liabilities;
            }

            // 解析表头字段名
            List<String> headers = parseCsvLine(headerLine);

            String line;
            int lineNumber = 1; // 记录行号用于错误处理

            while ((line = br.readLine()) != null) {
                lineNumber++;
                try {
                    TpaLiability liability = parseLineToLiability(line, headers);
                    if (liability != null) {
                        liabilities.add(liability);
                    }
                } catch (Exception e) {
                    System.err.printf("解析第%d行时出错: %s%n", lineNumber, e.getMessage());
                    System.err.println("行内容: " + line);
                }
            }
        }

        return liabilities;
    }

    /**
     * 从txt文件读取TpaCoverage数据
     */
    public static List<TpaCoverage> readCoverageFromFile(String filePath) throws IOException {
        List<TpaCoverage> coverages = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            // 读取表头
            String headerLine = br.readLine();
            if (headerLine == null) {
                return coverages;
            }

            // 解析表头字段名
            List<String> headers = parseCsvLine(headerLine);

            String line;
            int lineNumber = 1; // 记录行号用于错误处理

            while ((line = br.readLine()) != null) {
                lineNumber++;
                try {
                    TpaCoverage coverage = parseLineToTpaCoverage(line, headers);
                    if (coverage != null) {
                        coverages.add(coverage);
                    }
                } catch (Exception e) {
                    System.err.printf("解析第%d行时出错: %s%n", lineNumber, e.getMessage());
                    System.err.println("行内容: " + line);
                }
            }
        }

        return coverages;
    }

    /**
     * 从txt文件读取TpaLiabilityConfig数据
     */
    public static List<TpaLiabilityConfig> readTpaLiabilityConfigFromFile(String filePath) throws IOException {
        List<TpaLiabilityConfig> configs = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            // 读取表头
            String headerLine = br.readLine();
            if (headerLine == null) {
                return configs;
            }

            // 解析表头字段名
            List<String> headers = parseCsvLine(headerLine);

            String line;
            int lineNumber = 1; // 记录行号用于错误处理

            while ((line = br.readLine()) != null) {
                lineNumber++;
                try {
                    TpaLiabilityConfig config = parseLineToTpaLiabilityConfig(line, headers);
                    if (config != null) {
                        configs.add(config);
                    }
                } catch (Exception e) {
                    System.err.printf("解析第%d行时出错: %s%n", lineNumber, e.getMessage());
                    System.err.println("行内容: " + line);
                }
            }
        }

        return configs;
    }

    /**
     * 从txt文件读取ValidRate数据
     */
    public static List<ValidRate> readValidRateFromFile(String filePath) throws IOException {
        List<ValidRate> validRates = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            // 读取表头
            String headerLine = br.readLine();
            if (headerLine == null) {
                return validRates;
            }

            // 解析表头字段名
            List<String> headers = parseCsvLine(headerLine);

            String line;
            int lineNumber = 1; // 记录行号用于错误处理

            while ((line = br.readLine()) != null) {
                lineNumber++;
                try {
                    ValidRate validRate = parseLineToValidRate(line, headers);
                    if (validRate != null) {
                        validRates.add(validRate);
                    }
                } catch (Exception e) {
                    System.err.printf("解析第%d行时出错: %s%n", lineNumber, e.getMessage());
                    System.err.println("行内容: " + line);
                }
            }
        }

        return validRates;
    }


    /**
     * 解析CSV行
     */
    private static List<String> parseCsvLine(String line) {
        // 移除字符串两端的引号，并分割
        return Arrays.stream(line.split(CSV_SPLIT_REGEX))
                .map(field -> {
                    // 移除字段两端的引号
                    if (field.startsWith("\"") && field.endsWith("\"")) {
                        return field.substring(1, field.length() - 1);
                    }
                    return field;
                })
                .collect(Collectors.toList());
    }

    /**
     * 将一行数据解析为Liability对象
     */
    private static TpaLiability parseLineToLiability(String line, List<String> headers) {
        List<String> fields = parseCsvLine(line);

        if (fields.size() != headers.size()) {
            System.err.printf("字段数量不匹配: 表头有%d个字段，数据行有%d个字段%n",
                    headers.size(), fields.size());
            return null;
        }

        TpaLiability liability = new TpaLiability();
        Map<String, String> fieldMap = new HashMap<>();

        // 将字段名和值存入Map
        for (int i = 0; i < headers.size(); i++) {
            fieldMap.put(headers.get(i), fields.get(i));
        }

        try {
            // 根据字段名设置属性值
            setLiabilityFields(liability, fieldMap);
        } catch (Exception e) {
            throw new RuntimeException("设置Liability字段时出错: " + e.getMessage(), e);
        }

        return liability;
    }

    /**
     * 将一行数据解析为TpaCoverage对象
     */
    private static TpaCoverage parseLineToTpaCoverage(String line, List<String> headers) {
        List<String> fields = parseCsvLine(line);

        if (fields.size() != headers.size()) {
            System.err.printf("字段数量不匹配: 表头有%d个字段，数据行有%d个字段%n",
                    headers.size(), fields.size());
            return null;
        }

        TpaCoverage coverage = new TpaCoverage();
        Map<String, String> fieldMap = new HashMap<>();

        // 将字段名和值存入Map
        for (int i = 0; i < headers.size(); i++) {
            fieldMap.put(headers.get(i), fields.get(i));
        }

        try {
            // 根据字段名设置属性值
            setTpaCoverageFields(coverage, fieldMap);
        } catch (Exception e) {
            throw new RuntimeException("设置TpaCoverage字段时出错: " + e.getMessage(), e);
        }

        return coverage;
    }


    /**
     * 将一行数据解析为TpaLiabilityConfig对象
     */
    private static TpaLiabilityConfig parseLineToTpaLiabilityConfig(String line, List<String> headers) {
        List<String> fields = parseCsvLine(line);

        if (fields.size() != headers.size()) {
            System.err.printf("字段数量不匹配: 表头有%d个字段，数据行有%d个字段%n",
                    headers.size(), fields.size());
            return null;
        }

        TpaLiabilityConfig config = new TpaLiabilityConfig();
        Map<String, String> fieldMap = new HashMap<>();

        // 将字段名和值存入Map
        for (int i = 0; i < headers.size(); i++) {
            fieldMap.put(headers.get(i), fields.get(i));
        }

        try {
            // 根据字段名设置属性值
            setTpaLiabilityConfigFields(config, fieldMap);
        } catch (Exception e) {
            throw new RuntimeException("设置TpaLiabilityConfig字段时出错: " + e.getMessage(), e);
        }

        return config;
    }

    /**
     * 将一行数据解析为ValidRate对象
     */
    private static ValidRate parseLineToValidRate(String line, List<String> headers) {
        List<String> fields = parseCsvLine(line);

        if (fields.size() != headers.size()) {
            System.err.printf("字段数量不匹配: 表头有%d个字段，数据行有%d个字段%n",
                    headers.size(), fields.size());
            return null;
        }

        ValidRate validRate = new ValidRate();
        Map<String, String> fieldMap = new HashMap<>();

        // 将字段名和值存入Map
        for (int i = 0; i < headers.size(); i++) {
            fieldMap.put(headers.get(i), fields.get(i));
        }

        try {
            // 根据字段名设置属性值
            setValidRateFields(validRate, fieldMap);
        } catch (Exception e) {
            throw new RuntimeException("设置ValidRate字段时出错: " + e.getMessage(), e);
        }

        return validRate;
    }


    /**
     * 设置Liability对象的字段值
     */
    private static void setLiabilityFields(TpaLiability liability, Map<String, String> fieldMap) {
        liability.setId(fieldMap.get("ID"));

        // 字符串类型字段
        liability.setPolicyNo(fieldMap.get("PolicyNo"));
        liability.setPlanName(fieldMap.get("PlanName"));
        liability.setTreatmentType(fieldMap.get("TreamentType")); // 注意：原数据中是TreamentType
        liability.setCauseReason(fieldMap.get("CauseReason"));
        liability.setBearOwnExpense(fieldMap.get("BearOwnExpense"));
        liability.setBearingClassB(fieldMap.get("BearingClassB"));
        liability.setBenefitType(fieldMap.get("BenefitType"));
        liability.setMatchType(fieldMap.get("MatchType"));
        liability.setMedicalInsuranceCondition(fieldMap.get("MedicalInsuranceCondition"));
        liability.setIsWaiting(fieldMap.get("isWaiting"));
        liability.setWaitingDay(fieldMap.get("WatingDay")); // 注意：原数据中是WatingDay
        liability.setReponsibilityName(fieldMap.get("ReponsibilityName"));
        liability.setReponsibilityType(fieldMap.get("ReponsibilityType"));
        liability.setAccidentNature(fieldMap.get("AccidentNature"));
        liability.setReponsibilityCodeOne(fieldMap.get("ReponsibilityCodeOne"));
        liability.setReponsibilityCodeTwo(fieldMap.get("ReponsibilityCodeTwo"));
        liability.setReponsibilityCode(fieldMap.get("ReponsibilityCode"));
        liability.setDeductionType(fieldMap.get("DeductionType"));
        liability.setRemark(fieldMap.get("Reamrk")); // 注意：原数据中是Reamrk
        liability.setReserved1(fieldMap.get("Reserved1"));
        liability.setReserved2(fieldMap.get("Reserved2"));

        liability.setLimitTimesMethodType(fieldMap.get("LimitTimesMethodType"));
        liability.setLimitTimesMethodNumber(fieldMap.get("LimitTimesMethodNumber"));
        liability.setIsTotalCompensation(fieldMap.get("is_total_compensation"));
        liability.setResponsibilityMold(fieldMap.get("ResponsibilityMold"));
        liability.setOutputCalSqlFlag(fieldMap.get("output_cal_sql_flag"));
        liability.setLiabilityBillType(fieldMap.get("liability_bill_type"));
        liability.setPublicAccountPolicyNo(fieldMap.get("public_account_policy_no"));
        liability.setNoPolicyLiabilityItems(fieldMap.get("noPolicyLiabilityItems"));
        liability.setIsEnteredCompensation(fieldMap.get("is_entered_compensation"));
        liability.setIsDiagnosisRange(fieldMap.get("is_diagnosis_range"));
        liability.setJudgmentMode(fieldMap.get("judgment_mode"));
        liability.setJudgmentModeSub(fieldMap.get("judgment_mode_sub"));
        liability.setDeductionRemark(fieldMap.get("deduction_remark"));
        liability.setYcLiabilityBillType(fieldMap.get("yc_liability_bill_type"));
        liability.setAdjustmentPlan(fieldMap.get("adjustment_plan"));
        liability.setCostMode(fieldMap.get("cost_mode"));
        liability.setYdMode(fieldMap.get("yd_mode"));
        liability.setDutyPayCondition(fieldMap.get("duty_pay_condition"));
        liability.setLimitMethodType(fieldMap.get("limit_method_type"));

        // UUID类型字段
        if (isNotEmpty(fieldMap.get("PalnId"))) {
            liability.setPalnId(fieldMap.get("PalnId"));
        }
        if (isNotEmpty(fieldMap.get("PlanBenefitId"))) {
            liability.setPlanBenefitId(fieldMap.get("PlanBenefitId"));
        }
        if (isNotEmpty(fieldMap.get("InsuranceId"))) {
            liability.setInsuranceId(fieldMap.get("InsuranceId"));
        }
        if (isNotEmpty(fieldMap.get("PersonInSuranceInfoId"))) {
            liability.setPersonInSuranceInfoId(fieldMap.get("PersonInSuranceInfoId"));
        }
        if (isNotEmpty(fieldMap.get("PersonInSuranceInfoIdTwo"))) {
            liability.setPersonInSuranceInfoIdTwo(fieldMap.get("PersonInSuranceInfoIdTwo"));
        }

        // BigDecimal类型字段
        liability.setReponsibilityLimit(parseBigDecimal(fieldMap.get("ReponsibilityLimit")));
        liability.setTotalCompensation(parseBigDecimal(fieldMap.get("total_compensation")));
        liability.setCorporateAccountControlAmount(parseBigDecimal(fieldMap.get("corporate_account_control_amount")));
        liability.setEnteredCompensation(parseBigDecimal(fieldMap.get("entered_compensation")));

        // Integer类型字段
        liability.setBenefitsTotalCounts(parseInteger(fieldMap.get("benefits_totalcounts")));
        liability.setLimitMethodAmount(parseInteger(fieldMap.get("limit_method_amount")));
        liability.setDutyIndex(parseInteger(fieldMap.get("duty_index")));

    }

    /**
     * 设置TpaCoverage对象的字段值
     */
    private static void setTpaCoverageFields(TpaCoverage coverage, Map<String, String> fieldMap) {

        coverage.setId(fieldMap.get("ID"));

        // 字符串类型字段
        coverage.setPolicyNo(fieldMap.get("PolicyNo"));
        coverage.setInsuranceCode(fieldMap.get("InsuranceCode"));
        coverage.setInsuranceName(fieldMap.get("InsuranceName"));
        coverage.setIsPublicLimit(fieldMap.get("IsPublicLimit"));
        coverage.setReserved1(fieldMap.get("Reserved1"));
        coverage.setReserved2(fieldMap.get("Reserved2"));

        // UUID类型字段
        if (isNotEmpty(fieldMap.get("PlanId"))) {
            coverage.setPlanId(fieldMap.get("PlanId"));
        }

        // BigDecimal类型字段
        coverage.setLimitAmount(parseBigDecimal(fieldMap.get("LimitAmount")));
    }

    /**
     * 设置TpaLiabilityConfig对象的字段值
     */
    private static void setTpaLiabilityConfigFields(TpaLiabilityConfig config, Map<String, String> fieldMap) {
        // 字符串类型字段
        config.setPolicyNo(fieldMap.get("PolicyNo"));
        config.setIsWaiting(fieldMap.get("isWaiting"));
        config.setLimitMethodType(fieldMap.get("LimitMethodType"));
        config.setBearingClassB(fieldMap.get("BearingClassB"));
        config.setBearOwnExpense(fieldMap.get("BearOwnExpense"));
        config.setBearRational(fieldMap.get("BearRational"));
        config.setLimitRationalAmount(fieldMap.get("LimitRationalAmount"));
        config.setLimitSequence(fieldMap.get("LimitSequence"));
        config.setMedicalInsuranceType(fieldMap.get("MedicalInsuranceType"));
        config.setDeductibleMethodType(fieldMap.get("DeductibleMethodType"));
        config.setDeductibleOrder(fieldMap.get("DeductibleOrder"));
        config.setLimitTimesMethodType(fieldMap.get("LimitTimesMethodType"));
        config.setLimitTimesMethodNumber(fieldMap.get("LimitTimesMethodNumber"));
        config.setReserved1(fieldMap.get("Reserved1"));
        config.setReserved2(fieldMap.get("Reserved2"));
        config.setDeductibleMode(fieldMap.get("DeductibleMode"));
        config.setEnableAbValue(fieldMap.get("enable_ab_value"));
        config.setEnableAbCondition(fieldMap.get("enable_ab_condition"));
        config.setEnableAbRule(fieldMap.get("enable_ab_rule"));
        config.setLimitOrder(fieldMap.get("LimitOrder"));
        config.setEnableDeductible(fieldMap.get("EnableDeductible"));

        config.setResponsibilityId(fieldMap.get("ResponsibilityId"));
        config.setPersonInSuranceInfoId(fieldMap.get("PersonInSuranceInfoId"));

        // Integer类型字段
        config.setWaitingDay(parseInteger(fieldMap.get("WatingDay"))); // 注意：原数据中是WatingDay
        config.setLimitMethodNum(parseInteger(fieldMap.get("LimitMethodNum")));

        // BigDecimal类型字段
        config.setOwnExpenseRate(parseBigDecimal(fieldMap.get("OwnExpenseRate")));
        config.setOwnExpenseLimitAmount(parseBigDecimal(fieldMap.get("OwnExpenseLimitAmount")));
        config.setOwnExpenseClassBRate(parseBigDecimal(fieldMap.get("OwnExpenseClassBRate")));
        config.setOwnExpenseClassBLimitAmount(parseBigDecimal(fieldMap.get("OwnExpenseClassBLimitAmount")));
        config.setLimitMethodAmount(parseBigDecimal(fieldMap.get("LimitMethodAmount")));
        config.setLimitClassBAmount(parseBigDecimal(fieldMap.get("LimitClassBAmount")));
        config.setLimitOwnExpense(parseBigDecimal(fieldMap.get("LimitOwnExpense")));
        config.setRationalLimitAmount(parseBigDecimal(fieldMap.get("RationalLimitAmount")));
        config.setDeductibleMethodAmount(parseBigDecimal(fieldMap.get("DeductibleMethodAmount")));
        config.setDeductibleAmount(parseBigDecimal(fieldMap.get("DeductibleAmount")));
        config.setDeductibleRate(parseBigDecimal(fieldMap.get("DeductibleRate")));
        config.setRationalSublimit(parseBigDecimal(fieldMap.get("RationalSublimit")));
        config.setClassBSublimit(parseBigDecimal(fieldMap.get("ClassBSublimit")));
        config.setOwnExpenseSublimit(parseBigDecimal(fieldMap.get("OwnExpenseSublimit")));
    }

    /**
     * 设置ValidRate对象的字段值
     */
    private static void setValidRateFields(ValidRate validRate, Map<String, String> fieldMap) {
        // 字符串类型字段
        validRate.setPolicyNo(fieldMap.get("PolicyNo"));
        validRate.setRateType(fieldMap.get("RateType"));
        validRate.setReserved1(fieldMap.get("Reserved1"));
        validRate.setReserved2(fieldMap.get("Reserved2"));
        validRate.setPayDefinition(fieldMap.get("pay_definition")); // 注意：列名是pay_definition

        // UUID类型字段
        validRate.setResponsibilityId(fieldMap.get("ResponsibilityId"));
        validRate.setResponsibilityIdBasicConfigId(fieldMap.get("ResponsibilityIdBasicConfigId"));

        // BigDecimal类型字段
        validRate.setRate(parseBigDecimal(fieldMap.get("Rate")));

        // Integer类型字段
        validRate.setMinNum(parseInteger(fieldMap.get("minNum"))); // 注意：列名是minNum
        validRate.setMaxNum(parseInteger(fieldMap.get("maxNum"))); // 注意：列名是maxNum
    }


    /**
     * 辅助方法：解析BigDecimal
     */
    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            System.err.println("解析BigDecimal失败: " + value);
            return null;
        }
    }

    /**
     * 辅助方法：解析Integer
     */
    private static Integer parseInteger(String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("解析Integer失败: " + value);
            return null;
        }
    }

    /**
     * 检查字符串是否非空
     */
    private static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty() && !"null".equalsIgnoreCase(str);
    }
}
