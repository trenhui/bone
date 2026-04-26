package com.bone.tpa.claim.domain.ext.strategy;

import com.alibaba.fastjson.JSONObject;
import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.api.vo.ClaimPersonInfo;
import com.bone.tpa.api.vo.CollectPersonInfo;
import com.bone.tpa.claim.application.enums.YongChengVisitTypeEnum;
import com.bone.tpa.claim.application.response.ClaimDetailObject;
import com.bone.tpa.claim.application.response.OutInsurePerson;
import com.bone.tpa.claim.domain.service.ClaimStakeholderService;
import com.bone.tpa.claim.sync.Constant.CollectPersonConstants;
import com.bone.tpa.claim.sync.Constant.InvoiceFieldsConstants;
import com.bone.tpa.claim.sync.Context;
import com.bone.tpa.claim.sync.SyncBaseTool;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.sdk.adjustment.enums.DateRangeEnum;
import com.bone.tpa.sdk.util.DateUtil;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.util.OptionSetObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 永诚专用
 *
 * 发票和赔案更新时调用的规则
 */
@Service
@Slf4j
public class YongChengUpdateRule {
    @Autowired
    private  SyncBaseTool syncBaseTool;

    @Autowired
    private ClaimStakeholderService claimStakeholderService;



    public void onUpdateClaim(Claim claimExist ,ClaimDetailObject claimDetailObject) {
        OutInsurePerson outInsure = claimDetailObject.getOutInsurePerson();
        if (outInsure == null || outInsure.getOutInsureIdentityNo() == null || outInsure.getOutInsureIdentityNo().isEmpty()) {
            return;
        }


        List<ClaimStakeholder> collectList = claimStakeholderService.getByClaimId(claimDetailObject.getId(),
                claimDetailObject.getBizIdentityCode(), claimDetailObject.getTenantId(),
                Collections.singletonList(PersonTypeEnum.COLLECT.getCode()));

        if(collectList == null || collectList.isEmpty()) {
            return;
        }

        ClaimStakeholder collect = collectList.get(0);


        OptionSetDTO exCollectionRelationOptionset = null;
        String extRelationkey = "EX_collectrelation";
        if (Objects.equals(outInsure.getOutInsureName(), collect.getName()) &&
                Objects.equals(outInsure.getOutInsureIdentityTypeCn(), collect.getIdentityTypeCn()) &&
                Objects.equals(outInsure.getOutInsureIdentityNo(), collect.getIdentityNo())) {

            exCollectionRelationOptionset = syncBaseTool.matchCollectionCode("被保险人本人", extRelationkey,
                    claimDetailObject.getBizIdentityCode(), claimDetailObject.getId(), FieldModelDefine.赔案, true, new HashMap<>());
        } else if (calculateAge(outInsure.getOutInsureIdentityNo()) < 18) {
            exCollectionRelationOptionset = syncBaseTool.matchCollectionCode("监护人", extRelationkey,
                    claimDetailObject.getBizIdentityCode(), claimDetailObject.getId(), FieldModelDefine.赔案, true, new HashMap<>());
        } else {
            exCollectionRelationOptionset = syncBaseTool.matchCollectionCode("非利益第三方（含单位和个人）", extRelationkey,
                    claimDetailObject.getBizIdentityCode(), claimDetailObject.getId(), FieldModelDefine.赔案, true, new HashMap<>());
        }
        String exCode = exCollectionRelationOptionset == null?"":exCollectionRelationOptionset.getCode();
        claimDetailObject.getCollectInfo().getExtraProperties().put("EX_collectrelation", exCode);

         ExtraStoreUtil.fillExtraStoreWithOrigin(claimExist,extRelationkey,exCollectionRelationOptionset);
    }

    public void filterDataWhileToTpa(ClaimDetailSyncVO syncVO){
        if( syncVO == null){
            return;
        }
        if(syncVO.getCollectPersonInfo()!= null){
            CollectPersonInfo collectPersonInfo =   syncVO.getCollectPersonInfo();
            String bankName =   collectPersonInfo.getBankBranchName();
            collectPersonInfo.setBankBranchName(bankName);
            collectPersonInfo.setBankName(bankName);
        }
    }




    public void onUpdateClaimWhileFromTpa(ClaimDetailSyncVO syncVO,
                                          String bizIdentityCode,
                                          Context context,
                                          Map<String,String> hintMap) {
        dealCollectrelation(syncVO,bizIdentityCode);
        dealCollectBankName(syncVO,bizIdentityCode,context,hintMap);

    }

