package com.bone.tpa.claim.domain.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.sdk.dao.InvoiceProjectItemRepository;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.InvoiceProjectItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * ss_invoice_project_item Service 接口
 *
 * @author 0
 */
@Service
@Slf4j
public class InvoiceProjectItemService {
    @Autowired
    private InvoiceProjectItemRepository invoiceProjectItemRepository;

    private static final String tableName = "ss_invoice_project_item";
    private static final String appCode = "tpa";


    /**
     * 查询发票下面的所有费用项目详情
     *
     * @param request   列表查询请求
     * @return
     */
    public List<InvoiceProjectItem> getInvoiceProjectItems(QueryListRequest request) {
        Criteria<InvoiceProjectItem> criteria = new Criteria<>();

        criteria.eq(InvoiceProjectItem::getRelatedId, request.getId())
                .eq(InvoiceProjectItem::getBizIdentityCode, request.getBizIdentityCode())
                .eq(InvoiceProjectItem::getTenantId, request.getTenantId());

        criteria.page(request.getPageSize(), request.getPageNo());

        return invoiceProjectItemRepository.findByCriteria(criteria);
    }


    /**
     * 根据关联id获取费用项目详情
     *
     * @param
     * @return
     */
    public List<InvoiceProjectItem> getByInvoiceId(List<Long> invoiceIdList) {
        Criteria<InvoiceProjectItem> criteria = Criteria.create();
        criteria.in(InvoiceProjectItem::getRelatedId, invoiceIdList);

        return invoiceProjectItemRepository.findByCriteria(criteria);
    }


    /**
     * 检查特定发票底下的费用明细的某些必备字段
     * 目前：费用项目名称
     *
     * @param invoice
     */
    public void checkProject(ClaimInvoice invoice) {
        //检查item的项目名称字段是不是是有的
        QueryListRequest getListRequest = new QueryListRequest();
        getListRequest.setId(String.valueOf(invoice.getId()));
        getListRequest.setBizIdentityCode(invoice.getBizIdentityCode());
        getListRequest.setTenantId(String.valueOf(invoice.getTenantId()));

        List<InvoiceProjectItem> itemList = getInvoiceProjectItems(getListRequest);

        for (InvoiceProjectItem item : itemList) {
            if (item.getRelatedProjectName() == null) {
                throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, "项目明细的关联费用项目为空：" + item.getItemName());
            }
        }
    }

    public void deleteItem(List<Long> itemIdList) {
        invoiceProjectItemRepository.deleteByIds(itemIdList);
    }

}
