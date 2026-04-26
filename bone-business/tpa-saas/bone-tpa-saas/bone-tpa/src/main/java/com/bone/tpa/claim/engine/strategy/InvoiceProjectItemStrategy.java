package com.bone.tpa.claim.engine.strategy;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.core.util.JsonUtil;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.api.enums.MedicalCostTypeEnum;
import com.bone.tpa.claim.application.converter.InvoiceProjectItemConverter;
import com.bone.tpa.claim.application.dto.InvoiceProjectItemDTO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.sdk.dao.ClaimInvoiceRepository;
import com.bone.tpa.sdk.dao.InvoiceProjectItemRepository;
import com.bone.tpa.sdk.dao.InvoiceProjectRepository;
import com.bone.tpa.claim.domain.service.ClaimInvoiceService;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.util.ObjInvoke;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 费用详情信息通用处理逻辑
 *
 */
@Service
public class InvoiceProjectItemStrategy extends DefaultStrategy {

    private final String TABLE = "ss_invoice_project_item";

    @Autowired
    private InvoiceProjectItemConverter itemConverter;

    @Autowired
    private InvoiceProjectItemRepository itemRepository;

    @Autowired
    private ClaimInvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceProjectRepository projectRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimInvoiceService invoiceService;

    private static final int RESULT_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP; // 四舍五入模式

    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.INVOICE_PROJECT_ITEM;
    }

    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        InvoiceProjectItem item = itemRepository.findById(request.getId());

        InvoiceProjectItemDTO itemDTO = itemConverter.toDTO(item);

        ClaimInvoice invoice = invoiceRepository.findById(item.getRelatedId());

        ClaimHintMsg claimHintMsg = querySyncHint(invoice.getRelatedId(), HintMsgType.INVOICE_PROJECT_ITEM, item.getId());
        if (claimHintMsg != null) {
            itemDTO.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
        }

        return new GenericQueryResponse(itemDTO);
    }

    @Override
    public PageResult<InvoiceProjectItemDTO> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        Boolean flag = false;
        for (SortingField sortingField : sortingFields) {
            if (sortingField.getField().equals("id")) {
                flag = true;
            }
        }
        if (!flag) {
            sortingFields.add(new SortingField("id", "asc"));
        }
        PageResult<InvoiceProjectItem> result = itemRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        List<InvoiceProjectItem> itemList = result.getData();
        List<InvoiceProjectItemDTO> itemDTOList = new ArrayList<>();
        for (InvoiceProjectItem item : itemList) {
            InvoiceProjectItemDTO itemDTO = itemConverter.toDTO(item);
            ClaimInvoice invoice = invoiceRepository.findById(item.getRelatedId());

            ClaimHintMsg claimHintMsg = querySyncHint(invoice.getRelatedId(), HintMsgType.INVOICE_PROJECT_ITEM, item.getId());
            if (claimHintMsg != null) {
                itemDTO.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
            }
            ObjInvoke.getObjBigDemical(itemDTO);
            itemDTOList.add(itemDTO);
        }

        return new PageResult<>(itemDTOList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
        InvoiceProjectItem item = itemConverter.toEntity(mapper.convertValue(data, InvoiceProjectItemDTO.class));

        ClaimInvoice invoice = invoiceRepository.findById(item.getRelatedId());
        Claim claim = claimService.getById(invoice.getRelatedId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        //针对费用详情做出各种限制
        if (item.getItemTotalAmount() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "金额未填写");
        }
        if (item.getMedicalType() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "医疗费用类型未填写");
        }
        if (item.getMedicalType().equals(MedicalCostTypeEnum.乙类.getCode()) && item.getChargingPercentage() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "乙类医疗费用需要输入自付比例");
        }
        if (item.getCount() == null || item.getCount() <= 0) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "数量不合法");
        }

        //这里去计算单价和扣费比例，不按照入参来录入
        if (MedicalCostTypeEnum.甲类.getCode().equals(item.getMedicalType())) {
            item.setChargingPercentage(BigDecimal.ZERO);
            item.setChargingAmount(BigDecimal.ZERO);
        } else if (MedicalCostTypeEnum.丙类.getCode().equals(item.getMedicalType())) {
            item.setChargingPercentage(BigDecimal.valueOf(100));
            item.setChargingAmount(item.getItemTotalAmount());
        } else if (MedicalCostTypeEnum.乙类.getCode().equals(item.getMedicalType())) {
            if (item.getChargingPercentage().compareTo(BigDecimal.valueOf(100)) > 0
                    || item.getChargingPercentage().compareTo(BigDecimal.ZERO) < 0) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "自付比例不合法");
            }
            item.setChargingAmount(item.getItemTotalAmount().multiply(item.getChargingPercentage()).multiply(BigDecimal.valueOf(0.01)).setScale(RESULT_SCALE, ROUNDING_MODE));
        }

        item.setPrice(item.getItemTotalAmount().divide(BigDecimal.valueOf(item.getCount()), RESULT_SCALE, ROUNDING_MODE));
        item.setRelatedInvoiceNo(invoice.getInvoiceNo());
        if (item.getItemUuid() == null || item.getItemUuid().isEmpty()) {
            item.setItemUuid(String.valueOf(UUID.randomUUID()));
        }
        item.setUpdateTime(null);
        item.setCreateTime(null);
        InvoiceProjectItem exist =null;
        if(item.getId()!= null){
            exist = itemRepository.findById(item.getId());
        }
        if(exist!= null){
            //需要做一次merge，避免全量覆盖
            String newStoreExtra =    ExtraStoreUtil.mergeExtraStore(item.getExtraStore(),exist.getExtraStore());
            item.setExtraStore(newStoreExtra);
        }


        itemRepository.save(item);

        //更新发票的金额字段
        Criteria<InvoiceProjectItem> itemCriteria = new Criteria<>();
        itemCriteria.eq(InvoiceProjectItem::getRelatedId, item.getRelatedId());

        List<InvoiceProjectItem> itemList = itemRepository.findByCriteria(itemCriteria);
        invoice = invoiceService.updateInvoiceMoney(invoice, itemList);

        invoiceRepository.update(invoice);
    }

    @Override
    public void delete(DeleteRequest request) {
        if (request.getIdList() == null || request.getIdList().isEmpty()) {
            return;
        }
        List<InvoiceProjectItem> itemList = itemRepository.findById(request.getIdList());

        ClaimInvoice claimInvoice = invoiceRepository.findById(itemList.get(0).getRelatedId());
        Claim claim = claimService.getById(claimInvoice.getRelatedId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        itemRepository.deleteByIds(request.getIdList());

        Set<Long> invoiceIdSet = itemList.stream().map(InvoiceProjectItem::getRelatedId).collect(Collectors.toSet());

        List<ClaimInvoice> invoiceList = invoiceRepository.findById(invoiceIdSet.stream().toList());

        List<ClaimInvoice> newInvoiceList = new ArrayList<>();
        for (ClaimInvoice invoice : invoiceList) {
            Criteria<InvoiceProjectItem> itemCriteria = new Criteria<>();
            itemCriteria.eq(InvoiceProjectItem::getRelatedId, claimInvoice.getId());

            List<InvoiceProjectItem> updateItemList = itemRepository.findByCriteria(itemCriteria);
            newInvoiceList.add(invoiceService.updateInvoiceMoney(invoice, updateItemList));
        }

        invoiceRepository.saveBatch(newInvoiceList);
    }


}
