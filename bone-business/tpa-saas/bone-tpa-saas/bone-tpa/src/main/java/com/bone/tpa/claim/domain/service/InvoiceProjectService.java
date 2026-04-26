package com.bone.tpa.claim.domain.service;

import com.bone.core.result.QueryParam;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.sdk.dao.InvoiceProjectRepository;
import com.bone.tpa.sdk.claim.model.InvoiceProject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * ss_invoice_project Service 接口
 *
 * @author 0
 */
@Service
@Slf4j
public class InvoiceProjectService {
    @Autowired
    private InvoiceProjectRepository invoiceProjectRepository;


    private static final String tableName = "ss_invoice_project";
    private static final String appCode = "tpa";


    /**
     * 查询发票下面的所有费用项目概况
     *
     * @param request   列表查询请求
     * @return
     */
    public List<InvoiceProject> getInvoiceProjects(QueryListRequest request) {
        request.getQueryParams().add(new QueryParam("relatedId", request.getId()));
        request.getQueryParams().add(new QueryParam("tenantId", request.getTenantId()));

        return invoiceProjectRepository.queryByCondition(request.getQueryParams(), request.getSortingFields(), (request.getPageNo() - 1) * request.getPageSize(), request.getPageSize(),
                                            "ss_invoice_project", request.getBizIdentityCode()).getData();
    }

}
