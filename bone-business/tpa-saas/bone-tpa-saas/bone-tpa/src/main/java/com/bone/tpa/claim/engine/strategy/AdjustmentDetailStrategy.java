package com.bone.tpa.claim.engine.strategy;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.intelligent.adjustment.converter.AdjustmentRecordConvert;
import com.bone.tpa.sdk.dao.impl.AdjustmentRecordRepository;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.adjustment.response.InvoiceAdjustmentResponse;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


/**
 * 理算详情信息通用处理逻辑
 *
 */
@Service
public class AdjustmentDetailStrategy extends DefaultStrategy {

    private final String TABLE = "ia_adjustment_record";

    @Autowired
    private AdjustmentRecordRepository adjustmentRecordRepository;

    @Autowired
    private AdjustmentRecordConvert adjustmentRecordConvert;

    @Autowired
    private ClaimService claimService;


    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.ADJUSTMENT_DETAIL;
    }


    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        AdjustmentRecord adjustmentRecord = adjustmentRecordRepository.findById(request.getId());

        GenericQueryResponse response = new GenericQueryResponse(adjustmentRecordConvert.toDto(adjustmentRecord));
        response.setTenantId(String.valueOf(adjustmentRecord.getTenantId()));
        response.setBizIdentityCode(adjustmentRecord.getBizIdentityCode());

        return response;
    }

    @Override
    public PageResult<InvoiceAdjustmentResponse> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        Boolean flag = false;
        for (SortingField sortingField : sortingFields) {
            if (sortingField.getField().equals("id")) {
                flag = true;
            }
        }
        if (!flag) {
            sortingFields.add(new SortingField("id", "asc"));
        }

        PageResult<AdjustmentRecord> result = adjustmentRecordRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        List<AdjustmentRecord> adjustmentRecordList = result.getData();
        List<InvoiceAdjustmentResponse> responseList = new ArrayList<>();
        for (AdjustmentRecord adjustmentRecord : adjustmentRecordList) {
            InvoiceAdjustmentResponse response = adjustmentRecordConvert.toDto(adjustmentRecord);
            responseList.add(response);
        }

        return new PageResult<>(responseList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
        AdjustmentRecord adjustmentRecord = convertToEntity(mapper.convertValue(data, InvoiceAdjustmentResponse.class));

        Claim claim = claimService.getById(adjustmentRecord.getRelatedId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        adjustmentRecordRepository.save(adjustmentRecord);
    }

    @Override
    public void delete(DeleteRequest request) {
        throw new TpaBizException(BizErrorCode.DATA_UNCHANGEABLE);
    }


    private AdjustmentRecord convertToEntity(InvoiceAdjustmentResponse response) {
        AdjustmentRecord adjustmentRecord = adjustmentRecordRepository.findById(response.getId());

        if (adjustmentRecord == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "理算记录不存在: " + response.getId());
        }

        adjustmentRecord.setResultDetail(response.getResultDetail());
        adjustmentRecord.setInvoiceResult(response.getInvoiceResult());

        return adjustmentRecord;
    }
}
