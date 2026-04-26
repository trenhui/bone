package com.bone.tpa.task.impl;

import com.alibaba.fastjson.JSONObject;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.api.enums.PKImageMapEnum;
import com.bone.tpa.api.enums.YesOrNoEnum;
import com.bone.tpa.claim.domain.ext.strategy.YongChengUpdateRule;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.domain.service.HintService;
import com.bone.tpa.claim.flow.stage.InputStageService;
import com.bone.tpa.claim.flow.jump.QualityFlowFireService;
import com.bone.tpa.claim.flow.trigger.InputOcrCompleteTrigger;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.vo.InsuranceCompanyImageVO;
import com.bone.tpa.hook.ClaimHookUtil;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.dao.*;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.identityRule.invoice.update.OnSaveInvoiceHook;
import com.bone.tpa.sdk.util.ObjInvoke;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;
import com.bone.tpa.sdk.vo.InputConfigVO;
import com.bone.tpa.task.service.ImageOcrChangeService;
import com.bone.tpa.task.service.vo.ImageOcrChangeResult;
import com.bone.tpa.util.PkJsonUtil;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自动化录入模块
 */
@Slf4j
@Service
public class AutoInputTrigger extends SyncTaskTemplate {

    @Autowired
    private ImageOcrChangeService ocrChangeService;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimImageRepository imageRepository;

    @Autowired
    private InputStageService inputStageService;
    @Autowired
    private ClaimInvoiceRepository invoiceRepository;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private NotifyTpaOcrInputingTrigger tpaOcrInputingTrigger;

    @Autowired
    private InvoiceProjectItemRepository itemRepository;

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    protected CommonLogService commonLogService;

    @Autowired
    private ClaimHookUtil claimHookUtil;

    @Autowired
    protected HintService hintService;
    @Autowired
    protected ClaimFlowConfigBiz claimFlowConfigBiz;
    @Autowired
    protected QualityFlowFireService qualityFlowFireService;
    @Autowired
    private InvoiceImageRelationRepository relationRepository;

