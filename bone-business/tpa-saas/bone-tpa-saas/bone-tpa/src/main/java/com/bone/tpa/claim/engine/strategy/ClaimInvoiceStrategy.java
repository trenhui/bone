package com.bone.tpa.claim.engine.strategy;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.core.util.JsonUtil;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.claim.application.converter.ClaimInvoiceConverter;
import com.bone.tpa.claim.application.dto.ClaimInvoiceDTO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.claim.domain.ext.strategy.YongChengUpdateRule;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.hook.ClaimHookUtil;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.service.PlanService;
import com.bone.tpa.sdk.dao.ClaimInvoiceRepository;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.domain.service.InvoiceImageRelationService;
import com.bone.tpa.claim.domain.service.InvoiceProjectItemService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityInfoService;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.identityRule.invoice.update.OnSaveInvoiceHook;
import com.bone.tpa.sdk.util.ObjInvoke;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;



/**
 * 发票信息通用处理逻辑
 *
 */
@Slf4j
@Service
public class ClaimInvoiceStrategy extends DefaultStrategy {

    private final String TABLE = "ss_claim_invoice";

    @Autowired
    private ClaimInvoiceConverter invoiceConverter;

    @Autowired
    private CommonLogService logService;

    @Autowired
    private ClaimInvoiceRepository invoiceRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private InvoiceImageRelationService relationService;

    @Autowired
    private InvoiceProjectItemService itemService;

    @Autowired
    private YongChengUpdateRule yongChengUpdateRule;

    @Autowired
    private LiabilityInfoService liabilityInfoService;

    @Autowired
    private PlanService planService;

