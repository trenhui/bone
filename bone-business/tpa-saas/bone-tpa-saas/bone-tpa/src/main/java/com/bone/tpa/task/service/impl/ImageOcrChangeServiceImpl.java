package com.bone.tpa.task.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.exception.ServiceException;
import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.enums.*;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimInvoiceService;
import com.bone.tpa.claim.sync.Constant.CostItemFieldConstants;
import com.bone.tpa.claim.sync.Constant.InvoiceFieldsConstants;
import com.bone.tpa.claim.sync.SyncBaseTool;
import com.bone.tpa.core.hook.BizHookFactory;
import com.bone.tpa.core.util.CommonUtil;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.core.util.ImageUtil;
import com.bone.tpa.core.util.KTFeignUtil;
import com.bone.tpa.core.util.kuaitong.KTResponseHandle;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.hook.ClaimHookUtil;
import com.bone.tpa.intelligent.adjustment.differ.InsureanceEventFactory;
import com.bone.tpa.intelligent.adjustment.differ.InsurenceObjectEvent;
import com.bone.tpa.intelligent.adjustment.enums.AccidentReasonEnum;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.identityRule.invoice.hospitalName.InvoiceHosptialCalHook;
import com.bone.tpa.sdk.util.SpringContextUtils;
import com.bone.tpa.task.service.ImageOcrChangeService;
import com.bone.tpa.task.service.vo.ImageOcrChangeResult;
import com.bone.tpa.task.service.vo.OcrMedicalInvoiceModel;
import com.bone.tpa.task.service.vo.OcrVatInvoiceModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ImageOcrChangeServiceImpl extends SyncBaseTool implements ImageOcrChangeService {

    @Value("${oss.domain.externalDomainName:bucket-tpa.oss-cn-qingdao.aliyuncs.com}")
    private String externalDomainName;

    @Value("${oss.domain.intranetDomainName:bucket-tpa.oss-cn-qingdao-internal.aliyuncs.com}")
    private String intranetDomainName;

    @Autowired
    private ClaimHookUtil claimHookUtil;


    @Autowired
    private BizHookFactory hookFactory;

    @Autowired
    private KTFeignUtil ktFeignUtil;

    @Autowired
    private ClaimInvoiceService claimInvoiceService;

    //特殊判断的日期格式
    SimpleDateFormat spDateFormat = new SimpleDateFormat("yyyy-MM-dd");


    public String callKT(String imagePath) {
        if (StringUtils.isBlank(imagePath)) {
            throw new ServiceException(500, "发票地址不能为空");
        }

        //为节省流量费用，把从外网下载阿里云oss图片修改成从内网下载
//        if (imagePath.contains(externalDomainName)) {
//            imagePath = imagePath.replaceFirst(externalDomainName, intranetDomainName);
//        }

        long fileLength = 0;
        try {
            fileLength = ImageUtil.getFileLength(imagePath);
        } catch (Exception e) {
            log.info("获取文件大小发生异常", e);
            throw new ServiceException(500, "获取文件大小发生异常");
        }

        String token = ktFeignUtil.getToken();
        File temFile = null;
        String response = null;
        if (fileLength / (1024.0 * 1024) >= 2) {
            temFile = ImageUtil.compressImage(imagePath);
            try {
                log.info("调用快瞳接口,传文件,imagePath:{}", imagePath);
                response = ktFeignUtil.invoiceCheckAllByFile(temFile, token);
            } finally {
                temFile.delete();
            }
        } else {
            log.info("调用快瞳接口,传图片路径,imagePath:{}", imagePath);
            response = ktFeignUtil.invoiceCheckAllByUrl(imagePath, token);
        }

        boolean successCall = KTResponseHandle.successCall(response);
        if (successCall) {
            return response;
        } else {
            throw new RuntimeException("快瞳接口处理图片失败,imagePath:" + imagePath + ",响应:" + response);
        }
    }

    /**
     * 通过ocr识别，把图片变成发票和消费项目的对象（不落库）
     * 如果有异常，则直接抛出
     */
    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.NOT_SUPPORTED)
    @Override
    public ImageOcrChangeResult changeImageOcr(Long claimNumber, ClaimImage image) {
        Claim claim = claimRepository.findById(claimNumber);
        if (claim == null) {
            throw new RuntimeException("找不到目标赔案,赔案号:" + claimNumber);
        }

        String dataStr = null;
        CommonLog existData = PkListUtil.first(commonLogService.getByObjectId(image.getId().toString(), CommonLogType.OCR_API_DATA));
        if (existData == null) {
            dataStr = callKT(image.getImagePath());
            commonLogService.addLogASyncNoReplace(image.getId().toString(), CommonLogType.OCR_API_DATA, dataStr);
        } else {
            dataStr = existData.getRemark();
        }
        if (StringUtils.isBlank(dataStr)) {
            throw new RuntimeException("请求接口返回空");
        }
        commonLogService.addClaimLogAsync(claimNumber, CommonLogType.CLAIM_COMMON, "orc识别，请求iamgeUuidId:{},请求接口返回数据:{}", image.getImageDetailId(), dataStr);

        JSONObject jsonObj = JSONObject.parseObject(dataStr);
        JSONObject data = jsonObj.getJSONObject("data");
        if (data == null || data.size() == 0) {
            throw new RuntimeException("快瞳返回结果的data参数为空,结果:" + jsonObj);
        }

        String productCode = data.getString("productCode");
        if ("003082".equals(productCode)) {
            //增值税发票
            if (checkTrue(jsonObj)) {
                return zengzhishui(claim, data);
            } else {
                return fakeZengzhishui(claim, data);
            }
        } else if ("003081".equals(productCode)) {
            //医疗发票
            return handleMedicalInvoice(claim, data);
        } else {
            throw new RuntimeException("未知发票类型");
        }
    }

    private ImageOcrChangeResult handleMedicalInvoice(Claim claim, JSONObject data) {
        ImageOcrChangeResult result = new ImageOcrChangeResult();

        ClaimInvoice invoice = new ClaimInvoice();
        initBigdecial(invoice);
        invoice.setInvoiceUuid(UUID.randomUUID().toString());
        invoice.setBizIdentityCode(claim.getBizIdentityCode());
        invoice.setInputTypeCn(InvoiceInputType.自动化.getDesc());
        invoice.setTenantId(claim.getTenantId());
        invoice.setRelatedId(claim.getId());

        //========================================
        invoice.setPaperType(InvoicePaperTypeEnum.ELECTRONIC.getCode());//医疗发票默认电子票
        invoice.setOutInsureReason(AccidentReasonEnum.Jb.getCode().toString());//出险原因code
        invoice.setOutInsureReasonCn(AccidentReasonEnum.Jb.getName());//出险原因
        invoice.setOcrFlag(YesOrNoEnum.YES.getCode());
        invoice.setPoolingThreshold(BigDecimal.ZERO);
        //invoice.setClaimInvoiceId() //发票Id
        invoice.setHasYb(YesOrNoEnum.YES.getCode());//是否医保
        //第三方类型
        invoice.setInvalidAmount(BigDecimal.ZERO);//不合理金额
        //发票类型(中文label)
        //发票类型
        //出险类型
        //诊断名称 诊断code
        String diseaseName = "不适和疲劳";
        invoice.setDiagnosisCn(nullIfEmpty(diseaseName));
        OptionSetDTO diseaseNameOption = matchCollectionCode(diseaseName, InvoiceFieldsConstants.diagnosis,
                claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票, true, result.getInvoiceHint());
        invoice.setDiagnosis(diseaseNameOption == null?"":diseaseNameOption.getCode());
        ExtraStoreUtil.fillExtraStoreWithOrigin(invoice,InvoiceFieldsConstants.diagnosis, diseaseNameOption);

        //医院code 医院名称
        //========================================

        OcrMedicalInvoiceModel details = JSON.parseObject(data.toJSONString(), OcrMedicalInvoiceModel.class);
        setInvoiceData(invoice, details, claim, result);
        result.setInvoice(invoice);
        getInvoiceProjectItems(claim, invoice, details, result);
        //计算合理金额
        claimInvoiceService.updateValidMoney(invoice);
        return result;
    }

    private void getInvoiceProjectItems(Claim claim, ClaimInvoice invoice,
                                        OcrMedicalInvoiceModel details, ImageOcrChangeResult result) {
        Integer inputCheckType = claim.getCfgBizInputType();
        CfgBizInputTypeEnum inputType =   CfgBizInputTypeEnum.getByCode(inputCheckType);
        Boolean flag = inputCheckType== null || inputType == CfgBizInputTypeEnum.INVOICE_AND_PROJECT;

        List<OcrMedicalInvoiceModel.feedetails> feeDetails = details.getFeedetails();
        for (OcrMedicalInvoiceModel.feedetails feeDetail : feeDetails) {
            InvoiceProjectItem projectItem = new InvoiceProjectItem();
            String extraStoreStr = projectItem.getExtraStore();
            result.getItemList().add(projectItem);
            Map<String, String> itemHint = new HashMap<>();
            result.getItemHintList().add(itemHint);

         /*   projectItem.setBizIdentityCode(claim.getBizIdentityCode());
            projectItem.setRelatedId(invoice.getId());
            projectItem.setRelatedInvoiceNo(invoice.getInvoiceNo());*/
            projectItem.setItemName(feeDetail.getItemName());
            projectItem.setItemCode(feeDetail.getItemCoding());
            projectItem.setCount((int) Double.parseDouble(feeDetail.getNumber()));
            projectItem.setItemTotalAmount(new BigDecimal(feeDetail.getTotalAmount()));
            String unit = feeDetail.getUnit();
            projectItem.setDosageFormCn(unit);
            OptionSetDTO dosageFormOptionSetDTO = matchCollectionCode(unit, CostItemFieldConstants.dosageForm,
                    claim.getBizIdentityCode(), claim.getId(), FieldModelDefine.发票费用明细, flag, itemHint);
            projectItem.setDosageForm(getOptionSetDTOCode(dosageFormOptionSetDTO));

             ExtraStoreUtil.fillExtraStoreWithOrigin(projectItem ,CostItemFieldConstants.dosageForm, dosageFormOptionSetDTO);

        }
    }

    private void setInvoiceData(ClaimInvoice invoice, OcrMedicalInvoiceModel details, Claim claim, ImageOcrChangeResult result) {
        invoice.setInvoiceNo(details.getBillNumber());//发票号
        invoice.setClaimInvoiceId(details.getBillCode());
        String payer = details.getPayer();
        if (StringUtils.isNotBlank(payer)) {
            payer = payer.split("\\(")[0];
            invoice.setInvoiceName(payer);
        }

        String invoiceDate = details.getInvoiceDate();
        if (StringUtils.isNotBlank(invoiceDate)) {
            try {
                Date date = CommonUtil.parseStrToDate(invoiceDate);
                invoice.setInvoiceDate(date);
            } catch (Exception e) {
                Date date = new Date(2999, Calendar.DECEMBER, 31);
                invoice.setInvoiceDate(date);
                log.info("转换开票日期发生异常, invoiceDate" + invoiceDate, e);
            }
        }

        //入院日期/出院日期/就诊天数
        String hospitalizationDateStr = details.getHospitalizationDate();
        invoice.setHospitalPeriod("2999-12-31,2999-12-31");
        invoice.setHospitalDays(1);
        try {
            if (StringUtils.isNotBlank(hospitalizationDateStr)) {
                //该字符串存在以下情况：
                //日期-日期
                //日期
                //  yyyyMMdd
                //  yyyy-MM-dd
                //  yyyy/MM/dd
                //  yyyy.MM.dd
                //需要先判断是单日还是多日，然后再做决断
                Date hospitalDate = CommonUtil.parseStrToDate(hospitalizationDateStr);
                if (hospitalDate == null) {
                    //证明不是单日
                    String[] split = hospitalizationDateStr.split("-", 2);

                    if (split.length == 2) {
                        String startDateStr = split[0];
                        String endDateStr = split[1];
//                SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy/MM/dd");
                        Date startDate = CommonUtil.parseStrToDate(startDateStr);
                        Date endDate = CommonUtil.parseStrToDate(endDateStr);

                        long diffMillis = endDate.getTime() - startDate.getTime();
                        long days = TimeUnit.DAYS.convert(diffMillis, TimeUnit.MILLISECONDS);

                        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");
                        invoice.setHospitalPeriod(sdf2.format(startDate) + "," + sdf2.format(endDate));
                        invoice.setHospitalDays((int) (days + 1));
                    } else {
                        String startDateStr = split[0];
                        Date startDate = CommonUtil.parseStrToDate(startDateStr);
                        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");
                        invoice.setHospitalPeriod(sdf2.format(startDate) + "," + sdf2.format(startDate));
                        invoice.setHospitalDays(1);
                    }
                } else {
                    SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");
                    invoice.setHospitalPeriod(sdf2.format(hospitalDate) + "," + sdf2.format(hospitalDate));
                    invoice.setHospitalDays(1);
                }
            } else if (StringUtils.isNotBlank(invoiceDate)) {
//                SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = CommonUtil.parseStrToDate(invoiceDate);
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");
                invoice.setHospitalPeriod(sdf2.format(date) + "," + sdf2.format(date));
                invoice.setHospitalDays(1);
            }
        } catch (Exception e) {
            log.info("转换住院期间/就诊天数发生异常,hospitalizationDate:" + hospitalizationDateStr + ", invoiceDate" + invoiceDate, e);
        }

        //出险类型：若入院日期和出院日期为空则为13：门急诊，否则为14：住院
//        if (StringUtils.isBlank(details.getHospitalizationDate()) || StringUtils.isBlank(details.getDischargeDate())) {
//            //产品说不处理
//        }

        //医保类型
        if ("自费".equals(details.getMedicareType())) {
            invoice.setYbType(InvoiceYbType.FOURPay.getCode());
            invoice.setYbTypeCn(InvoiceYbType.FOURPay.getDesc());
        } else {
            invoice.setYbType(InvoiceYbType.YBPay.getCode());
            invoice.setYbTypeCn(InvoiceYbType.YBPay.getDesc());
        }

        //医保个人账户支出
//        if (details.getSelfAcountAmount() != null) {
//            //产品说不处理
//        }

        //发票总金额
        if (details.getAmount() != null) {
            invoice.setTotalAmount(details.getAmount());
        }

        //医保统筹金额
        if (details.getMedicarePay() != null) {
            invoice.setBasicPoolingAmount(details.getMedicarePay());
        }

        if(invoice.getBasicPoolingAmount()!= null){
            invoice.setTotalMedicalFundPayment(invoice.getBasicPoolingAmount());
        }

        //自费金额
        if (details.getPersonalExpense() != null) {
            invoice.setClassCSelfPayAmount(details.getPersonalExpense());
        }

        //部分自费
        if (details.getClassificationPays() != null) {
            invoice.setSelfPayPart2Amount(details.getClassificationPays());
        }

        //第三方支付
        if (details.getOtherPayment() != null) {
            invoice.setThirdPartyPaidAmount(details.getOtherPayment());
        }

        //验真结果
        if (details.getFlushedRed()) {
            invoice.setVerifyValid(EInvoiceValidResult.真票.getCode());
        } else {
            invoice.setVerifyValid(EInvoiceValidResult.红冲.getCode());
        }

        //核验次数
        if (details.getCheckCount() != null) {
            invoice.setVerifyValidTimes(details.getCheckCount());
        }

        //医院名称
        if (StringUtils.isNotBlank(details.getPayeeName())) {
            String hospitalName = details.getPayeeName();
            invoice.setHospitalName(hospitalName);
            OptionSetDTO hospitalCodeDto = matchCollectionCodeWithAlert(hospitalName,
                    InvoiceFieldsConstants.hospitalCode,
                    claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票,
                    true,false, result.getInvoiceHint());
            invoice.setHospitalCode(getOptionSetDTOCode(hospitalCodeDto));
             ExtraStoreUtil.fillExtraStoreWithOrigin(
                    invoice,InvoiceFieldsConstants.hospitalCode,
                    hospitalCodeDto);

            if(StringUtils.isBlank(invoice.getHospitalCode())){

                    //有的保司有自己的默认医院,比如"其他"
                    String defaultHospitalName =  SpringContextUtils.getBean(InsureanceEventFactory.class).
                            getDefaultHospitalName(claim.getId());
                    if(StringUtils.isNotBlank(defaultHospitalName)){
                        //注意，这里是不记录hint 的，也不告警
                        OptionSetDTO hosptialCodeDefaultDto =   matchCollectionCodeWithAlert(defaultHospitalName,
                                InvoiceFieldsConstants.hospitalCode,
                                claim.getBizIdentityCode(),
                                claim.getId(),
                                FieldModelDefine.发票,false,false,result.getInvoiceHint());
                       ExtraStoreUtil.fillExtraStoreWithOrigin(
                                invoice,InvoiceFieldsConstants.hospitalCode,
                                hosptialCodeDefaultDto);

                        invoice.setHospitalCode(getOptionSetDTOCode(hosptialCodeDefaultDto));
                        invoice.setHospitalName(nullIfEmpty(defaultHospitalName));
                    }
            }

           // invoice.setHospitalName(details.getPayeeName());
        }



        //就诊日期
        if (StringUtils.isNotBlank(details.getSeeDoctorDate())) {
            try {
                Date date = CommonUtil.parseStrToDate(details.getSeeDoctorDate());
                invoice.setVisitDate(date);
            } catch (Exception e) {
                log.info("转换医疗发票就诊日期发生异常,seeDoctorDate:" + details.getSeeDoctorDate(), e);
            }
        }

        //医疗机构类型
        if (StringUtils.isNotBlank(details.getInstitutionsType())) {
            invoice.setMedicalInstitutionTypeCn(details.getInstitutionsType());//医疗机构类型中文
            OptionSetDTO medicalTypeCodeOption = matchCollectionCode(details.getInstitutionsType(),
                    InvoiceFieldsConstants.medicalInstitutionType,
                    claim.getBizIdentityCode(), claim.getId(), FieldModelDefine.发票, true, result.getInvoiceHint());
            invoice.setMedicalInstitutionType(getOptionSetDTOCode(medicalTypeCodeOption));//医疗机构类型code
            invoice.setMedicalInstitutionTypeCn(details.getInstitutionsType());//医疗机构类型中文
            ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.medicalInstitutionType, medicalTypeCodeOption);
        }

       /* if (StringUtils.isNotBlank(details.getRemark())) {
            invoice.setRemark(details.getRemark());
        }*/
    }

    /**
     * 真票还是假票
     *
     * @param jsonObj
     * @return
     */
    public boolean checkTrue(JSONObject jsonObj) {
        String status = jsonObj.getString("status");
        String code = jsonObj.getString("code");
        boolean fakeInvoice = "400".equals(status) && ("10001".equals(code) || "10100".equals(code));
        return !fakeInvoice;
    }

    /**
     * 增值税假发票
     */
    private ImageOcrChangeResult fakeZengzhishui(Claim claim, JSONObject data) {
        ImageOcrChangeResult rs = new ImageOcrChangeResult();
        ClaimInvoice invoice = new ClaimInvoice();
        initBigdecial(invoice);
        rs.setInvoice(invoice);

        invoice.setInputTypeCn(InvoiceInputType.自动化.getDesc());
        String type = data.getString("type");
        InvoicePaperTypeEnum invoiceTypeEnum = KTResponseHandle.getInvoiceType(type);
        if (invoiceTypeEnum != null) {
            invoice.setPaperType(invoiceTypeEnum.getCode());
        }
        invoice.setVerifyValid(EInvoiceValidResult.假票.getCode());
        invoice.setInvoiceUuid(UUID.randomUUID().toString());
        invoice.setBizIdentityCode(claim.getBizIdentityCode());
        invoice.setTenantId(claim.getTenantId());
        invoice.setRelatedId(claim.getId());
        invoice.setOcrFlag(YesOrNoEnum.YES.getCode());
        invoice.setInvoiceNo("999999");
        invoice.setVerifyValidTimes(1);
        invoice.setOutInsureReason(AccidentReasonEnum.Jb.getName());
        invoice.setHasYb(YesOrNoEnum.NO.getCode());
        invoice.setYbTypeCn(InvoiceYbType.FOURPay.getDesc());
        OptionSetDTO ybTypeCodeOption = matchCollectionCode(InvoiceYbType.FOURPay.getDesc(), InvoiceFieldsConstants.ybType,
                claim.getBizIdentityCode(), claim.getId(), FieldModelDefine.发票, true, rs.getInvoiceHint());
        invoice.setYbType(getOptionSetDTOCode(ybTypeCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.ybType, ybTypeCodeOption);


        invoice.setMedicalInstitutionTypeCn(InvoiceMedicalTypeEnum.Ptsj.getDesc());
        OptionSetDTO medicalTypeCodeOption = matchCollectionCode(InvoiceMedicalTypeEnum.Ptsj.getDesc(),
                InvoiceFieldsConstants.medicalInstitutionType,
                claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票, true, rs.getInvoiceHint());
        invoice.setMedicalInstitutionType(getOptionSetDTOCode(medicalTypeCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.medicalInstitutionType, medicalTypeCodeOption);


        String treatmentTypeCn = "药房";
        invoice.setVisitTypeCn(treatmentTypeCn);
        OptionSetDTO visitTypeCodeOption = matchCollectionCode(treatmentTypeCn, InvoiceFieldsConstants.visitType,
                claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票, true, rs.getInvoiceHint());
        invoice.setVisitType(getOptionSetDTOCode(visitTypeCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.visitType, visitTypeCodeOption);

        invoice.setHospitalPeriod(",");
        invoice.setHospitalDays(0);
        invoice.setInvoiceDate(null);
        invoice.setTotalAmount(BigDecimal.ZERO);
        String diseaseName = "不适和疲劳";
        invoice.setDiagnosisCn(nullIfEmpty(diseaseName));
        OptionSetDTO diseaseNameCodeOption = matchCollectionCode(diseaseName, InvoiceFieldsConstants.diagnosis,
                claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票, true, rs.getInvoiceHint());
        invoice.setDiagnosis(getOptionSetDTOCode(diseaseNameCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.diagnosis, diseaseNameCodeOption);
        InvoiceHosptialCalHook hosptialCalHook =  hookFactory.getInvoiceHosptialCalHook(claim.getBizIdentityCode());
        String hospitalName = "本代码表中不存在的其他医院";
        if( hosptialCalHook != null){
            hospitalName =   hosptialCalHook.doEvent(claim.getBizIdentityCode());
        }
        invoice.setHospitalName(hospitalName);
        OptionSetDTO hospitalCodeOption = matchCollectionCode(hospitalName, InvoiceFieldsConstants.hospitalCode,
                claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票, true, rs.getInvoiceHint());
        invoice.setHospitalCode(getOptionSetDTOCode(hospitalCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.hospitalCode, hospitalCodeOption);
        claimInvoiceService.updateValidMoney(invoice);
        return rs;
    }

    private String getHospitalNamefromData(Claim claim,OcrVatInvoiceModel details ){

        String seller = details.getSeller();
        if(StringUtils.isNotBlank(seller)){
            return seller;
        }
        InvoiceHosptialCalHook hosptialCalHook =  hookFactory.getInvoiceHosptialCalHook(claim.getBizIdentityCode());
        String hospitalName = "本代码表中不存在的其他医院";
        if( hosptialCalHook != null){
            hospitalName =   hosptialCalHook.doEvent(claim.getBizIdentityCode());
        }
        return hospitalName;
    }

    /**
     * 增值税发票，进来就是真票
     */
    private ImageOcrChangeResult zengzhishui(Claim claim, JSONObject data) {
        ImageOcrChangeResult rs = new ImageOcrChangeResult();
        ClaimInvoice invoice = new ClaimInvoice();
        initBigdecial(invoice);//把所有的bigdecial 设置成0
        rs.setInvoice(invoice);

        invoice.setInvoiceUuid(UUID.randomUUID().toString());
        invoice.setBizIdentityCode(claim.getBizIdentityCode());
        invoice.setInputTypeCn(InvoiceInputType.自动化.getDesc());
        invoice.setTenantId(claim.getTenantId());
        invoice.setRelatedId(claim.getId());
        invoice.setOcrFlag(YesOrNoEnum.YES.getCode());

        String type = data.getString("type");
        InvoicePaperTypeEnum invoiceTypeEnum = KTResponseHandle.getInvoiceType(type);
        if (invoiceTypeEnum != null) {
            invoice.setPaperType(invoiceTypeEnum.getCode());
        }

        invoice.setOutInsureReason(AccidentReasonEnum.Jb.getCode().toString());
        invoice.setOutInsureReasonCn(AccidentReasonEnum.Jb.getName());
        invoice.setHasYb(YesOrNoEnum.NO.getCode());
        invoice.setYbTypeCn(InvoiceYbType.FOURPay.getDesc());
        invoice.setYbType(InvoiceYbType.FOURPay.getCode());

        OcrVatInvoiceModel details = JSON.parseObject(data.getJSONObject("details").toJSONString(), OcrVatInvoiceModel.class);
        String buyer = details.getBuyer();
        if (StringUtils.isNotBlank(buyer)) {
            buyer = buyer.split("\\(")[0];
        }
        invoice.setInvoiceName(buyer);
        invoice.setInvoiceNo(details.getNumberConfirm());
        if (StringUtils.isNotBlank(details.getTotal())) {
            invoice.setTotalAmount(new BigDecimal(details.getTotal()));
        }
        String treatmentTypeCn = "药房";
        invoice.setVisitTypeCn(treatmentTypeCn);
        OptionSetDTO visitTypeCodeOption = matchCollectionCode(treatmentTypeCn, InvoiceFieldsConstants.visitType,
                claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票, true, rs.getInvoiceHint());

        invoice.setVisitType(getOptionSetDTOCode(visitTypeCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.visitType, visitTypeCodeOption);

        String oldInvoiceDateStr = details.getDate();
        if (StringUtils.isNotBlank(oldInvoiceDateStr)) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日");
                Date dt = CommonUtil.parseStrToDate(oldInvoiceDateStr);
                invoice.setInvoiceDate(dt);
                sdf = new SimpleDateFormat("yyyy-MM-dd");
                String invoiceDateStr = sdf.format(dt);
                invoice.setHospitalPeriod(invoiceDateStr + "," + invoiceDateStr);
                invoice.setHospitalDays(1);
            } catch (Exception e) {
                invoice.setHospitalPeriod(",");
                invoice.setHospitalDays(0);
            }
        }
        String deaseName = "不适和疲劳";
        OptionSetDTO diseaseNameCodeOption = matchCollectionCode(deaseName, InvoiceFieldsConstants.diagnosis,
                claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票, true, rs.getInvoiceHint());
        invoice.setDiagnosis(getOptionSetDTOCode(diseaseNameCodeOption));
        invoice.setDiagnosisCn(nullIfEmpty(deaseName));
        ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.diagnosis, diseaseNameCodeOption);


        String hospitalName = getHospitalNamefromData( claim, details );
        invoice.setHospitalName(hospitalName);
        OptionSetDTO hospitalCodeoption = matchCollectionCodeWithAlert(hospitalName,
                InvoiceFieldsConstants.hospitalCode,
                claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票,
                true,false, rs.getInvoiceHint());
        invoice.setHospitalCode(getOptionSetDTOCode(hospitalCodeoption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.hospitalCode, hospitalCodeoption);

        if(StringUtils.isBlank(invoice.getHospitalCode())){
           /* InsurenceObjectEvent customerHandler =  SpringContextUtils.getBean(InsureanceEventFactory.class).
                    getEventHandler(claim.getId());*/
                //有的保司有自己的默认医院,比如"其他"
                String defaultHospitalName =  SpringContextUtils.getBean(InsureanceEventFactory.class).
                        getDefaultHospitalName( claim.getId());
                if(StringUtils.isNotBlank(defaultHospitalName)){
                    //注意，这里是不记录hint 的，也不告警
                    OptionSetDTO hosptialCodeDefaultOption =   matchCollectionCodeWithAlert(defaultHospitalName,
                            InvoiceFieldsConstants.hospitalCode,
                            claim.getBizIdentityCode(),
                            claim.getId(),
                            FieldModelDefine.发票,false,false,rs.getInvoiceHint());
                    invoice.setHospitalCode(getOptionSetDTOCode(hosptialCodeDefaultOption));
                    invoice.setHospitalName(nullIfEmpty(defaultHospitalName));
                    ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, InvoiceFieldsConstants.hospitalCode, hosptialCodeDefaultOption);
                }
        }



        String invoiceTypeNo = details.getInvoiceTypeNo();
        if ("0".equals(invoiceTypeNo)) {
            invoice.setVerifyValid(EInvoiceValidResult.真票.getCode());
        } else if ("1".equals(invoiceTypeNo)) {
            invoice.setVerifyValid(EInvoiceValidResult.无法验真.getCode());
        } else if ("2".equals(invoiceTypeNo) || "3".equals(invoiceTypeNo) || "7".equals(invoiceTypeNo) || "8".equals(invoiceTypeNo)) {
            invoice.setVerifyValid(EInvoiceValidResult.红冲.getCode());
        }
        invoice.setClaimInvoiceId(details.getCodeConfirm());
        invoice.setVerificationCode(details.getCheckCode());//校验码
        String checkCount = data.getString("checkCount");//核验次数
        if (StringUtils.isNotBlank(checkCount)) {
            invoice.setVerifyValidTimes(Integer.valueOf(checkCount));
        }

        Integer inputCheckType = claim.getCfgBizInputType();
        CfgBizInputTypeEnum inputType =   CfgBizInputTypeEnum.getByCode(inputCheckType);
        Boolean flag = inputCheckType== null || inputType == CfgBizInputTypeEnum.INVOICE_AND_PROJECT;

        List<OcrVatInvoiceModel.Items> items = details.getItems();
        for (int i = 0; i < items.size(); i++) {
            OcrVatInvoiceModel.Items item = items.get(i);
            String wholeName = item.getName();
            if (StringUtils.isBlank(wholeName)) {
                continue;
            }
            InvoiceProjectItem costItem = new InvoiceProjectItem();
            initBigdecial(costItem);
            rs.getItemList().add(costItem);
            Map<String, String> hint = new HashMap<>();
            rs.getItemHintList().add(hint);

            String[] arr = wholeName.split("\\*", 3);
            String projectName = arr[1];
            String drugName = arr[2];
            costItem.setRelatedId(invoice.getId());
            costItem.setRelatedInvoiceNo(invoice.getInvoiceNo());
            costItem.setRelatedProjectName(projectName);
            OptionSetDTO projectCodeOption = matchCollectionCode(projectName, CostItemFieldConstants.projectCode,
                    claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票费用明细, flag, hint);
            costItem.setRelatedProjectCode(getOptionSetDTOCode(projectCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(costItem, CostItemFieldConstants.projectCode, projectCodeOption);


            costItem.setItemName(drugName);
            //costItem.setMedicalType(MedicalCostTypeEnum.乙类.getCode());
            costItem.setMedicalType("");
            BigDecimal price = null;
            BigDecimal itemTotalAmount = null;
            String oldPriceStr = item.getPrice();
            String taxRateStr = item.getTaxRate();
            if (Objects.equals("免税", taxRateStr) || Objects.equals("***", item.getTax())) {
                price = new BigDecimal(oldPriceStr != null ? oldPriceStr : item.getTotal());
                itemTotalAmount = new BigDecimal(item.getTotal());
            } else {
                itemTotalAmount = new BigDecimal(item.getTotal()).add(new BigDecimal(item.getTax()));
                if (StringUtils.isNotBlank(oldPriceStr) && StringUtils.isNotBlank(taxRateStr)) {
                    BigDecimal oldPrice = new BigDecimal(oldPriceStr);
                    BigDecimal taxRate = new BigDecimal(taxRateStr);
                    BigDecimal taxAmount = oldPrice.multiply(taxRate);// 计算税额
                    price = oldPrice.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
                } else {
                    price = itemTotalAmount;
                }
            }
            costItem.setPrice(price);
            costItem.setItemTotalAmount(itemTotalAmount);
            costItem.setCount(item.getQuantity() != null ? item.getQuantity().intValue() : 1);
            String unit = item.getUnit();
            costItem.setDosageFormCn(unit);
            OptionSetDTO dosageFormCodeOption = matchCollectionCode(unit, CostItemFieldConstants.dosageForm,
                    claim.getBizIdentityCode(), claim.getId(),  FieldModelDefine.发票费用明细, flag, hint);
            costItem.setDosageForm(getOptionSetDTOCode(dosageFormCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(costItem, CostItemFieldConstants.dosageForm, dosageFormCodeOption);
        }

        //汇总金额
        if (flag) {
            claimInvoiceService.updateInvoiceMoney(rs.getInvoice(), rs.getItemList());
        }
        //计算合理金额
        claimInvoiceService.updateValidMoney(rs.getInvoice());
        return rs;
    }


    private BigDecimal getDrugModelUnitPrice(String priceStr, String taxRateStr) {
        if (StringUtils.isBlank(priceStr)) {
            return BigDecimal.ZERO;
        }
        // 将字符串转换为 BigDecimal
        BigDecimal price = new BigDecimal(priceStr);
        BigDecimal taxRate = new BigDecimal(taxRateStr);
        // 计算税额
        BigDecimal taxAmount = price.multiply(taxRate);
        // 计算总价并四舍五入
        return price.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
    }


    private void initBigdecial(Object obj) {
        Method[] allMethod = obj.getClass().getMethods();
        for (Method method : allMethod) {
            String methodName = method.getName();
            if (!methodName.startsWith("set")) {
                continue;
            }
            Class[] paramArr = method.getParameterTypes();
            if (paramArr == null || paramArr.length != 1) {
                continue;
            }
            if (paramArr[0] == BigDecimal.class) {
                try {
                    method.invoke(obj, BigDecimal.ZERO);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }
}