    @Autowired
    private YongChengUpdateRule yongChengUpdateRule;
    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "auto-ocr-input";
    }


    @Override
    public String getAlertUrl() {
        return dingAlertUrl;
    }

    @Override
    public String getAlertMode() {
        return AlertRobotManager.QIWEI_MODE;
    }

    @XxlJob("autoOcrInputJob")
    public void autoOcrInputJobXXljob() {
        execute();
        tpaOcrInputingTrigger.execute();
    }

    /**
     * 执行一个任务
     * 在这里加Transaction标记没用
     *
     * @param task
     */
    @Override
    public void syncOneData(SyncTask task) {
        Long claimNumber = Long.valueOf(task.getData());
        try {
            //为啥要这调用，其实为了引发事务控制
            SpringContextUtils.getBean(AutoInputTrigger.class).doAutoInput(claimNumber);
        } catch (Exception e) {
            log.error("AutoInputTrigger error,claimNumber:"+claimNumber, e);
            //变更状态为录入完成，申请分配人员录入（包括外包和普康录入）
            goToCompleteExample(claimNumber);
        }
    }

    /**
     * 状态修改成初审完成，并且稍后去请求
     *
     * @param claimNumber
     */
    private void goToCompleteExample(Long claimNumber) {

        Claim  claim =  claimRepository.findById(claimNumber);
        ClaimFlowConfigVO flowConfigVO =  claimFlowConfigBiz.convertById(claim.getFlowConfigId());
        InputConfigVO inputConfigVO =  flowConfigVO.getInputConfigVO();
        if(claim.getCfgBizType() == 2){
            //变成已初审状态
            Claim upDto = new Claim();
            upDto.setId(claimNumber);
            upDto.setStatus(ClaimStatusEnum.COMPLETE_PRE_ADUIT.getCode());
            upDto.setStatusSub(ClaimStatusEnum.COMPLETE_PRE_ADUIT.getSubStatus());
            upDto.setStage(ClaimStatusEnum.COMPLETE_PRE_ADUIT.getStage().getCode());
            claimService.updateClaim(upDto, false, "goToCompleteExample");
            //申请分配人员录入（包括外包和普康录入）,为啥用异步任务呢，因为很多场景
            //tpa->saas->tpa 怕tpa对一行记录有锁
            SpringContextUtils.getBean(InputDealerApplyTrigger.class).
                    addJobAndTryFire(claimNumber.toString(), 1, 3);
        }else{
            SpringContextUtils.getBean(InputOcrCompleteTrigger.class).
                    addJobAndTryFire( PkJsonUtil.buildPkJson("claimNumber",claim.getId().toString()),1, 3);
        }





    }


    @Transactional(rollbackFor = Throwable.class)
    public void doAutoInput(Long claimNumber) {
        Claim claim = claimRepository.findById(claimNumber);
        /**
         * 针对 claimNumber 对影像进行分类
         * vo.imageMapCode
         * 1  发票
         * 2  病例
         * 3  申请书
         * 4  身份证资料
         * 5  其他
         * 6  未分类
         */
        ApiResult<List<InsuranceCompanyImageVO>> remoteCategory = tpaDataSyncFeign.getImageMapDetail(claim.getInsuranceName());

        try {
            log.info("getImageMapDetail,claimNumber:{},remoteCategory:{}", claimNumber, JSONObject.toJSONString(remoteCategory));
            commonLogService.addClaimLogAsync(claimNumber, CommonLogType.CLAIM_COMMON, "orc识别，获取tpa分类: "+ JSONObject.toJSONString(remoteCategory));

        } catch (Exception e) {
        }
        if (!remoteCategory.isSuccess()) {
            commonLogService.addClaimLogAsync(claimNumber, CommonLogType.CLAIM_COMMON, "orc识别，tpa返回获取影像分类失败  ");

            throw new RuntimeException("tpa返回获取影像分类失败");
        }
        List<InsuranceCompanyImageVO> categoryList = remoteCategory.getData();
        if (PkListUtil.isEmpty(categoryList)) {
            commonLogService.addClaimLogAsync(claimNumber, CommonLogType.CLAIM_COMMON, "orc识别，tpa返回影像分类为空  ");

            throw new RuntimeException("tpa返回影像分类为空");
        }
        //保险公司的分类codeList
        List<String> baosiTypeList = categoryList.stream().filter(t -> t.getImageMapCode() != null)
                .filter(t -> "1".equals(t.getImageMapCode()))
                .map(InsuranceCompanyImageVO::getImageClassifyCode)
                .collect(Collectors.toList());
        if (PkListUtil.isEmpty(baosiTypeList)) {
            commonLogService.addClaimLogAsync(claimNumber, CommonLogType.CLAIM_COMMON, "orc识别，tpa返回发票类型的类别为空  ");
            throw new RuntimeException("tpa返回发票类型的类别为空");
        }
        Criteria<ClaimImage> imageCriteria = new Criteria<>();
        imageCriteria.eq(ClaimImage::getRelatedId, claimNumber);
        List<ClaimImage> claimImageList = imageRepository.findByCriteria(imageCriteria);
        if (PkListUtil.isEmpty(claimImageList)) {
            commonLogService.addClaimLogAsync(claimNumber, CommonLogType.CLAIM_COMMON, "orc识别，没有影像数据  ");

            throw new RuntimeException("没有影像数据");
        }
        //筛选出发票,通过普康分类和保司分类两个维度
        claimImageList = claimImageList.stream().filter(t -> baosiTypeList.contains(t.getImageType()) ||
                        Objects.equals(t.getImagePkType(), PKImageMapEnum.INVOICE.getCode()))
                .collect(Collectors.toList());
        if (PkListUtil.isEmpty(claimImageList)) {
            commonLogService.addClaimLogAsync(claimNumber, CommonLogType.CLAIM_COMMON, "orc识别，没有发票的影像数据  ");
            throw new RuntimeException("没有发票的影像数据");
        }

        Integer failNumber = 0;
        for (ClaimImage image : claimImageList) {
            ImageOcrChangeResult ocrChangeResult = null;
            log.info("start to ocrid:{}",image.getId());
            try {
                ocrChangeResult = ocrChangeService.changeImageOcr(claimNumber, image);
            } catch (Exception e) {
                log.error("影像件ocr发生异常,影像件id:" + image.getId() + ",影像件地址:" + image.getImagePath() + ",赔案id:" + image.getRelatedId(), e);
                failNumber++;
                commonLogService.addClaimLogAsync(claimNumber, CommonLogType.CLAIM_COMMON,
                        "影像件ocr发生异常,影像件id:" + image.getId() + ",影像件地址:" + image.getImagePath() + ",赔案id:" + image.getRelatedId()+",error:"+e.getMessage());
                continue;
            }
            if (ocrChangeResult == null) {
                log.error("影像件ocr结果为空,影像件id:" + image.getId() + ",影像件地址:" + image.getImagePath() + ",赔案id:" + image.getRelatedId());
                failNumber++;
                continue;
            }
            log.info("影像件ocr成功,影像件id:" + image.getId() + ",影像件地址:" + image.getImagePath() + ",赔案id:" + image.getRelatedId() + "结果:" + JSONObject.toJSONString(ocrChangeResult));

            //保存ocr结果
            ClaimInvoice invoice = ocrChangeResult.getInvoice();
            invoice.setInvoiceUuid(UUID.randomUUID().toString());
            invoice.setRelatedId(claimNumber);
            invoice.setBizIdentityCode(claim.getBizIdentityCode());
            invoice.setTenantId(claim.getTenantId());
            invoice.setOcrFlag(1);

            List<OnSaveInvoiceHook>  invoiceHookList =  claimHookUtil.
                    getOnSaveInvoiceHookList(claim.getBizIdentityCode());
            for( OnSaveInvoiceHook hook: invoiceHookList){
                hook.doEvent(invoice);
            }


            //yongChengUpdateRule.onUpdateInvoice(invoice);
            ObjInvoke.getObjBigDemical(invoice);

            invoiceRepository.insert(invoice);
            Map<String, String> invoiceHint = ocrChangeResult.getInvoiceHint();
            if (invoiceHint != null) {
                saveHint(invoice.getId(), HintMsgType.CLAIM_INVOICE, invoiceHint, claimNumber);
            }
            //image 和 发票关系
            InvoiceImageRelation relation = new InvoiceImageRelation();
            relation.setClaimId(claimNumber);
            relation.setImageDetailId(image.getImageDetailId());
            relation.setInvoiceUuid(invoice.getInvoiceUuid());
            relationRepository.insert(relation);

            List<InvoiceProjectItem> itemList = ocrChangeResult.getItemList();
            List<Map<String, String>> itemHintList = ocrChangeResult.getItemHintList();
            if (PkListUtil.isNotEmpty(itemList)) {
                for (int i = 0; i < itemList.size(); i++) {
                    InvoiceProjectItem item = itemList.get(i);
                    item.setRelatedId(invoice.getId());
                    item.setRelatedInvoiceNo(invoice.getInvoiceNo());
                    item.setBizIdentityCode(claim.getBizIdentityCode());
                    item.setTenantId(claim.getTenantId());
                    item.setRelatedId(invoice.getId());
                    ObjInvoke.getObjBigDemical(item);
                    itemRepository.insert(item);
                    if (itemHintList.size() == itemList.size()) {
                        Map<String, String> itemHint = itemHintList.get(i);
                        saveHint(item.getId(), HintMsgType.INVOICE_PROJECT_ITEM, itemHint, claimNumber);
                    }
                }
            }

            //image 打标成ocr识别
            ClaimImage imageUpdateDto = new ClaimImage();
            imageUpdateDto.setId(image.getId());
            imageUpdateDto.setOcrFlag(YesOrNoEnum.YES.getCode());
            imageRepository.update(imageUpdateDto);
        }
        //
        goToCompleteExample(claimNumber);

    }


    protected void saveHint(Long objectId, HintMsgType hintMsgType,
                            Map<String, String> hintMap, Long claimNumber) {
        hintService.saveHint(objectId, hintMsgType, hintMap, claimNumber);
    }
}