    private void dealCollectBankName(ClaimDetailSyncVO syncVO, String bizIdentityCode,
                                     Context context,
                                     Map<String,String> hintMap){
        CollectPersonInfo collect = syncVO.getCollectPersonInfo();
        if (collect == null) {
            return;
        }


        String branchNameName =   collect.getBankName();
        log.info("yongchengCollectBankMatch start,branchNameName:{}",branchNameName);
        if(StringUtils.isNotBlank(branchNameName)){
            collect.setBankName(null);
            collect.setBankBranchName(branchNameName);
            try {
                OptionSetDTO brankBranchCodeOption = syncBaseTool.matchCollectionCodeForSync(
                        branchNameName,
                        CollectPersonConstants.personBranchCode,  context, FieldModelDefine.领款人信息,
                        false, hintMap);
                if(brankBranchCodeOption != null){
                    log.info("yongchengCollectBankMatch success,branchNameName:{},getExtraProperty:{}",branchNameName,
                            brankBranchCodeOption.getExtraProperty());
                    String extendStr = brankBranchCodeOption.getExtraProperty();
                    if(StringUtils.isNotBlank(extendStr)){
                        JSONObject extObj =  JSONObject.parseObject(extendStr);
                        String bankName = extObj.getString("所属银行总行");
                        String bankProvince = extObj.getString("所属地区省");
                        String bankCity = extObj.getString("所属地区市");
                        if(StringUtils.isNotBlank(bankName)){
                            collect.setBankName(bankName);
                        }
                        if(StringUtils.isNotBlank(bankProvince)){
                            collect.setBankProvince(bankProvince);
                        }
                        if(StringUtils.isNotBlank(bankCity)){
                            collect.setBankCity(bankCity);
                        }
                        log.info("yongchengCollectBankMatch success,branchNameName:{},bankName:{},bankProvince:{},bankCity:{}",
                              branchNameName,  bankName,bankProvince,bankCity);

                    }
                }
            } catch (Exception e) {
                log.error("yongchengCollectBankMatch error",e);
            }
        }


    }

    private void dealCollectrelation(ClaimDetailSyncVO syncVO, String bizIdentityCode){
        ClaimPersonInfo outInsure = syncVO.getOutPersonInfo();
        if (outInsure == null || outInsure.getIdentityNo() == null || outInsure.getIdentityNo().isEmpty()) {
            return;
        }

        CollectPersonInfo collect = syncVO.getCollectPersonInfo();
        if (collect == null) {
            return;
        }

        String exCode;
        if (Objects.equals(outInsure.getName(), collect.getName()) &&
                Objects.equals(outInsure.getIdentityTypeCn(), collect.getIdentityTypeCn()) &&
                Objects.equals(outInsure.getIdentityNo(), collect.getIdentityNo())) {

            exCode = "被保险人本人";
        } else if (calculateAge(outInsure.getIdentityNo()) < 18) {
            exCode = "监护人";
        } else {
            exCode = "非利益第三方（含单位和个人）";
        }

        if (syncVO.getExtMap() == null) {
            syncVO.setExtMap(new HashMap<>());
        }

        syncVO.getExtMap().put("collectrelation", exCode);
    }

    public void onUpdateInvoice(ClaimInvoice invoice) {
        try {
            //当就诊类型是对应值时，调整出险类型为对应值
            String visitCn = "";
            OptionSetObject visitCnExtraStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.visitType,invoice.getExtraStore());
            if( visitCnExtraStore != null){
                visitCn = visitCnExtraStore.getName();
            }else {
                visitCn = invoice.getVisitTypeCn();
            }
            for(YongChengVisitTypeEnum e : YongChengVisitTypeEnum.values()) {
                if (e.getVisitTypeCn().equals(visitCn)) {
                    OptionSetDTO exCodeOptionSet = syncBaseTool.matchCollectionCode(e.getExAccidenttypeCn(), "EX_accidenttype",
                            invoice.getBizIdentityCode(), invoice.getRelatedId(), FieldModelDefine.发票, true, new HashMap<>());

                    invoice.getExtraProperties().put("EX_accidenttype", exCodeOptionSet== null?"":exCodeOptionSet.getCode());
                     ExtraStoreUtil.fillExtraStoreWithOrigin(invoice, "EX_accidenttype", exCodeOptionSet);
                }
            }


            //自动计算住院天数与津贴天数
            Date[] invoiceDates = parse(invoice.getHospitalPeriod());

            long inPeriodDays = DateUtil.calculateDayDifference(invoiceDates[0], invoiceDates[1], DateRangeEnum.CALENDER);
            if (inPeriodDays < 1) {
                invoice.setHospitalDays(1);
                invoice.setSubsidyDays(1);
            } else {
                invoice.setHospitalDays((int) inPeriodDays);
                invoice.setSubsidyDays((int) inPeriodDays);
            }

        } catch (Exception e) {
            log.info("onUpdateInvoice error1",e);
        }


