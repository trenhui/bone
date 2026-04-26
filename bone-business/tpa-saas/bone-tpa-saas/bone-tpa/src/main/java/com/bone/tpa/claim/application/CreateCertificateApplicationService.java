package com.bone.tpa.claim.application;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.api.enums.CollectTypeEnum;
import com.bone.tpa.claim.application.enums.CertificateConfigEnums;
import com.bone.tpa.claim.application.response.FieldDataForYCLpsqs;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.push.enums.GenderEnum;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.adjustment.model.AdjustmentResult;
import com.bone.tpa.sdk.adjustment.model.Policy;
import com.bone.tpa.sdk.adjustment.response.CertificateConfig;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.dao.ClaimImageRepository;
import com.bone.tpa.sdk.dao.ClaimInvoiceRepository;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.ClaimStakeholderRepository;
import com.bone.tpa.sdk.dao.impl.AdjustmentRecordRepository;
import com.bone.tpa.sdk.dao.impl.AdjustmentResultRepository;
import com.bone.tpa.sdk.dao.impl.PolicyRepository;
import com.bone.tpa.sdk.masterdb.mapper.InsuPersonMapper;
import com.bone.tpa.sdk.masterdb.mapper.PersonClaimDetailMapper;
import com.bone.tpa.sdk.masterdb.model.InsuPerson;
import com.bone.tpa.sdk.masterdb.model.PersonClaimDetail;
import com.bone.tpa.util.ALiYunOSSUtil;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.*;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class CreateCertificateApplicationService {

    @Autowired
    private PolicyRepository policyRepository;
    @Autowired
    private ClaimStakeholderRepository stakeholderRepository;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private ClaimInvoiceRepository invoiceRepository;
    @Autowired
    private AdjustmentResultRepository adjustmentResultRepository;
    @Autowired
    private AdjustmentRecordRepository adjustmentRecordRepository;
    @Autowired
    private ClaimImageRepository imageRepository;
    @Autowired
    private PersonClaimDetailMapper personClaimDetailMapper;
    @Autowired
    private InsuPersonMapper insuPersonMapper;

    @Autowired
    private ClaimTrackLogService claimTrackLogService;

    @Autowired
    private ALiYunOSSUtil ossUtil;

    @Value("${oss.BUCKETNAME:bucket-pktest}")
    private String bucketName;

    @Value("${oss.VISIT:oss-cn-hangzhou.aliyuncs.com}")
    private String visit;

    //经过测试,dpi为96,100,105,120,150,200中,105显示效果较为清晰,体积稳定,dpi越高图片体积越大,一般电脑显示分辨率为96
    public static final float DEFAULT_DPI = 150;

    // 定义中文数字和单位
    private static final String[] CN_NUMS = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] CN_UNITS = {"", "拾", "佰", "仟"};
    private static final String[] CN_BIG_UNITS = {"", "万", "亿", "兆"};

    /**
     * 读取单证配置并且生成证书
     */
    @Async
    public void loadConfigAndCreateCertificate(Long claimNumber) {
        Claim claim = claimRepository.findById(claimNumber);
        if (claim == null) {
            log.info("停止生成单证,因为赔案不存在,赔案号:{}", claimNumber);
            return;
        }

        String policyNo = claim.getPolicyNo();
        Criteria<Policy> policyCriteria = Criteria.create();
        policyCriteria.eq(Policy::getPolicyNo, policyNo);
        List<Policy> policyList = policyRepository.findByCriteria(policyCriteria);
        if (CollectionUtils.isEmpty(policyList) || policyList.size() > 1) {
            log.info("停止生成单证,因为目标保单不存在或数量大于1,赔案号:{},保单号:{}", claimNumber, policyNo);
            return;
        }

        Policy policy = policyList.get(0);
        CertificateConfig certificateConfig = policy.queryCertificateConfig();
        if (certificateConfig == null) {
            log.info("停止生成单证,因为未设置单证配置,赔案号:{},保单号:{}", claimNumber, policyNo);
            return;
        }

        //删除旧的同类型的单证影像件
        Criteria<ClaimImage> imageCriteria = Criteria.create();
        imageCriteria.eq(ClaimImage::getRelatedId, claimNumber)
                .in(ClaimImage::getCertificateType, List.of(CertificateConfigEnums.CertificateTemplateEnum.ITEM1.getCode(),
                        CertificateConfigEnums.CertificateTemplateEnum.ITEM2.getCode()));
        List<ClaimImage> oldCertificateImageList = imageRepository.findByCriteria(imageCriteria);
        if (!CollectionUtils.isEmpty(oldCertificateImageList)) {
            imageRepository.deleteByIds(oldCertificateImageList.stream().map(AbstractEntity::getId).toList());
            log.info("旧单证数据删除完成,赔案号:{}", claimNumber);
        }

        boolean createApplicationFlag = false, createNotificationFlag = false;
        try {
            log.info("开始读取单证配置并且生成证书,赔案号:{},保单号:{}", claimNumber, policyNo);
            try {
                //理赔申请书配置处理
                Boolean createApplication = certificateConfig.getCreateApplication();
                CertificateConfig.ConfigNode applicationConfig = certificateConfig.getApplicationConfig();
                if (createApplication != null && createApplication && applicationConfig != null) {
                    //根据配置来决定是否生成单证
                    createApplicationFlag = getApplicationConfigFlag(claimNumber, applicationConfig, claim);
                    if (createApplicationFlag) {
                        String template = applicationConfig.getCertificateTemplate();
                        String imageType = applicationConfig.getImageType();
                        String imageTypePK = applicationConfig.getImageTypePK();
                        createCertificate(claim, template, imageType, imageTypePK);
                    }
                }
            } catch (Exception e) {
                log.error("理赔申请书生成发生异常,赔案号:{},保单号:{}", claimNumber, policyNo, e);
            }

            //理赔通知书配置处理
            try {
                Boolean createNotification = certificateConfig.getCreateNotification();
                CertificateConfig.ConfigNode notificationConfig = certificateConfig.getNotificationConfig();
                if (createNotification != null && createNotification && notificationConfig != null) {
                    //根据配置来决定是否生成单证
                    createNotificationFlag = getNotificationConfigFlag(claimNumber, notificationConfig, claim);
                    if (createNotificationFlag) {
                        String template = notificationConfig.getCertificateTemplate();
                        String imageType = notificationConfig.getImageType();
                        String imageTypePK = notificationConfig.getImageTypePK();
                        createCertificate(claim, template, imageType, imageTypePK);
                    }
                }
            } catch (Exception e) {
                log.error("理赔通知书生成发生异常,赔案号:{},保单号:{}", claimNumber, policyNo, e);
            }
        } finally {
            log.info("结束读取单证配置并且生成证书,赔案号:{},保单号:{}, 是否需要生成理赔申请书:{}, 是否需要生成理赔通知书:{}", claimNumber, policyNo, createApplicationFlag, createNotificationFlag);
        }
    }

    /**
     * 根据理赔通知书配置来决定是否生成单证, true:生成,false:不生成
     */
    private boolean getNotificationConfigFlag(Long claimNumber, CertificateConfig.ConfigNode notificationConfig, Claim claim) {
        //适用赔案
        if (!getClaimAuditTypeFlag(notificationConfig, claim)) {
            return false;
        }

        //生成节点
        if (!getProcessNodeFlag(notificationConfig, claim)) {
            return false;
        }

        //生成条件（理赔结论）-下拉框
        if (!getAdjustmentConclusionFlag(notificationConfig, claimNumber)) {
            return false;
        }
        return true;
    }

    /**
     * 根据理赔申请书配置来决定是否生成单证, true:生成,false:不生成
     */
    private boolean getApplicationConfigFlag(Long claimNumber, CertificateConfig.ConfigNode applicationConfig, Claim claim) {
        //暂时：如果是领款单位就不生成
        if (claim.getCollectType().equals(CollectTypeEnum.COMPANY.getCode())) {
            return false;
        }

        //适用赔案
        if (!getClaimAuditTypeFlag(applicationConfig, claim)) {
            return false;
        }

        //生成节点
        if (!getProcessNodeFlag(applicationConfig, claim)) {
            return false;
        }

        //理赔结论
        if (!getAdjustmentConclusionFlag(applicationConfig, claimNumber)) {
            return false;
        }
        return true;
    }

    /**
     * 根据条件(理赔结论)判断是否不生成单证, true:生成,false:不生成
     */
    private boolean getAdjustmentConclusionFlag(CertificateConfig.ConfigNode config, Long claimNumber) {
        String adjustmentConclusion = config.getAdjustmentConclusion();
        if (!StringUtils.hasText(adjustmentConclusion) ||
                adjustmentConclusion.equals(CertificateConfigEnums.AdjustmentConclusionEnum.ALL.getCode())) {
            return true;
        }

        Criteria<AdjustmentResult> criteria = Criteria.create();
        criteria.eq(AdjustmentResult::getClaimId, claimNumber);
        List<AdjustmentResult> adjustmentResults = adjustmentResultRepository.findByCriteria(criteria);
        if (CollectionUtils.isEmpty(adjustmentResults) || adjustmentResults.size() > 1 ||
                CollectionUtils.isEmpty(adjustmentResults.get(0).getResultDesc())) {
            log.info("停止生成理赔申请书,因为理算结论记录数量大于1或理算结论不存在,claimNumber:" + claimNumber);
            return false;
        }

        List<String> resultDescList = adjustmentResults.get(0).getResultDesc();
        if (!adjustmentConclusion.equals(resultDescList.get(0))) {
            log.info("停止生成理赔申请书,因为条件(理赔结论)不匹配");
            return false;
        }
        return true;
    }

    /**
     * 根据条件(生成节点)判断是否不生成单证, true:生成,false:不生成
     */
    private boolean getProcessNodeFlag(CertificateConfig.ConfigNode config, Claim claim) {
//        String processNode = config.getClaimProcessNode();
//        if (!StringUtils.hasText(processNode)) {
//            return true;
//        }
//
//        String claimStatus = claim.getStatus();
//        if (!StringUtils.hasText(claimStatus) || Integer.parseInt(claimStatus) < Integer.parseInt(processNode)) {
//            return false;
//        }
        return true;
    }

    /**
     * 根据条件(适用赔案)判断是否不生成单证, true:生成,false:不生成
     */
    private boolean getClaimAuditTypeFlag(CertificateConfig.ConfigNode config, Claim claim) {
        Integer claimAuditType = config.getClaimAuditType();
        if (claimAuditType == null || CertificateConfigEnums.ClaimAuditTypeEnum.ALL.getCode().equals(claimAuditType)) {
            return true;
        }

        String source = claim.getSource();
        if (!String.valueOf(claimAuditType).equals(source)) {
            log.info("停止生成理赔申请书,因为条件(适用赔案)不匹配");
            return false;
        }
        return true;
    }

    /**
     * 根据单证配置生成对应的文件,并作为影像件保存
     */
    public void createCertificate(Claim claim, String certificateType, String imgType, String imgTypePK) {
        Long claimNumber = claim.getId();
        String imgPath = null, imgUrl = null;
        try {
            log.info("开始生成单证,claimNumber:{}, certificateType:{}", claimNumber, certificateType);
            if (CertificateConfigEnums.CertificateTemplateEnum.ITEM1.getCode().equals(certificateType)) {
                imgPath = createYCLpjatzs(claim);
            } else if (CertificateConfigEnums.CertificateTemplateEnum.ITEM2.getCode().equals(certificateType)) {
                imgPath = createYCLpsqs(claim);
            } else {
                throw new RuntimeException("不支持的证书类型,赔案号:" + claimNumber + ",证书类型:" + certificateType);
            }
            if (!StringUtils.hasText(imgPath)) {
                throw new RuntimeException("证书生成失败,赔案号:" + claimNumber + ",证书类型:" + certificateType);
            }
            log.info("证书生成成功, claimNumber:{}, certificateType:{}", claimNumber, certificateType);

            imgUrl = uploadToOSS(imgPath);
            if (!StringUtils.hasText(imgUrl)) {
                throw new RuntimeException("证书上传到oss失败,赔案号:" + claimNumber + ",证书类型:" + certificateType + ",imgUrl:" + imgUrl);
            }

            //新增单证影像件
            ClaimImage claimImage = new ClaimImage();
            claimImage.setTenantId(claim.getTenantId());
            claimImage.setRelatedId(claimNumber);
            claimImage.setImageDetailId(UUID.randomUUID().toString());
            claimImage.setSourceSystem(1);
            claimImage.setImagePath(imgUrl);
            int index = imgUrl.lastIndexOf("/");
            claimImage.setImageName(index >= 0 ? imgUrl.substring(index + 1) : "异常影像件地址:" + imgUrl);
            claimImage.setImageType(imgType);
            claimImage.setImagePkType(imgTypePK);
            claimImage.setClearType(1);
            claimImage.setRemark("通过单证配置生成,赔案号:" + claimNumber + ",证书类型:" + certificateType);
            claimImage.setOcrFlag(0);
            claimImage.setPushFlag(1);
            claimImage.setCertificateType(certificateType);
            claimImage.setCreateTime(new Date());
            imageRepository.insert(claimImage);
        } finally {
            claimTrackLogService.addActionRecord(claim, "system", null, OperationTypeEnum.CREATE_CERTIFICATE, "certificateType" + certificateType, imgUrl);
            log.info("结束生成单证,claimNumber:{}, certificateType:{}, imgUrl:{}", claimNumber, certificateType, imgUrl);
            if (StringUtils.hasText(imgPath)) {
                new File(imgPath).delete();
            }
        }
    }

    private String uploadToOSS(String imgPath) {
        try {
            return ossUtil.upload(new FileInputStream(imgPath), imgPath.substring(imgPath.lastIndexOf(File.separator) + 1));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private HashMap<String, Object> getDataMapForYCLpjatzs(Claim claim) {
        Long claimNumber = claim.getId();

        Criteria<ClaimStakeholder> outInsureCriteria = new Criteria<>();
        outInsureCriteria.eq(ClaimStakeholder::getRelatedId, claimNumber)
                .eq(ClaimStakeholder::getPersonType, PersonTypeEnum.OUT_INSURE.getCode());
        List<ClaimStakeholder> outInsurePeoples = stakeholderRepository.findByCriteria(outInsureCriteria);
        if (CollectionUtils.isEmpty(outInsurePeoples)) {
            throw new RuntimeException("出险人不存在,claimNumber:" + claimNumber);
        }
        ClaimStakeholder outInsurePeople = outInsurePeoples.get(0);
        log.info("通知书outInsurePeople:{}", JSON.toJSONString(outInsurePeople));

        Criteria<ClaimStakeholder> collectCriteria = new Criteria<>();
        collectCriteria.eq(ClaimStakeholder::getRelatedId, claimNumber)
                .eq(ClaimStakeholder::getPersonType, PersonTypeEnum.COLLECT.getCode());
        List<ClaimStakeholder> collectPeoples = stakeholderRepository.findByCriteria(collectCriteria);
        if (CollectionUtils.isEmpty(collectPeoples)) {
            throw new RuntimeException("领款人不存在,claimNumber:" + claimNumber);
        }
        ClaimStakeholder collectPeople = collectPeoples.get(0);
        log.info("通知书collectPeople:{}", JSON.toJSONString(collectPeople));

        AdjustmentResult adjustmentResult = null;
        BigDecimal compensationAmount = BigDecimal.ZERO;
        Criteria<AdjustmentResult> criteria = Criteria.create();
        criteria.eq(AdjustmentResult::getClaimId, claimNumber);
        List<AdjustmentResult> adjustmentResults = adjustmentResultRepository.findByCriteria(criteria);
        if (!CollectionUtils.isEmpty(adjustmentResults)) {
            adjustmentResult = adjustmentResults.get(0);
            compensationAmount = adjustmentResult.getPayoutAmount();
        }
        log.info("通知书adjustmentResult:{}", JSON.toJSONString(adjustmentResult));

        Criteria<ClaimInvoice> invoiceCriteria = Criteria.create();
        invoiceCriteria.eq(ClaimInvoice::getRelatedId, claimNumber);
        List<ClaimInvoice> invoiceList = invoiceRepository.findByCriteria(invoiceCriteria);
        BigDecimal invoiceAmount = BigDecimal.ZERO;
        BigDecimal pooledAmount = BigDecimal.ZERO;
        BigDecimal totalSelfPayAmount = BigDecimal.ZERO;
        BigDecimal partSelfPayAmount = BigDecimal.ZERO;
        BigDecimal unReasonableAmount = BigDecimal.ZERO;
        BigDecimal thirdPartyAmount = BigDecimal.ZERO;
        for (ClaimInvoice invoice : invoiceList) {
            invoiceAmount = invoiceAmount.add(Objects.isNull(invoice.getTotalAmount()) ? BigDecimal.ZERO : invoice.getTotalAmount());
            pooledAmount = pooledAmount.add(Objects.isNull(invoice.getBasicPoolingAmount()) ? BigDecimal.ZERO : invoice.getBasicPoolingAmount());
            totalSelfPayAmount = totalSelfPayAmount.add(Objects.isNull(invoice.getTotalSelfPayAmount()) ? BigDecimal.ZERO : invoice.getTotalSelfPayAmount());
            partSelfPayAmount = partSelfPayAmount.add(Objects.isNull(invoice.getSelfPayPart2Amount()) ? BigDecimal.ZERO : invoice.getSelfPayPart2Amount());
            unReasonableAmount = unReasonableAmount.add(Objects.isNull(invoice.getInvalidAmount()) ? BigDecimal.ZERO : invoice.getInvalidAmount());
            thirdPartyAmount = thirdPartyAmount.add(Objects.isNull(invoice.getThirdPartyPaidAmount()) ? BigDecimal.ZERO : invoice.getThirdPartyPaidAmount());
        }
        log.info("invoiceList:{}", JSON.toJSONString(invoiceList));

        Criteria<AdjustmentRecord> adjustmentRecordCriteria = Criteria.create();
        adjustmentRecordCriteria.eq(AdjustmentRecord::getRelatedId, claimNumber);
        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordRepository.findByCriteria(adjustmentRecordCriteria);
        log.info("adjustmentRecordList:{}", JSON.toJSONString(adjustmentRecordList));

        HashMap<String, Object> dataMap = new HashMap<>();
        dataMap.put("outInsureName", outInsurePeople.getName());
        dataMap.put("gyTaskNo", claim.getInsurerClaimNo());
        dataMap.put("compensationAmount", compensationAmount.setScale(2, RoundingMode.HALF_UP).toString());
        dataMap.put("compensationAmountChinese", convertToChinese(compensationAmount));

        //给付款项支付明细
        dataMap.put("collectUser", collectPeople.getName());
        dataMap.put("bankName", collectPeople.getBankCodeCn());
        dataMap.put("accountNo", collectPeople.getAccountNo());

        //保单
        dataMap.put("policyNo", claim.getPolicyNo());

        String policyStartDate = claim.getPolicyStartDate();
        if (StringUtils.hasText(policyStartDate)) {
            if (policyStartDate.length() == 19) {
                dataMap.put("effDate", policyStartDate.substring(0, 10).replaceAll("-", "/"));
            } else {
                dataMap.put("effDate", policyStartDate.replaceAll("-", "/"));
            }
        }

        String policyEndDate = claim.getPolicyEndDate();
        if (StringUtils.hasText(policyEndDate)) {
            if (policyEndDate.length() == 19) {
                dataMap.put("expDate", policyEndDate.substring(0, 10).replaceAll("-", "/"));
            } else {
                dataMap.put("expDate", policyEndDate.replaceAll("-", "/"));
            }
        }

        dataMap.put("claimNumber", String.valueOf(claimNumber));
        if (adjustmentResult != null) {
            List<String> descList = adjustmentResult.getResultDesc();
            dataMap.put("compensationConclusionName", !CollectionUtils.isEmpty(descList) ? descList.get(0) : "");
            dataMap.put("claimRejectedRemark", adjustmentResult.getResultDetail());
        }

        //医疗账单信息
        dataMap.put("invoiceAmount", invoiceAmount.setScale(2, RoundingMode.HALF_UP).toString());
        dataMap.put("pooledAmount", pooledAmount.setScale(2, RoundingMode.HALF_UP).toString());
        dataMap.put("selfPayAmount", totalSelfPayAmount.setScale(2, RoundingMode.HALF_UP).toString());
        dataMap.put("partSelfPayAmount", partSelfPayAmount.setScale(2, RoundingMode.HALF_UP).toString());
        dataMap.put("unReasonableAmount", unReasonableAmount.setScale(2, RoundingMode.HALF_UP).toString());
        dataMap.put("thirdPartyAmount", thirdPartyAmount.setScale(2, RoundingMode.HALF_UP).toString());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        // 医疗账单明细
        List<Map<String, Object>> invoiceDataList = new ArrayList<>(adjustmentRecordList.size());
        for (AdjustmentRecord record : adjustmentRecordList) {
            Map<String, Object> invoiceMap = new HashMap<>();
            invoiceMap.put("invoiceNo", record.getInvoiceNo());
            invoiceMap.put("liveStartDate", record.getVisitDate() != null ? sdf.format(record.getVisitDate()) : "");
            invoiceMap.put("compensationAmount", record.getPayoutAmount() != null ? record.getPayoutAmount().setScale(2, RoundingMode.HALF_UP).toString() : "");
            invoiceMap.put("responsibilityName", record.getLiabilityName());
            invoiceMap.put("formulaValue", record.getFormula());
            invoiceDataList.add(invoiceMap);
        }
        dataMap.put("invoiceList", invoiceDataList);

        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
        if (claim.getReviewingPassTime() != null) {
            dataMap.put("auditEndTime", sdf1.format(claim.getReviewingPassTime()));
        } else if (claim.getAuditingPassTime() != null) {
            dataMap.put("auditEndTime", sdf1.format(claim.getAuditingPassTime()));
        }
        return dataMap;
    }

    public static String convertToChinese(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "零圆整";
        }

        // 分离整数部分和小数部分
        String[] parts = amount.toString().split("\\.");
        String integerPart = parts[0];
        String decimalPart = parts.length > 1 ? parts[1] : "";

        // 转换整数部分
        String chineseInteger = convertIntegerPart(integerPart);

        // 转换小数部分
        String chineseDecimal = convertDecimalPart(decimalPart);

        // 拼接结果
        StringBuilder result = new StringBuilder();
        if (!chineseInteger.isEmpty()) {
            result.append(chineseInteger);
            if (chineseDecimal.isEmpty()) {
                result.append("圆整");
            } else {
                result.append("圆").append(chineseDecimal);
            }
        } else {
            result.append("零圆").append(chineseDecimal.isEmpty() ? "整" : chineseDecimal);
        }

        return result.toString();
    }

    private static String convertIntegerPart(String integerPart) {
        if ("0".equals(integerPart)) {
            return "零";
        }

        StringBuilder result = new StringBuilder();
        int length = integerPart.length();

        for (int i = 0; i < length; i++) {
            int num = Character.getNumericValue(integerPart.charAt(i));
            int unitIndex = (length - i - 1) % 4;
            int bigUnitIndex = (length - i - 1) / 4;

            if (num != 0) {
                result.append(CN_NUMS[num]).append(CN_UNITS[unitIndex]);
            } else {
                // 处理连续的零
                if (result.length() > 0 && !"零".equals(result.substring(result.length() - 1))) {
                    result.append(CN_NUMS[num]);
                }
            }

            // 添加大单位（万、亿等）
            if (unitIndex == 0 && num != 0) {
                result.append(CN_BIG_UNITS[bigUnitIndex]);
            }
        }

        // 去掉末尾多余的零
        while (result.length() > 0 && "零".equals(result.substring(result.length() - 1))) {
            result.deleteCharAt(result.length() - 1);
        }

        return result.toString();
    }

    private static String convertDecimalPart(String decimalPart) {
        if (decimalPart.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String[] decimalUnits = {"角", "分"};

        for (int i = 0; i < decimalPart.length() && i < 2; i++) {
            int num = Character.getNumericValue(decimalPart.charAt(i));
            if (num != 0) {
                result.append(CN_NUMS[num]).append(decimalUnits[i]);
            } else if (i == 0 && result.length() == 0) {
                result.append("零");
            }
        }

        return result.toString();
    }

    /**
     * 生成永诚_理赔结案通知书
     */
    public String createYCLpjatzs(Claim claim) {
        HashMap<String, Object> dataMap = getDataMapForYCLpjatzs(claim);
        log.info("通知书data:{}", JSON.toJSONString(dataMap));

        logMemoryUsage("开始创建通知书", claim.getId());

        try (OutputStream outputStream = new ByteArrayOutputStream()) {
            String htmlStr = getHtmlStr("yc_lpjatzs.html", dataMap);

            // html转pdf
            htmlStrToPdf(htmlStr, outputStream);

            // 输出流转输入流
            InputStream inputStream = outStream2InStream(outputStream);

            // pdf转图片
            return pdfToJpg(inputStream, null);
        } catch (Throwable e) {
            throw new RuntimeException("生成永诚_理赔结案通知书发生异常,赔案号:" + claim.getClaimNo(), e);
        } finally {
            logMemoryUsage("结束创建通知书", claim.getId());
        }
    }

    /**
     * pdf转图片
     */
    public static String pdfToJpg(InputStream inputStream, Integer jpgHeight) {
        try {
            //图像合并使用参数
            int width = 0; // 总宽度
            int height = 0; // 总高度
            int[] singleImgRGB; // 保存一张图片中的RGB数据
            int shiftHeight = 0;
            BufferedImage imageResult = null;//保存每张图片的像素值

            //利用PdfBox生成图像
            PDDocument pdDocument = PDDocument.load(inputStream);
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            BufferedImage lastImage = renderer.renderImageWithDPI(pdDocument.getNumberOfPages() - 1, DEFAULT_DPI, ImageType.RGB);
            int lastPageHeight = getActualHeightWithColorBackground(lastImage, Color.WHITE);

            //循环每个页码
            for (int i = 0, len = pdDocument.getNumberOfPages(); i < len; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DEFAULT_DPI, ImageType.RGB);
                int imageHeight = image.getHeight();
                int imageWidth = image.getWidth();
                if (i == 0) {//计算高度和偏移量
                    width = imageWidth;//使用第一张图片宽度;
                    int totalHeight = height = imageHeight * (len - 1) + lastPageHeight + 100;
                    //保存每页图片的像素值
                    imageResult = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_RGB);
                } else {
                    shiftHeight += imageHeight; // 计算偏移高度
                }
                if (i == len - 1) {
                    // +20为了最后一页地下的文字被遮住
                    if (imageHeight > lastPageHeight + 100) {
                        imageHeight = lastPageHeight + 100;
                    }
                }
                singleImgRGB = image.getRGB(0, 0, width, imageHeight, null, 0, width);
                imageResult.setRGB(0, shiftHeight, width, imageHeight, singleImgRGB, 0, width); // 写入流中
            }
            pdDocument.close();
            if (Objects.nonNull(jpgHeight)) {
                imageResult = cropImageFromBottom(imageResult, height - jpgHeight);
            }

            String relativePath = System.getProperty("user.dir") + File.separator + "temFile";
            File dir = new File(relativePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String absolutePath = relativePath + File.separator + System.currentTimeMillis() + "_永诚_理赔结案通知书.jpg";
            File outFile = new File(absolutePath);
            ImageIO.write(imageResult, "jpg", outFile);// 写图片
            return absolutePath;
        } catch (Exception e) {
            throw new RuntimeException("pdf转图片发生异常", e);
        }
    }

    /**
     * 图片裁切掉底部指定高度
     *
     * @param originalImage 原始图像
     * @param heightToCrop  底部需要裁切掉的高度
     * @return BufferedImage 裁切后的图像
     */
    public static BufferedImage cropImageFromBottom(BufferedImage originalImage, int heightToCrop) {
        // 检查裁切高度是否有效
        if (heightToCrop < 0 || heightToCrop > originalImage.getHeight()) {
            throw new IllegalArgumentException("Invalid height to crop: " + heightToCrop);
        }

        // 计算裁切后图像的新高度
        int newHeight = originalImage.getHeight() - heightToCrop;

        // 使用getSubimage方法裁切图像
        // 起始y坐标为原始图像高度减去裁切高度，宽度不变，新高度为newHeight
        BufferedImage croppedImage = originalImage.getSubimage(0, 0, originalImage.getWidth(), newHeight);
        return croppedImage;
    }

    public static int getActualHeightWithColorBackground(BufferedImage image, Color backgroundColor) {
        int bgRGB = backgroundColor.getRGB();
        int width = image.getWidth();
        int height = image.getHeight();

        int yMin = findYMin(image, width, height, bgRGB);
        int yMax = findYMax(image, width, height, bgRGB);

        return (yMin == -1 || yMax == -1) ? 0 : yMax - yMin + 1;
    }

    // 辅助方法：查找上边界
    private static int findYMin(BufferedImage image, int width, int height, int bgRGB) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (image.getRGB(x, y) != bgRGB) {
                    return y;
                }
            }
        }
        return -1; // 全部是背景
    }

    // 辅助方法：查找下边界
    private static int findYMax(BufferedImage image, int width, int height, int bgRGB) {
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                if (image.getRGB(x, y) != bgRGB) {
                    return y;
                }
            }
        }
        return -1; // 全部是背景
    }

    private static InputStream outStream2InStream(OutputStream outputStream) {
        if (null == outputStream) {
            throw new RuntimeException("输出流不存在！");
        }

        ByteArrayInputStream inputStream = null;
        try (ByteArrayOutputStream temStream = (ByteArrayOutputStream) outputStream) {
            inputStream = new ByteArrayInputStream(temStream.toByteArray());
            return inputStream;
        } catch (IOException e) {
            throw new RuntimeException("流转换发生异常", e);
        }
    }

    private static void htmlStrToPdf(String htmlStr, OutputStream outputStream) {
        try {
            // 创建转换器属性
            ConverterProperties properties = new ConverterProperties();

            // 配置字体提供者以支持中文
            FontProvider fontProvider = new FontProvider();
            ClassPathResource fontResource = new ClassPathResource("certificateTemplate/font/SimSun.ttf");
            InputStream fontStream = fontResource.getInputStream();
            // 添加字体到字体提供者
            fontProvider.addFont(fontStream.readAllBytes(), BaseFont.IDENTITY_H);
            fontProvider.addStandardPdfFonts();
            fontProvider.addSystemFonts();

            // 设置字体提供者
            properties.setFontProvider(fontProvider);

            // 设置字符编码
            properties.setCharset("UTF-8");

            // 执行转换
            HtmlConverter.convertToPdf(htmlStr, outputStream, properties);
        } catch (Exception e) {
            throw new RuntimeException("html转pdf发生异常", e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush();
                } catch (IOException ex) {
                    log.error("", ex);
                }
            }
        }
    }

    private static String getHtmlStr(String templateName, HashMap<String, Object> dataMap) throws Exception {
        // 初始化FreeMarker配置
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassForTemplateLoading(CreateCertificateApplicationService.class, "/certificateTemplate");
        configuration.setDefaultEncoding("UTF-8");

        // 获取模板
        Template template = configuration.getTemplate(templateName);

        // 使用StringWriter输出HTML字符串
        StringWriter writer = new StringWriter();
        template.process(dataMap, writer);
        writer.flush();
        return writer.toString();
    }

    public String createYCLpsqs(Claim claim) {
        Long claimNumber = claim.getId();
        Map<String, Object> fieldData = getFieldDataForYCLpsqs(claim);
        log.info("开始生成永诚理赔申请书，赔案号: {}, 数据量: {}", claimNumber, fieldData.size());
        byte[] img = getSignature(claimNumber);

        // 定义临时工作目录，确保最后被清理
        String timestamp = String.valueOf(System.currentTimeMillis());
        String tempWorkDir = System.getProperty("java.io.tmpdir") + File.separator + "pdf_gen_" + timestamp + "_" + claimNumber;
        File tempDirFile = new File(tempWorkDir);
        tempDirFile.mkdirs();

        // 定义关键文件路径
        String tempPdfPath = tempWorkDir + File.separator + "filled_form.pdf";
        String finalImagePath = null; // 最终要返回的图片路径

        try {
            // ---------- 第一阶段：生成填充后的PDF到临时文件 ----------
            log.info("第一阶段：生成PDF到临时文件，赔案号: {}", claimNumber);
            generatePdfToFile(claim, fieldData, img, tempPdfPath);
            logMemoryUsage("PDF生成后", claimNumber);

            // ---------- 第二阶段：将PDF转换为图片（内存安全模式） ----------
            log.info("第二阶段：转换PDF为图片，赔案号: {}", claimNumber);
            finalImagePath = convertPdfToImageSafely(tempPdfPath, claimNumber);
            log.info("PDF转换成功，最终图片路径: {}, 赔案号: {}", finalImagePath, claimNumber);

            return finalImagePath;

        } catch (Throwable e) {
            log.error("生成永诚理赔申请书发生严重异常, 赔案号: {}", claimNumber, e);
            // 尝试清理可能已创建的半成品文件
            if (finalImagePath != null && new File(finalImagePath).exists()) {
                new File(finalImagePath).delete();
            }
            throw new RuntimeException("生成永诚_意健险理赔申请书发生异常,赔案号:" + claimNumber, e);
        } finally {
            // ---------- 第三阶段：异步清理临时工作目录 ----------
            cleanupTempDirAsync(tempDirFile);
            logMemoryUsage("整个流程结束", claimNumber);
        }
    }

    /**
     * 生成PDF到指定文件路径 (原逻辑，但输出到文件)
     */
    private void generatePdfToFile(Claim claim, Map<String, Object> fieldData, byte[] img, String outputPdfPath)
            throws Exception {
        Long claimNumber = claim.getId();
        try (InputStream pdfStream = new ClassPathResource("certificateTemplate/yc_lpsqs.pdf").getInputStream();
             FileOutputStream pdfOutput = new FileOutputStream(outputPdfPath);
             InputStream fontStream = new ClassPathResource("certificateTemplate/font/STSong-Light.ttf").getInputStream()) {

            PdfReader reader = new PdfReader(pdfStream);
            PdfStamper stamper = new PdfStamper(reader, pdfOutput);

            try {
                AcroFields form = stamper.getAcroFields();
                // 字体处理：读取字节并创建
                byte[] fontData = fontStream.readAllBytes();
                BaseFont baseFont = BaseFont.createFont("STSong-Light.ttf", BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED, true, fontData, null);

                form.addSubstitutionFont(baseFont);
                for (String key : form.getFields().keySet()) {
                    form.setFieldProperty(key, "textfont", baseFont, null);
                }
                stamper.setFormFlattening(true);

                // 插入签名图片
                if (img != null) {
                    PdfContentByte content = stamper.getOverContent(1);
                    Image img1 = Image.getInstance(img);
                    img1.scaleToFit(60, 60);
                    Rectangle imageFieldRect1 = form.getFieldPositions("img1").get(0).position;
                    float x1 = imageFieldRect1.getLeft() + (imageFieldRect1.getWidth() - img1.getScaledWidth()) / 2;
                    float y1 = imageFieldRect1.getBottom() + (imageFieldRect1.getHeight() - img1.getScaledHeight()) / 2;
                    img1.setAbsolutePosition(x1, y1);
                    content.addImage(img1);
                }

                // 填充表单数据
                fieldData.forEach((key, value) -> {
                    try {
                        form.setFieldProperty(key, "textfont", baseFont, null);
                        form.setFieldProperty(key, "textsize", 10f, null);
                        String fieldValue = value != null ? value.toString() : "";
                        form.setField(key, fieldValue);
                    } catch (Exception e) {
                        log.warn("填充字段[{}]失败，赔案号:{}，将跳过。异常: {}", key, claimNumber, e.getMessage());
                    }
                });
                log.info("PDF表单填充完成，赔案号: {}", claimNumber);
            } finally {
                stamper.close();
                reader.close();
            }
        }
    }

    /**
     * 安全地将PDF转换为图片（核心优化方法）
     */
    private String convertPdfToImageSafely(String pdfFilePath, Long claimNumber) throws Exception {
        File pdfFile = new File(pdfFilePath);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String finalImagePath = System.getProperty("user.dir") + File.separator + "temFile" +
                File.separator + timestamp + "_永诚_意健险理赔申请书.jpg";

        // 确保输出目录存在
        new File(System.getProperty("user.dir") + File.separator + "temFile").mkdirs();

        PDDocument document = null;
        try {
            // >>> 核心优化1：使用临时文件模式加载，大幅减少内存占用 <<<
            document = PDDocument.load(pdfFile, MemoryUsageSetting.setupTempFileOnly());

            // >>> 核心优化2：禁用内部资源缓存 <<<
            document.setResourceCache(null);

            PDFRenderer renderer = new PDFRenderer(document);
            // >>> 核心优化3：启用子采样 <<<
            renderer.setSubsamplingAllowed(true);

            int pageCount = document.getNumberOfPages();
            log.info("开始渲染PDF，总页数: {}, 赔案号: {}", pageCount, claimNumber);

            // >>> 核心优化4：根据内存情况动态选择DPI <<<
            int dpi = calculateSafeDpi(claimNumber);
            log.info("动态计算渲染DPI: {}, 赔案号: {}", dpi, claimNumber);

            if (pageCount == 1) {
                // 单页PDF - 直接渲染并保存
                try (FileOutputStream fos = new FileOutputStream(finalImagePath)) {
                    BufferedImage image = renderer.renderImageWithDPI(0, dpi);
                    saveImageWithCompression(image, fos, 0.85f); // 85% JPEG质量
                    image.flush();
                    log.info("单页PDF渲染完成，赔案号: {}", claimNumber);
                }
            } else {
                // 多页PDF - 使用流式处理避免内存堆积
                log.info("处理多页PDF，使用流式处理，赔案号: {}", claimNumber);
                finalImagePath = processMultiPageSafely(renderer, pageCount, dpi, finalImagePath, claimNumber);
            }

            return finalImagePath;
        } finally {
            if (document != null) {
                document.close();
            }
        }
    }

    /**
     * 动态计算安全的DPI值
     */
    private int calculateSafeDpi(Long claimNumber) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long freeMemory = maxMemory - usedMemory;

        // 根据可用内存动态调整DPI
        if (freeMemory > 500 * 1024 * 1024) { // 500MB以上
            return 200;
        } else if (freeMemory > 300 * 1024 * 1024) { // 300MB以上
            return 150;
        } else if (freeMemory > 150 * 1024 * 1024) { // 150MB以上
            return 120;
        } else {
            // 内存紧张，使用最低可接受质量
            log.warn("内存紧张，使用保守DPI: 96，可用内存: {}MB, 赔案号: {}",
                    freeMemory / 1024 / 1024, claimNumber);
            return 96;
        }
    }

    /**
     * 安全地处理多页PDF（流式处理，避免将所有页面同时加载到内存）
     */
    private String processMultiPageSafely(PDFRenderer renderer, int pageCount, int dpi,
                                          String outputPath, Long claimNumber) throws Exception {
        // 用于保存每页临时图片的路径列表
        List<String> tempPagePaths = new ArrayList<>();
        String tempDir = new File(outputPath).getParent() + File.separator + "pages_" + System.currentTimeMillis();
        new File(tempDir).mkdirs();

        try {
            // 第一步：逐页渲染到独立的临时文件
            for (int i = 0; i < pageCount; i++) {
                String pageImagePath = tempDir + File.separator + "page_" + i + ".jpg";

                try (FileOutputStream fos = new FileOutputStream(pageImagePath)) {
                    log.debug("正在渲染第 {} 页/共 {} 页，赔案号: {}", i + 1, pageCount, claimNumber);
                    BufferedImage pageImage = renderer.renderImageWithDPI(i, dpi);
                    saveImageWithCompression(pageImage, fos, 0.9f);
                    pageImage.flush();
                    tempPagePaths.add(pageImagePath);
                }

                // 每处理2页后，检查内存并可能触发GC（针对极大文件）
                if (i % 2 == 0) {
                    long freeMem = Runtime.getRuntime().freeMemory();
                    if (freeMem < 100 * 1024 * 1024) { // 小于100MB时触发GC
                        System.gc();
                        Thread.sleep(10);
                    }
                }
            }

            // 第二步：合并所有页面图片（使用内存安全的方式）
            mergePageImages(tempPagePaths, outputPath, claimNumber);
            log.info("多页PDF合并完成，总页数: {}, 赔案号: {}", pageCount, claimNumber);

            return outputPath;
        } finally {
            // 第三步：清理所有临时单页文件
            for (String tempPath : tempPagePaths) {
                try {
                    new File(tempPath).delete();
                } catch (Exception e) {
                    log.warn("删除临时文件失败: {}，赔案号: {}", tempPath, claimNumber, e);
                }
            }
            // 删除临时目录
            new File(tempDir).delete();
        }
    }

    /**
     * 合并多个页面图片为一个（优化内存版本）
     */
    private void mergePageImages(List<String> pagePaths, String outputPath, Long claimNumber) throws Exception {
        if (pagePaths.isEmpty()) {
            throw new IllegalArgumentException("页面图片列表为空，赔案号:" + claimNumber);
        }

        // 读取第一张图片获取尺寸（然后立即关闭）
        int pageWidth = 0;
        int pageHeight = 0;
        try {
            BufferedImage firstImage = ImageIO.read(new File(pagePaths.get(0)));
            pageWidth = firstImage.getWidth();
            pageHeight = firstImage.getHeight();
            firstImage.flush();
        } catch (Exception e) {
            throw new RuntimeException("读取第一页图片失败，赔案号:" + claimNumber, e);
        }

        int totalHeight = pageHeight * pagePaths.size();

        // 创建最终图片
        BufferedImage combinedImage = new BufferedImage(pageWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = combinedImage.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setBackground(Color.WHITE);
            g2d.clearRect(0, 0, pageWidth, totalHeight);

            // 逐页绘制
            int currentY = 0;
            for (int i = 0; i < pagePaths.size(); i++) {
                File pageFile = new File(pagePaths.get(i));
                BufferedImage pageImage = ImageIO.read(pageFile);

                if (pageImage != null) {
                    g2d.drawImage(pageImage, 0, currentY, null);
                    currentY += pageHeight;

                    // 立即释放单页内存
                    pageImage.flush();

                    // 每合并几页后检查内存
                    if (i % 3 == 0 && Runtime.getRuntime().freeMemory() < 50 * 1024 * 1024) {
                        System.gc();
                    }
                }
            }
        } finally {
            g2d.dispose();
        }

        // 保存合并后的图片
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            saveImageWithCompression(combinedImage, fos, 0.85f);
            combinedImage.flush();
        }
    }

    /**
     * 用压缩方式保存图片（减少磁盘空间）
     */
    private void saveImageWithCompression(BufferedImage image, OutputStream output, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
        if (!writers.hasNext()) {
            // 回退到普通写入方式
            ImageIO.write(image, "JPEG", output);
            return;
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality); // 设置JPEG质量
            }

            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /**
     * 异步清理临时目录
     */
    private void cleanupTempDirAsync(File tempDir) {
        CompletableFuture.runAsync(() -> {
            try {
                // 等待30秒，确保主流程已完成文件读取
                Thread.sleep(30000);

                if (tempDir.exists() && tempDir.isDirectory()) {
                    FileUtils.deleteDirectory(tempDir);
                    log.debug("已清理临时工作目录: {}", tempDir.getAbsolutePath());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("清理临时目录失败: {}，将稍后重试", tempDir.getAbsolutePath(), e);
                // 可以在此添加重试逻辑
            }
        });
    }

    /**
     * 获取签名图片
     */
    private byte[] getSignature(Long claimNumber) {
        Claim claim = claimRepository.findById(claimNumber);

        Criteria<ClaimStakeholder> mainInsureCriteria = new Criteria<>();
        mainInsureCriteria.eq(ClaimStakeholder::getRelatedId, claimNumber)
                .eq(ClaimStakeholder::getPersonType, PersonTypeEnum.MAIN_INSURE.getCode());
        List<ClaimStakeholder> mainInsurePeoples = stakeholderRepository.findByCriteria(mainInsureCriteria);
        ClaimStakeholder mainInsurePeople = mainInsurePeoples.get(0);

        String claimNo = claim.getInsurerClaimNo();
        String identityNo = mainInsurePeople.getIdentityNo();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            String imgUrl = null;
            LambdaQueryWrapper<PersonClaimDetail> wrapper1 = new LambdaQueryWrapper<>();
            wrapper1.eq(PersonClaimDetail::getDeleted, 0)
                    .eq(PersonClaimDetail::getClaimCode, claimNo)
                    .eq(PersonClaimDetail::getDetailImageType, 13)
                    .isNotNull(PersonClaimDetail::getDetailImgUrl);
            List<PersonClaimDetail> tpcClaimDetails = personClaimDetailMapper.selectList(wrapper1);
            if (CollUtil.isNotEmpty(tpcClaimDetails) && StrUtil.isNotBlank(tpcClaimDetails.get(0).getDetailImgUrl())) {
                String detailImgUrl = tpcClaimDetails.get(0).getDetailImgUrl();
                if (detailImgUrl.contains("http")) {
                    imgUrl = detailImgUrl;
                    log.info("getClaimApplySignatureImg-获取图片地址,有http-{}", imgUrl);
                } else {
                    imgUrl = "http://" + bucketName + "." + visit + "/" + detailImgUrl;
                    log.info("getClaimApplySignatureImg-获取图片地址,无http-{}", imgUrl);
                }
            } else {
                List<InsuPerson> insuPersonList = insuPersonMapper.selectList(new LambdaQueryWrapper<InsuPerson>()
                        .eq(InsuPerson::getPersonCertid, identityNo)
                        .eq(InsuPerson::getPersonDeleted, 0)
                        .isNotNull(InsuPerson::getPersonSignature));
                if (CollUtil.isNotEmpty(insuPersonList) && StrUtil.isNotBlank(insuPersonList.get(0).getPersonSignature())) {
                    imgUrl = insuPersonList.get(0).getPersonSignature();
                    log.info("getClaimApplySignatureImg-获取图片地址,空地址-{}", imgUrl);
                }
            }
            if (StrUtil.isBlank(imgUrl)) {
                return null;
            }

            URL url = new URL(imgUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5 * 1000);
            InputStream imgStream = connection.getInputStream();
            BufferedImage bufferedImage = ImageIO.read(imgStream);
            ImageIO.write(bufferedImage, "png", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("获取签名图片发生异常,赔案号:" + claimNumber, e);
        }
    }

    /**
     * 准备永诚_意健险理赔申请书相关数据
     */
    private Map<String, Object> getFieldDataForYCLpsqs(Claim claim) {
        Long claimNumber = claim.getId();

        Criteria<Policy> policyCriteria = Criteria.create();
        policyCriteria.eq(Policy::getPolicyNo, claim.getPolicyNo());
        List<Policy> policyList = policyRepository.findByCriteria(policyCriteria);
        if (CollectionUtils.isEmpty(policyList)) {
            throw new RuntimeException("保单不存在,claimNumber:" + claimNumber);
        }
        Policy policy = policyList.get(0);
        log.info("申请书policy:{}", JSON.toJSONString(policy));

        Criteria<ClaimStakeholder> outInsureCriteria = new Criteria<>();
        outInsureCriteria.eq(ClaimStakeholder::getRelatedId, claimNumber)
                .eq(ClaimStakeholder::getPersonType, PersonTypeEnum.OUT_INSURE.getCode());
        List<ClaimStakeholder> outInsurePeoples = stakeholderRepository.findByCriteria(outInsureCriteria);
        if (CollectionUtils.isEmpty(outInsurePeoples)) {
            throw new RuntimeException("出险人不存在,claimNumber:" + claimNumber);
        }
        ClaimStakeholder outInsurePeople = outInsurePeoples.get(0);
        log.info("申请书outInsurePeople:{}", JSON.toJSONString(outInsurePeople));

        Criteria<ClaimStakeholder> collectCriteria = new Criteria<>();
        collectCriteria.eq(ClaimStakeholder::getRelatedId, claimNumber)
                .eq(ClaimStakeholder::getPersonType, PersonTypeEnum.COLLECT.getCode());
        List<ClaimStakeholder> collectPeoples = stakeholderRepository.findByCriteria(collectCriteria);
        if (CollectionUtils.isEmpty(collectPeoples)) {
            throw new RuntimeException("领款人不存在,claimNumber:" + claimNumber);
        }
        ClaimStakeholder collectPeople = collectPeoples.get(0);
        log.info("申请书collectPeople:{}", JSON.toJSONString(collectPeople));

        Criteria<ClaimStakeholder> mainInsureCriteria = new Criteria<>();
        mainInsureCriteria.eq(ClaimStakeholder::getRelatedId, claimNumber)
                .eq(ClaimStakeholder::getPersonType, PersonTypeEnum.MAIN_INSURE.getCode());
        List<ClaimStakeholder> mainInsurePeoples = stakeholderRepository.findByCriteria(mainInsureCriteria);
        if (CollectionUtils.isEmpty(mainInsurePeoples)) {
            throw new RuntimeException("主被保人不存在,claimNumber:" + claimNumber);
        }
        ClaimStakeholder mainInsurePeople = mainInsurePeoples.get(0);
        log.info("申请书mainInsurePeople:{}", JSON.toJSONString(mainInsurePeople));

        Criteria<ClaimInvoice> invoiceCriteria = Criteria.create();
        invoiceCriteria.eq(ClaimInvoice::getRelatedId, claimNumber);
        List<ClaimInvoice> invoiceList = invoiceRepository.findByCriteria(invoiceCriteria);
        log.info("申请书invoiceList:{}", JSON.toJSONString(invoiceList));

        FieldDataForYCLpsqs data = new FieldDataForYCLpsqs();
        data.setOutInsureName(outInsurePeople.getName());
        String identityType = outInsurePeople.getIdentityType();
        String identityNo = outInsurePeople.getIdentityNo();
        /**
         * 永诚身份证是0，而通用的是10，这里取0
         */
        if ("0".equals(identityType) && StringUtils.hasText(identityNo) && (identityNo.length() == 18 || identityNo.length() == 16)) {
            setGenderAndAge(data, identityNo);
        }
        if (!StringUtils.hasText(data.getOutInsureSex())) {
            data.setOutInsureSex(GenderEnum.getDescByCode(outInsurePeople.getGender()));
        }
        data.setOutInsureIdentityNo(identityNo);
        data.setInsureCompanyName(claim.getInsureName());

        Date effdate = policy.getEffdate();
        Date expdate = policy.getExpdate();
        if (effdate != null && expdate != null) {
            List<ClaimInvoice> scopeInvoices = invoiceList.stream()
                    .filter(i -> i.getVisitDate() != null && i.getVisitDate().compareTo(effdate) >= 0 && i.getVisitDate().compareTo(expdate) <= 0)
                    .sorted(Comparator.comparing(ClaimInvoice::getVisitDate))
                    .toList();
            if (!CollectionUtils.isEmpty(scopeInvoices)) {
                ClaimInvoice invoice = scopeInvoices.get(0);

                String hospitalPeriod = invoice.getHospitalPeriod();
                if (StringUtils.hasText(hospitalPeriod)) {
                    try {
                        String[] periodArray = hospitalPeriod.split(",");
                        if (periodArray.length == 2) {
                            String first = periodArray[0];
                            if (first.length() == 19) {
                                //2025-12-31 00:00:00
                                data.setInvoiceBeginDate(first.substring(0, 10));
                            } else {
                                //2025-12-31
                                data.setInvoiceBeginDate(first);
                            }
                        }
                    } catch (Exception e) {
                        log.error("解析最早发票的账单日期起期发生异常,发票信息:{}", JSON.toJSONString(invoice), e);
                    }
                }

                Date visitDate = invoice.getVisitDate();
                if (visitDate != null) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        String visitDateStr = sdf.format(visitDate);
                        data.setAccidentProcess(outInsurePeople.getName() + "在" + visitDateStr + "去医保定点机构看" + invoice.getDiagnosisCn());
                    } catch (Exception e) {
                        log.error("解析事故经过发生异常,发票信息:{}", JSON.toJSONString(invoice), e);
                    }
                }
            }
        }
        data.setInvoiceDisease("疾病");
        data.setOutLocale(claim.getOutInsureAddress());
        data.setApplyName(mainInsurePeople.getName());
        data.setCollectTel(collectPeople.getPhone());
        /**
         * 这里需要的是开户行分行，所以不取总行
         */
        data.setBankName(collectPeople.getBranchCodeCn());
        data.setCollectName(collectPeople.getName());
        data.setBankAmount(collectPeople.getAccountNo());
        Date auditingPassTime = claim.getAuditingPassTime();
        if (auditingPassTime != null) {
            SimpleDateFormat sdfYear = new SimpleDateFormat("yyyy");
            SimpleDateFormat sdfMonth = new SimpleDateFormat("MM");
            SimpleDateFormat sdfDay = new SimpleDateFormat("dd");
            String year = sdfYear.format(auditingPassTime);
            data.setYear(year);
            data.setCompanyYear(year);
            String month = sdfMonth.format(auditingPassTime);
            data.setMouth(month);
            data.setCompanyMouth(month);
            String day = sdfDay.format(auditingPassTime);
            data.setDay(day);
            data.setCompanyDay(day);
        }
        return BeanUtil.beanToMap(data, false, true);
    }

    private static void setGenderAndAge(FieldDataForYCLpsqs data, String identityNo) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            Date birthDate = null;
            int genderCode = -1;
            if (identityNo.length() == 18) {
                // 处理18位身份证
                String birthStr = identityNo.substring(6, 14);
                birthDate = sdf.parse(birthStr);
                genderCode = Character.getNumericValue(identityNo.charAt(16));
            } else if (identityNo.length() == 16) {
                String birthStr = identityNo.substring(6, 12);
                int year = Integer.parseInt(birthStr.substring(0, 2));
                int currentYear = Calendar.getInstance().get(Calendar.YEAR);
                int century = currentYear / 100;
                year = (year > century) ? 1900 + year : 2000 + year;
                String fullBirthStr = String.format("%04d%02d%02d", year, Integer.parseInt(birthStr.substring(2, 4)), Integer.parseInt(birthStr.substring(4, 6)));
                birthDate = sdf.parse(fullBirthStr);
                genderCode = Character.getNumericValue(identityNo.charAt(15));
            }

            // 计算年龄
            if (birthDate != null) {
                Calendar now = Calendar.getInstance();
                Calendar birthCal = Calendar.getInstance();
                birthCal.setTime(birthDate);
                int age = now.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR);
                if (now.get(Calendar.MONTH) < birthCal.get(Calendar.MONTH) || (now.get(Calendar.MONTH) == birthCal.get(Calendar.MONTH) &&
                        now.get(Calendar.DAY_OF_MONTH) < birthCal.get(Calendar.DAY_OF_MONTH))) {
                    age--;
                }
                data.setOutInsureAge(String.valueOf(age));
            }

            // 计算性别
            if (genderCode != -1) {
                String gender = (genderCode % 2 == 0) ? "女" : "男";
                data.setOutInsureSex(gender);
            }
        } catch (Exception e) {
            log.error("根据证件号解析年龄和性别失败,证件号:{}", identityNo, e);
        }
    }

    // 在方法开始和结束时记录内存使用
    private void logMemoryUsage(String stage, Long claimNumber) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        log.info("内存使用[{}]-赔案号{}: 已用={}MB, 最大={}MB, 使用率={}%",
                stage, claimNumber,
                usedMemory / 1024 / 1024,
                maxMemory / 1024 / 1024,
                (usedMemory * 100 / maxMemory));
    }
}
