package com.bone.tpa.test.claim.sync;

import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.enums.*;
import com.bone.tpa.api.request.SyncClaimWithEventRequest;
import com.bone.tpa.api.vo.*;
import com.bone.tpa.claim.sync.ClaimSyncFromTpaService;
import com.bone.tpa.sdk.claim.enums.ClmProcessType;
import com.bone.tpa.test.BaseTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TpaSyncTest  extends BaseTest {


    @Autowired
    ClaimSyncFromTpaService claimSyncFromTpaService;


    public static SyncClaimWithEventRequest generateRequest(){
        SyncClaimWithEventRequest request = new SyncClaimWithEventRequest();
        request.setClaimNumber(ClaimTestConstants.claimNumber);
        request.setEventType(EventType.tpa赔案跳过初审下发到saas录入.getCode());
        ClaimConfig claimConfig = new ClaimConfig();
        claimConfig.setClaimNumber(ClaimTestConstants.claimNumber);
        claimConfig.setBizInputType(CfgBizInputTypeEnum.INVOICE.getCode());
        claimConfig.setClmProcess(ClmProcessType.FULL_PROCESS.getCode());
        claimConfig.setImageClassify(YesOrNoEnum.YES.getCode());
        claimConfig.setCaseClaimAudit(YesOrNoEnum.YES.getCode());
        claimConfig.setCaseClaimAuditType(0);
        claimConfig.setBizType(2);
        claimConfig.setNeedVerify(0);
        claimConfig.setCanBeAutomation(ClaimAutomationEnum.NON_AUTOMATION.getCode());
        request.setClaimConfig(claimConfig);


        request.setClaimInfo(generClaimDetailSyncVO());
        return request;
    }

    public static ClaimDetailSyncVO generClaimDetailSyncVO(){
        ClaimDetailSyncVO rs = new ClaimDetailSyncVO();
        rs.setClaimNumber(ClaimTestConstants.claimNumber);
        rs.setClaimDetailId(ClaimTestConstants.claimUuid);
        rs.setPolicyNo(ClaimTestConstants.PolicyNo);
        rs.setCaseNo(ClaimTestConstants.caseNo);
        ClaimHeadInfo headInfo =new ClaimHeadInfo();
        headInfo.setClaimheadnumber(ClaimTestConstants.claimHeadNumber);
        rs.setClaimHeadInfo(headInfo);
        rs.setSlipPersonPsc(ClaimTestConstants.slipPersonPsc);
        rs.setVipSignCn(ClaimTestConstants.vipSignCn);
        rs.setAccidentTime(ClaimTestConstants.accidentTime);
        rs.setAccidentTypeCn(ClaimTestConstants.accidentTypeCn);
        rs.setOutAccidentProvice(ClaimTestConstants.outAccidentProvice);
        rs.setOutAccidentCity(ClaimTestConstants.outAccidentCity);
        rs.setOutAccidentArea(ClaimTestConstants.outAccidentArea);
        rs.setOutAccidentAddress(ClaimTestConstants.getOutAccidentAddress);
        rs.setSourceType("线上");
        rs.setSourceCompanyCode(ClaimTestConstants.sourceCompanyCode);
        rs.setSourceCompanyName(ClaimTestConstants.sourceCompanyName);
        rs.setInsurancePolicyNo(ClaimTestConstants.insurancePolicyNo);
        rs.setPolicyStartDate(ClaimTestConstants.policyStartDate);
        rs.setPolicyEndDate(ClaimTestConstants.policyEndDate);
        rs.setInsuranceCoverage(ClaimTestConstants.insuranceCoverage);
        rs.setSlipAttribute(ClaimTestConstants.slipAttribute);
        rs.setInsuranceName(ClaimTestConstants.insuranceName);
        rs.setBranchName(ClaimTestConstants.branchName);
        rs.setInsureName(ClaimTestConstants.insureName);
        Map<String, String> extMap = Map.of("ext1","1","ext2","2");
        rs.setExtMap(extMap);
        //主被保险人
        rs.setMainPersonInfo(fillmainPersonInfo());
        //申请人
        rs.setApplyPersonInfo(fillApplyPersonInfo());
        //出险人
        rs.setOutPersonInfo(fillOutInsurePersonInfo());
        //benefiList 出险人
        rs.setBenefiList(PkListUtil.asList(fillBenifitPersonInfo()));

        //collectPersonInfo
        rs.setCollectPersonInfo(collectPersonInfo());
        //invoiceList
        rs.setInvoiceList(generateInvoiceList());

        //iamgeList
        rs.setImageList(PkListUtil.asList(fillImageInfo()));
        //imageBindList
        ImageBindInvoice bindInvoice = new  ImageBindInvoice();
        bindInvoice.setImageUuid(ImageTestConstants.imageUuid);
        bindInvoice.setInvoiceUuid(InvoiceTestConstants.invoiceUuid);
        rs.setImageBindList(PkListUtil.asList(bindInvoice));
        return rs;
    }

    public static ImageInfo fillImageInfo(){
        ImageInfo rs = new ImageInfo();
        //imageUuid
        rs.setImageUuid(ImageTestConstants.imageUuid);
        //imageName
        rs.setImageName(ImageTestConstants.imageName);
        //imagePath
        rs.setImagePath(ImageTestConstants.imagePath);
        //imageIndex
        rs.setImageIndex(ImageTestConstants.imageIndex);
        //imageType
        rs.setImageType(ImageTestConstants.imageType);
        //source
        rs.setSource(ImageTestConstants.source);
        //ocrDealedFlag
        rs.setOcrDealedFlag(ImageTestConstants.ocrDealedFlag);
        //pushFlag
        rs.setPushFlag(ImageTestConstants.pushFlag);
        return rs;
    }

    /**
     * 发票，项目，费用
     * @return
     */
    public static List<InvoiceInfo> generateInvoiceList(){
        List<InvoiceInfo> rs = new ArrayList<>();
        InvoiceInfo invoice = new InvoiceInfo();
        invoice.setInvoiceUuid(InvoiceTestConstants.invoiceUuid);
        invoice.setInvoiceNo(InvoiceTestConstants.invoiceNo);
        invoice.setInputModeCn(InvoiceTestConstants.inputModeCn);
        invoice.setInvoiceTypeCn(InvoiceTestConstants.invoiceTypeCn);
        invoice.setInvoiceDate(InvoiceTestConstants.invoiceDate);
        invoice.setInvoiceAmount(InvoiceTestConstants.invoiceAmount);
        invoice.setPaperType(InvoiceTestConstants.paperType);
        invoice.setEInvoiceNo(InvoiceTestConstants.eInvoiceNo);
        invoice.setVerificationCode(InvoiceTestConstants.verificationCode);
        invoice.setBillTypeCn(InvoiceTestConstants.billTypeCn);
        invoice.setVerifyValidCn(InvoiceTestConstants.verifyValidCn);
        invoice.setLiveEndDate(InvoiceTestConstants.liveEndDate);
        invoice.setLiveStartDate(InvoiceTestConstants.liveStartDate);
        invoice.setTreatmentTypeCn(InvoiceTestConstants.treatmentTypeCn);
        invoice.setInvoiceName(InvoiceTestConstants.invoiceName);
        invoice.setMedicalTypeCn(InvoiceTestConstants.medicalTypeCn);
        invoice.setYbFlag(InvoiceTestConstants.ybFlag);
        invoice.setYbTypeCn(InvoiceTestConstants.ybTypeCn);
        invoice.setHospitalName(InvoiceTestConstants.hospitalName);
        invoice.setHospitalLevelCn(InvoiceTestConstants.hospitalLevelCn);
        invoice.setInvoiceTypeCn(InvoiceTestConstants.invoiceTypeCn);
        invoice.setTreatmentTypeCn(InvoiceTestConstants.treatmentTypeCn);
        invoice.setInvoiceName(InvoiceTestConstants.invoiceName);
        invoice.setHospitalTypeCn(InvoiceTestConstants.hospitalTypeCn);
        invoice.setHospitalDepartmentCn(InvoiceTestConstants.hospitalDepartmentCn);
        invoice.setDiseaseName(InvoiceTestConstants.diseaseName);
        invoice.setSevereName(InvoiceTestConstants.severeName);
        invoice.setSevereFlag(InvoiceTestConstants.severeFlag);
        invoice.setChronicFlag(InvoiceTestConstants.chronicFlag);

        invoice.setChronicName(InvoiceTestConstants.chronicName);

        invoice.setSubsidyDays(InvoiceTestConstants.subsidyDays);

        invoice.setEnteredAmount(InvoiceTestConstants.enteredAmount);
        invoice.setTotalMedicalFundPayment(InvoiceTestConstants.totalMedicalFundPayment);

        invoice.setBasicPoolingAmount(InvoiceTestConstants.basicPoolingAmount);
        invoice.setPoolingReimbursementRate(InvoiceTestConstants.poolingReimbursementRate);
        //partSelfPayAmount
        invoice.setPartSelfPayAmount(InvoiceTestConstants.partSelfPayAmount);
        //totalSelfPayAmount
        invoice.setTotalSelfPayAmount(InvoiceTestConstants.totalSelfPayAmount);
        //classCSelfPayAmount
        invoice.setClassCSelfPayAmount(InvoiceTestConstants.classCSelfPayAmount);
        //excessLimitSelfPayAmount
        invoice.setExcessLimitSelfPayAmount(InvoiceTestConstants.excessLimitSelfPayAmount);
        //thirdPartyPaidAmount
        invoice.setThirdPartyPaidAmount(InvoiceTestConstants.thirdPartyPaidAmount);
        //otherFundAmount
        invoice.setOtherFundAmount(InvoiceTestConstants.otherFundAmount);
        //validAmount
        invoice.setValidAmount(InvoiceTestConstants.validAmount);
        //invalidAmount
        invoice.setInvalidAmount(InvoiceTestConstants.invalidAmount);
        //remark
        invoice.setRemark(InvoiceTestConstants.remark);
        //accountNo
        invoice.setAccountNo(InvoiceTestConstants.accountNo);
       // invoice.setProjectInfoList(PkListUtil.asList(fillProject()));
        invoice.setCostItemInfoList(PkListUtil.asList(fillItemCost()));
        rs.add(invoice);
        return rs;
    }

    public static ProjectInfo fillProject(){
        ProjectInfo rs = new ProjectInfo();
        //invoiceUuid
        rs.setInvoiceUuid(ProjectTestConstatns.invoiceUuid);
        //projectName
        rs.setProjectName(ProjectTestConstatns.projectName);
        //amount
        rs.setAmount(ProjectTestConstatns.amount);
        //allSelfPayAmount
        rs.setAllSelfPayAmount(ProjectTestConstatns.allSelfPayAmount);
        //partSelfPayAmount
        rs.setPartSelfPayAmount(ProjectTestConstatns.partSelfPayAmount);
        //thirdPayAmount
        rs.setThirdPayAmount(ProjectTestConstatns.thirdPayAmount);
        //reasonableAmount
        rs.setReasonableAmount(ProjectTestConstatns.reasonableAmount);
        //unReasonableAmount
        rs.setUnReasonableAmount(ProjectTestConstatns.unReasonableAmount);

        return  rs;
    }

    public static CostItemInfo fillItemCost(){
        CostItemInfo rs = new CostItemInfo();
        //invoiceUuid
        rs.setInvoiceUuid(ItemCostTestContants.invoiceUuid);
        //projectName
        rs.setProjectName(ItemCostTestContants.projectName);
        //drugName
        rs.setDrugName(ItemCostTestContants.drugName);
        //medicalTypeCn
        rs.setMedicalTypeCn(ItemCostTestContants.medicalTypeCn);
        //selfPayPercent
        rs.setSelfPayPercent(ItemCostTestContants.selfPayPercent);
        //unitPrice
        rs.setUnitPrice(ItemCostTestContants.unitPrice);
        //itemUnit
        rs.setItemUnit(ItemCostTestContants.itemUnit);
        //occurAmount
        rs.setOccurAmount(ItemCostTestContants.occurAmount);
        //payAmount
        rs.setPayAmount(ItemCostTestContants.payAmount);
        //dosageFormCn
        rs.setDosageFormCn(ItemCostTestContants.dosageFormCn);
        return rs;
    }

    public static CollectPersonInfo collectPersonInfo(){
        CollectPersonInfo rs = new CollectPersonInfo();
        rs.setCollectTypeCn(CollectTypeEnum.PERSON.getDesc());
        rs.setName(CollectionPersonTestConstants.name);
        rs.setGender(CollectionPersonTestConstants.gender);
        rs.setBirthday(CollectionPersonTestConstants.birthday);
        rs.setIdentityTypeCn(CollectionPersonTestConstants.identityTypeCn);
        rs.setIdentityNo(CollectionPersonTestConstants.identityNo);
        rs.setIdentityStart(CollectionPersonTestConstants.identityStart);
        rs.setIdentityEnd(CollectionPersonTestConstants.identityEnd);
        rs.setOccupationCn(CollectionPersonTestConstants.occupationCn);
        rs.setNationality(CollectionPersonTestConstants.nationality);
        rs.setPhone(CollectionPersonTestConstants.phone);
        rs.setContactProvice(CollectionPersonTestConstants.contactProvice);
        rs.setContactCity(CollectionPersonTestConstants.contactCity);
        rs.setContactArea(CollectionPersonTestConstants.contactArea);
        rs.setContactAddress(CollectionPersonTestConstants.contactAddress);
        rs.setRelationToOutInsureCn(CollectionPersonTestConstants.relationToOutInsureCn);
        rs.setRelationToMainInsureCn(CollectionPersonTestConstants.relationToMainInsureCn);
        rs.setExtMap(CollectionPersonTestConstants.extMap);
        rs.setTransferMethodTypeCn(CollectionPersonTestConstants.transferMethodTypeCn);
        rs.setBankName(CollectionPersonTestConstants.bankName);
        rs.setRelationToBenifitCn(CollectionPersonTestConstants.relationToBenifitCn);
        rs.setBankBranchName(CollectionPersonTestConstants.bankBranchName);
        rs.setCollectTypeCn(CollectionPersonTestConstants.collectTypeCn);
        rs.setAccountNo(CollectionPersonTestConstants.accountNo);
        rs.setBankAddress(CollectionPersonTestConstants.bankAddress);
        rs.setBankProvince(CollectionPersonTestConstants.bankProvince);
        rs.setBankCity(CollectionPersonTestConstants.bankCity);
        rs.setPaymentMethodTypeCn(CollectionPersonTestConstants.paymentMethodTypeCn);
        return  rs;
    }


    public static  CollectPersonInfo collectBusinessInfo(){
        return  null;
    }
    public static ClaimPersonInfo fillBenifitPersonInfo(){
        ClaimPersonInfo rs  = new ClaimPersonInfo();
        rs.setName(BenifitTestConstants.name);
        rs.setGender(BenifitTestConstants.gender);
        rs.setBirthday(BenifitTestConstants.birthday);
        rs.setIdentityTypeCn(BenifitTestConstants.identityTypeCn);
        rs.setIdentityNo(BenifitTestConstants.identityNo);
        rs.setIdentityStart(BenifitTestConstants.identityStart);
        rs.setIdentityEnd(BenifitTestConstants.identityEnd);
        rs.setOccupationCn(BenifitTestConstants.occupationCn);
        rs.setNationality(BenifitTestConstants.nationality);
        rs.setPhone(BenifitTestConstants.phone);
        rs.setContactProvice(BenifitTestConstants.contactProvice);
        rs.setContactCity(BenifitTestConstants.contactCity);
        rs.setContactArea(BenifitTestConstants.contactArea);
        rs.setContactAddress(BenifitTestConstants.contactAddress);
        rs.setRelationToOutInsureCn(BenifitTestConstants.relationToOutInsureCn);
        rs.setRelationToMainInsureCn(BenifitTestConstants.relationToMainInsureCn);
        rs.setBenefitPercentage(BenifitTestConstants.benefitPercentage);
        rs.setTpaBenifitId(BenifitTestConstants.tpaBenifitId);
        rs.setExtMap(BenifitTestConstants.extMap);
        return  rs;
    }

    public static ClaimPersonInfo fillOutInsurePersonInfo(){
        ClaimPersonInfo rs  = new ClaimPersonInfo();
        rs.setName(OutInsureTest.name);
        rs.setGender(OutInsureTest.gender);
        rs.setBirthday(OutInsureTest.birthday);
        rs.setIdentityTypeCn(OutInsureTest.identityTypeCn);
        rs.setIdentityNo(OutInsureTest.identityNo);
        rs.setIdentityStart(OutInsureTest.identityStart);
        rs.setIdentityEnd(OutInsureTest.identityEnd);
        rs.setOccupationCn(OutInsureTest.occupationCn);
        rs.setNationality(OutInsureTest.nationality);
        rs.setPhone(OutInsureTest.phone);
        rs.setContactProvice(OutInsureTest.contactProvice);
        rs.setContactCity(OutInsureTest.contactCity);
        rs.setContactArea(OutInsureTest.contactArea);
        rs.setContactAddress(OutInsureTest.contactAddress);
        rs.setRelationToMainInsureCn(OutInsureTest.relationToMainInsureCn);
        rs.setExtMap(OutInsureTest.extMap);
        return  rs;
    }

    /**
     * 申请人
     * @return
     */
    public static ClaimPersonInfo fillApplyPersonInfo(){
        ClaimPersonInfo rs  = new ClaimPersonInfo();
        rs.setName(ApplyPersonTestContants.name);
        rs.setGender(ApplyPersonTestContants.gender);
        rs.setBirthday(ApplyPersonTestContants.birthday);
        rs.setIdentityTypeCn(ApplyPersonTestContants.identityTypeCn);
        rs.setIdentityNo(ApplyPersonTestContants.identityNo);
        rs.setIdentityStart(ApplyPersonTestContants.identityStart);
        rs.setIdentityEnd(ApplyPersonTestContants.identityEnd);
        rs.setOccupationCn(ApplyPersonTestContants.occupationCn);
        rs.setNationality(ApplyPersonTestContants.nationality);
        rs.setPhone(ApplyPersonTestContants.phone);
        rs.setContactProvice(ApplyPersonTestContants.contactProvice);
        rs.setContactCity(ApplyPersonTestContants.contactCity);
        rs.setContactArea(ApplyPersonTestContants.contactArea);
        rs.setContactAddress(ApplyPersonTestContants.contactAddress);
        rs.setRelationToOutInsureCn(ApplyPersonTestContants.relationToOutInsureCn);
        rs.setExtMap(ApplyPersonTestContants.extMap);
        return  rs;
    }

    /**
     * 主被保险人
     * @return
     */
    public static ClaimPersonInfo fillmainPersonInfo(){
        ClaimPersonInfo rs  = new ClaimPersonInfo();
        rs.setName(MainPersonInfoTestConstants.name);
        rs.setGender(MainPersonInfoTestConstants.gender);
        rs.setBirthday(MainPersonInfoTestConstants.birthday);
        rs.setIdentityTypeCn(MainPersonInfoTestConstants.identityTypeCn);
        rs.setIdentityNo(MainPersonInfoTestConstants.identityNo);
        rs.setIdentityStart(MainPersonInfoTestConstants.identityStart);
        rs.setIdentityEnd(MainPersonInfoTestConstants.identityEnd);
        rs.setOccupationCn(MainPersonInfoTestConstants.occupationCn);
        rs.setNationality(MainPersonInfoTestConstants.nationality);
        rs.setPhone(MainPersonInfoTestConstants.phone);
        rs.setContactProvice(MainPersonInfoTestConstants.contactProvice);
        rs.setContactCity(MainPersonInfoTestConstants.contactCity);
        rs.setContactArea(MainPersonInfoTestConstants.contactArea);
        rs.setContactAddress(MainPersonInfoTestConstants.contactAddress);
        rs.setRelationToOutInsureCn(MainPersonInfoTestConstants.relationToOutInsureCn);
        rs.setExtMap(MainPersonInfoTestConstants.extMap);
        return  rs;
    }


}
