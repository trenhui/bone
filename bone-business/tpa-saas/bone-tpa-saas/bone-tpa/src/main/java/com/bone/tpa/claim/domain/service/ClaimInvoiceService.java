package com.bone.tpa.claim.domain.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.api.enums.MedicalCostTypeEnum;
import com.bone.tpa.claim.application.request.InvoiceUpdateRequest;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.dao.ClaimInvoiceRepository;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.InvoiceUpdateFieldEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.InvoiceProjectItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * ss_claim_invoice Service 接口
 *
 * @author 0
 */
@Service
@Slf4j
public class ClaimInvoiceService {
    @Autowired
    private ClaimInvoiceRepository claimInvoiceRepository;

    @Autowired
    private InvoiceProjectItemService itemService;

    private static final String tableName = "ss_claim_invoice";
    private static final String appCode = "tpa";


    /**
     * 查询特定赔案关联的发票列表
     *
     * @param request   列表查询请求
     * @return
     */
    public List<ClaimInvoice> getClaimInvoices(QueryListRequest request) {
        Criteria<ClaimInvoice> criteria = Criteria.create();
        criteria.eq(ClaimInvoice::getRelatedId, request.getId())
                .eq(ClaimInvoice::getBizIdentityCode, request.getBizIdentityCode())
                .eq(ClaimInvoice::getTenantId, request.getTenantId());

        criteria.page(request.getPageSize(), request.getPageNo());

        return claimInvoiceRepository.findByCriteria(criteria);
    }


    public List<ClaimInvoice> getClaimInvoicesByClaimNumber(Long claimNumber) {
        Assert.notNull(claimNumber,"claimNumber is null");
        Criteria<ClaimInvoice> criteria = Criteria.create();
        criteria.eq(ClaimInvoice::getRelatedId,claimNumber);
        return claimInvoiceRepository.findByCriteria(criteria);
    }


    public void insertBatch(List<ClaimInvoice> claimInvoiceList) {
        claimInvoiceRepository.insertBatch(claimInvoiceList);
    }

    public ClaimInvoice getById(Long invoiceId) {
        return claimInvoiceRepository.findById(invoiceId);
    }

    public List<ClaimInvoice> updateInvoiceField(InvoiceUpdateRequest request) {
        //0、检查要更新的类型是否已定义
        InvoiceUpdateFieldEnum updateField = InvoiceUpdateFieldEnum.getByCode(request.getUpdateType());
        if (updateField == null) {
            log.error("Update type not found: {}", request.getUpdateType());
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR ,"Update type not found: " + request.getUpdateType());
        }

        //1、查询需要更改的全部发票
        Criteria<ClaimInvoice> criteria = Criteria.create();
        criteria.in(ClaimInvoice::getRelatedId, request.getIdList());

        List<ClaimInvoice> claimInvoiceList = claimInvoiceRepository.findByCriteria(criteria);

        //根据需要更改的字段，更新这些字段
        switch (updateField) {
            case RESPONSIBILITY:
                break;
            case HOSPITAL:
                break;
            case DISEASE:
                break;
            default:
        }


        //2、调用更新对应值的扩展点
        //ClaimInvoice invoiceWithId = claimPersistenceExt.invoicePersistence(invoice);


        //3、增加操作记录
        //claimTrackLogService.claimInvoiceRecord(claimInvoiceList, OperationTypeEnum.UPDATE);