        try {

            if(StringUtils.isBlank(invoice.getBillType())){
                //医疗收据类型
                String medicType = SyncBaseTool.EXT_FIELD_PREFIX+"medicaltype";
                String medicTypeCode = null;
                if( invoice.getExtraProperties()!= null){
                    medicTypeCode = (  String)  invoice.getExtraProperties().get(medicType);
                }
                //出险原因
                String outInsureReason = invoice.getOutInsureReason();
                //就诊类型
                // 1 门/急诊
                // 2 住院
                // 3  药房
                // 4   其他
                // 5 门诊-慢特病
                String visitType =  invoice.getVisitType();
                log.info("start cal billtype,medicTypeCode:{},outInsureReason:{},visitType:{},hasyb:{}",medicTypeCode,outInsureReason,visitType,invoice.getHasYb());

                String billTypeCn =   "普通门诊";
                if(StringUtils.isBlank(medicTypeCode)){
                    billTypeCn = "普通门诊";

                }
                if("3".equals(medicTypeCode)){
                    billTypeCn = "医疗特殊病";
                }
                if("2".equals(medicTypeCode)){
                    billTypeCn = "医保审批单";
                }
                if("4".equals(medicTypeCode)){
                    billTypeCn = "分割单";
                }
                if("1".equals(medicTypeCode)){
                    if(StringUtils.isBlank(outInsureReason)){
                        billTypeCn = "普通门诊";
                    } else if ("4".equals(outInsureReason)) {
                        if("2".equals(visitType)){
                            billTypeCn = "生育住院";
                        }else {
                            billTypeCn = "生育门诊";
                        }

                    } else if ("1".equals(outInsureReason) || "2".equals(outInsureReason)) {
                        if(invoice.getHasYb() == null){
                            billTypeCn = "普通门诊";
                        }else if ("0".equals(invoice.getHasYb().toString())) {
                            if("2".equals(visitType)){
                                billTypeCn = "普通住院";
                            }else {
                                billTypeCn = "普通门诊";
                            }
                        }else {
                            if("2".equals(visitType)){
                                billTypeCn = "医疗住院";
                            }else {
                                billTypeCn = "医疗门诊";
                            }
                        }

                    }else{
                        billTypeCn = "普通门诊";
                    }
                }
                log.info("start cal billtype,chinese:{}",billTypeCn);
                OptionSetDTO billTypeOptionSet =    syncBaseTool. matchCollectionCodeWithAlert(billTypeCn,InvoiceFieldsConstants.BILL_TYPE,
                        invoice.getBizIdentityCode(),
                        invoice.getRelatedId(),FieldModelDefine.发票,false,false,new HashMap<>());
                if( billTypeOptionSet != null){
                    invoice.setBillType(billTypeOptionSet.getCode());
                    invoice.setBillTypeCn( billTypeOptionSet.getName()) ;
                    ExtraStoreUtil.fillExtraStoreWithOrigin(invoice,InvoiceFieldsConstants.BILL_TYPE, billTypeOptionSet);
                    log.info("end cal billtype ,return :{}", JSONObject.toJSON(billTypeOptionSet));
                }else{
                    log.info("end cal billtype,not match chinese");
                }
            }

        }catch (Exception e) {
            log.info("onUpdateInvoice error2",e);
        }


    }



    /**
     * 解析逗号分隔的日期字符串为 Date 数组
     * @param input 输入字符串（格式：date1,date2）
     * @return 包含两个 Date 的数组
     * @throws IllegalArgumentException 输入不符合要求时抛出
     */
    private Date[] parse(String input) throws ParseException {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        // 空值检查
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("输入字符串不能为空");
        }

        // 分割字符串
        String[] parts = input.split("\\s*,\\s*"); // 允许逗号前后有空格
        if (parts.length != 2) {
            throw new IllegalArgumentException("输入必须包含两个日期，实际数量: " + parts.length);
        }

        return new Date[] {dateFormat.parse(parts[0]), dateFormat.parse(parts[1])};
    }

    /**
     * 计算年龄
     * 周岁
     */
    private Integer calculateAge(String identityCode) {
        // 提取出生日期 (第7到14位)
        String birthDateStr = identityCode.substring(6, 14);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate birthDate = LocalDate.parse(birthDateStr, formatter);

        // 计算年龄
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