    @Autowired
    private ClaimHookUtil hookUtil;

    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.CLAIM_INVOICE;
    }

    @Override
    public List<BizModelEnum> childBizModel() {
        return List.of(BizModelEnum.INVOICE_PROJECT_ITEM);
    }

    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        ClaimInvoice invoice = invoiceRepository.findById(request.getId());

        ClaimInvoiceDTO invoiceDTO = invoiceConverter.toDTO(invoice);

        ClaimHintMsg claimHintMsg = querySyncHint(invoiceDTO.getRelatedId(), HintMsgType.CLAIM_INVOICE, invoiceDTO.getId());
        if (claimHintMsg != null) {
            invoiceDTO.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
        }

        //更新责任相关
        Claim claim = claimService.getById(invoice.getRelatedId());
        //更新责任相关
        if (claim.getPlanUuid() == null || claim.getPlanUuid().isBlank()) {
            invoiceDTO.setRelateLiability(new ArrayList<>());
        } else {
            PlanDTO plan = planService.queryByPlanUuid(claim.getPlanUuid());

            if (plan == null) {
                invoiceDTO.setRelateLiability(new ArrayList<>());
            } else {
                invoiceDTO.setRelateLiability(liabilityInfoService.transferFromLiabilityUuid(invoice.getRelateLiability(), plan.getId()));
            }
        }

        return new GenericQueryResponse(invoiceDTO);
    }

    @Override
    public PageResult<ClaimInvoiceDTO> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        //这里加一个特殊判定。如果当前的赔案阶段是录入，则已经被ocr识别的发票不展示
        String claimId = "";
        for (QueryParam queryParam : paramList) {
            if (queryParam.getField().equals("relatedId")) {
                claimId = String.valueOf(queryParam.getValue());
            }
        }
        Claim claim = claimService.getById(Long.valueOf(claimId));
        if (claim.getStage().equals(ClaimStageEnum.SUBMITTING.getCode())) {
            paramList.add(new QueryParam("ocrFlag", 0));
        }

        Boolean flag = false;
        for (SortingField sortingField : sortingFields) {
            if (sortingField.getField().equals("id")) {
                flag = true;
            }
        }
        if (!flag) {
            sortingFields.add(new SortingField("id", "asc"));
        }

        PageResult<ClaimInvoice> result = invoiceRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        List<ClaimInvoice> invoiceList = result.getData();
        List<ClaimInvoiceDTO> invoiceDTOList = new ArrayList<>();
        for (ClaimInvoice invoice : invoiceList) {
            ClaimInvoiceDTO invoiceDTO = invoiceConverter.toDTO(invoice);
            ClaimHintMsg claimHintMsg = querySyncHint(invoiceDTO.getRelatedId(), HintMsgType.CLAIM_INVOICE, invoiceDTO.getId());
            if (claimHintMsg != null) {
                invoiceDTO.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
            }

            ObjInvoke.getObjBigDemical(invoiceDTO);

            //更新责任相关
            if (claim.getPlanUuid() == null || claim.getPlanUuid().isBlank()) {
                invoiceDTO.setRelateLiability(new ArrayList<>());
            } else {
                PlanDTO plan = planService.queryByPlanUuid(claim.getPlanUuid());

                if (plan == null) {
                    invoiceDTO.setRelateLiability(new ArrayList<>());
                } else {
                    invoiceDTO.setRelateLiability(liabilityInfoService.transferFromLiabilityUuid(invoice.getRelateLiability(), plan.getId()));
                }
            }

            invoiceDTOList.add(invoiceDTO);
        }

        return new PageResult<>(invoiceDTOList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
       // ClaimInvoiceDTO dto =  mapper.convertValue(data, ClaimInvoiceDTO.class);
       // logService.addLogASync(dto.getRelatedId().toString(), CommonLogType.CLAIM_COMMON,"invoice update input:"+ JSONObject.toJSONString(data));
        ClaimInvoiceDTO dto = mapper.convertValue(data, ClaimInvoiceDTO.class);
        ClaimInvoice invoice = invoiceConverter.toEntity(dto);
      //  logService.addLogASync(dto.getRelatedId().toString(), CommonLogType.CLAIM_COMMON,"invoice update invoicedo:"+ JSONObject.toJSONString(invoice));
        if (invoice == null) {
            return;
        }

        if (invoice.getInvoiceNo() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "发票号码未填写");
        }

        if (invoice.getInvoiceUuid() == null || invoice.getInvoiceUuid().isEmpty()) {
            invoice.setInvoiceUuid(String.valueOf(UUID.randomUUID()));
        }
        invoice.setUpdateTime(null);
        invoice.setCreateTime(null);
        if( invoice.getExtraStore() == null){
            invoice.setExtraStore("{}");
        }
        Claim claim = claimService.getById(invoice.getRelatedId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);
        ClaimInvoice existInvoice = null;
        if(invoice.getId()!= null) {
            existInvoice =  invoiceRepository.findById(invoice.getId());
        }
        if(existInvoice!= null){
            //需要做一次merge，避免全量覆盖
            String newStoreExtra =    ExtraStoreUtil.mergeExtraStore(invoice.getExtraStore(),existInvoice.getExtraStore());
            invoice.setExtraStore(newStoreExtra);
            invoice.setBizIdentityCode(existInvoice.getBizIdentityCode());
            invoice.setRelatedId(existInvoice.getRelatedId());
        }
        List<OnSaveInvoiceHook>  invoiceHookList =  hookUtil.getOnSaveInvoiceHookList(claim.getBizIdentityCode());
        for( OnSaveInvoiceHook hook: invoiceHookList){
            hook.doEvent(invoice);
        }

        //更新责任相关
        invoice.setRelateLiability(liabilityInfoService.transferToLiabilityUuid(dto.getRelateLiability()));

        //yongChengUpdateRule.onUpdateInvoice(invoice);

        invoiceRepository.save(invoice);
    }

    /**
     * 删除发票
     * 并且删除和发票相关的下属信息
     * 1. 费用项目明细
     * 2. 影像件绑定关系
     */
    @Override
    public void delete(DeleteRequest request) {
        if (request.getIdList() == null || request.getIdList().isEmpty()) {
            return;
        }

        //首先取出所有的发票，然后依次检查赔案
        List<ClaimInvoice> claimInvoiceList = invoiceRepository.findById(request.getIdList());

        List<Long> claimIdList = claimInvoiceList.stream().map(ClaimInvoice::getRelatedId).distinct().toList();

        List<Claim> claimList = claimService.getByIdList(claimIdList);
        for (Claim claim : claimList) {
            claimService.checkFinish(claim);
            claimService.checkUserId(claim);
        }

        //这里要取出全部的影像件发票关联关系并且删除
        List<String> invoiceUuidList = claimInvoiceList.stream().map(ClaimInvoice::getInvoiceUuid).toList();
        List<InvoiceImageRelation> relationList = relationService.getByInvoiceUuid(invoiceUuidList);
        List<Long> relationIdList = relationList.stream().map(InvoiceImageRelation::getId).toList();
        relationService.deleteRelation(relationIdList);

        //取出项目费用明细，并且删除
        List<InvoiceProjectItem> itemList = itemService.getByInvoiceId(request.getIdList());
        List<Long> itemIdList = itemList.stream().map(InvoiceProjectItem::getId).toList();
        itemService.deleteItem(itemIdList);

        //最后删除发票本身
        invoiceRepository.deleteByIds(request.getIdList());
    }

}