        return null;
    }

    /**
     * 计算合理金额
     * @param claimInvoice
     */
    public void updateValidMoney(ClaimInvoice claimInvoice){
        try{
            if( claimInvoice == null){
                return;
            }
            BigDecimal total = claimInvoice.getTotalAmount();
            BigDecimal rs = total;
            if( total == null){
                claimInvoice.setValidAmount(BigDecimal.ZERO);
                return;
            }
            //totalMedicalFundPayment
            //医保基金总直付
            BigDecimal totalMedicalFundPayment = claimInvoice.getTotalMedicalFundPayment();
            if( totalMedicalFundPayment == null){
                totalMedicalFundPayment = BigDecimal.ZERO;
            }
            rs = rs.subtract(totalMedicalFundPayment);
            if(rs.compareTo(BigDecimal.ZERO) < 0) { //负数
                claimInvoice.setValidAmount(BigDecimal.ZERO);
                return;
            }
            //自付二selfPayPart2Amount
            BigDecimal selfPayPart2Amount = claimInvoice.getSelfPayPart2Amount();
            if( selfPayPart2Amount == null){
                selfPayPart2Amount = BigDecimal.ZERO;
            }
            rs = rs.subtract(selfPayPart2Amount);
            if(rs.compareTo(BigDecimal.ZERO) < 0) { //负数
                claimInvoice.setValidAmount(BigDecimal.ZERO);
                return;
            }
            //totalSelfPayAmount 总自费金额
            BigDecimal totalSelfPayAmount = claimInvoice.getTotalSelfPayAmount();
            if( totalSelfPayAmount == null){
                totalSelfPayAmount = BigDecimal.ZERO;
            }
            rs = rs.subtract(totalSelfPayAmount);
            if(rs.compareTo(BigDecimal.ZERO) < 0) { //负数
                claimInvoice.setValidAmount(BigDecimal.ZERO);
                return;
            }
            //thirdPartyPaidAmount 第三方支付金额
            BigDecimal thirdPartyPaidAmount = claimInvoice.getThirdPartyPaidAmount();
            if( thirdPartyPaidAmount == null){
                thirdPartyPaidAmount = BigDecimal.ZERO;
            }
            rs = rs.subtract(thirdPartyPaidAmount);
            if(rs.compareTo(BigDecimal.ZERO) < 0) { //负数
                claimInvoice.setValidAmount(BigDecimal.ZERO);
                return;
            }
            //invalidAmount 不合理金额
            BigDecimal invalidAmount = claimInvoice.getInvalidAmount();
            if( invalidAmount == null){
                invalidAmount = BigDecimal.ZERO;
            }
            rs = rs.subtract(invalidAmount);
            if(rs.compareTo(BigDecimal.ZERO) < 0) { //负数
                claimInvoice.setValidAmount(BigDecimal.ZERO);
                return;
            }
            claimInvoice.setValidAmount(rs);
        }catch (Exception e){
            log.error("updateValidMoney error",e);
        }

    }

    /**
     * 通过项目明细聚合和更新发票的金额字段
     *
     * @param
     */
    public ClaimInvoice updateInvoiceMoney(ClaimInvoice claimInvoice, List<InvoiceProjectItem> itemList) {

        if (itemList == null || itemList.isEmpty()) {
            return claimInvoice;
        }
        //发票总金额
        claimInvoice.setTotalAmount(itemList.stream().map(InvoiceProjectItem::getItemTotalAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));


        //medicalType, itemList
        Map<String, List<InvoiceProjectItem>> itemMap = itemList.stream().collect(
                Collectors.groupingBy(t -> Optional.ofNullable(t.getMedicalType()).orElse("DEFAULT_KEY")));

        //甲类, 对应医保统筹
        List<InvoiceProjectItem> jiaItemList = itemMap.get(MedicalCostTypeEnum.甲类.getCode());
        if (jiaItemList != null && !jiaItemList.isEmpty()) {
            BigDecimal totalAmount = jiaItemList.stream().map(InvoiceProjectItem::getItemTotalAmount)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            claimInvoice.setBasicPoolingAmount(totalAmount);
        }

        //乙类, 对应自付二
        List<InvoiceProjectItem> yiItemList = itemMap.get(MedicalCostTypeEnum.乙类.getCode());
        if (yiItemList != null && !yiItemList.isEmpty()) {
            BigDecimal totalAmount = yiItemList.stream().map(InvoiceProjectItem::getItemTotalAmount)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            claimInvoice.setSelfPayPart2Amount(totalAmount);
        }

        //丙类, 对应丙类自费（完全自费项目）
        List<InvoiceProjectItem> bingItemList = itemMap.get(MedicalCostTypeEnum.丙类.getCode());
        if (bingItemList != null && !bingItemList.isEmpty()) {
            BigDecimal totalAmount = bingItemList.stream().map(InvoiceProjectItem::getItemTotalAmount)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            claimInvoice.setClassCSelfPayAmount(totalAmount);
        }

        return claimInvoice;
    }


    /**
     * 检查该赔案底下的发票的每一个费用明细的所有必填字段。
     * 也可以检查发票的所有必填字段
     *
     * @param claim
     */
    public void checkProjectName(Claim claim) {
        QueryListRequest queryListRequest = new QueryListRequest();
        queryListRequest.setId(String.valueOf(claim.getId()));
        queryListRequest.setTenantId(String.valueOf(claim.getTenantId()));
        queryListRequest.setBizIdentityCode(claim.getBizIdentityCode());

        List<ClaimInvoice> claimInvoiceList = getClaimInvoices(queryListRequest);

        for (ClaimInvoice invoice : claimInvoiceList) {
            itemService.checkProject(invoice);
        }
    }

}
