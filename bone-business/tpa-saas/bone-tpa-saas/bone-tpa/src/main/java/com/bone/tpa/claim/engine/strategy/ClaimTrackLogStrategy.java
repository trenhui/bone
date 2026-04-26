package com.bone.tpa.claim.engine.strategy;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.tpa.claim.application.converter.ClaimTrackLogConverter;
import com.bone.tpa.claim.application.dto.ClaimTrackLogDTO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.sdk.dao.ClaimTrackLogRepository;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


/**
 * 发票信息通用处理逻辑
 *
 */
@Service
public class ClaimTrackLogStrategy extends DefaultStrategy {

    private final String TABLE = "ss_claim_track_log";

    @Autowired
    private ClaimTrackLogConverter trackLogConverter;

    @Autowired
    private ClaimTrackLogRepository trackLogRepository;


    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.CLAIM_TRACK_LOG;
    }

    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        ClaimTrackLog trackLog = trackLogRepository.findById(request.getId());

        ClaimTrackLogDTO trackLogDTO = trackLogConverter.toDTO(trackLog);

        return new GenericQueryResponse(trackLogDTO);
    }

    @Override
    public PageResult<ClaimTrackLogDTO> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        PageResult<ClaimTrackLog> result = trackLogRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        List<ClaimTrackLog> trackLogList = result.getData();
        List<ClaimTrackLogDTO> trackLogDTOList = new ArrayList<>();
        for (ClaimTrackLog trackLog : trackLogList) {
            ClaimTrackLogDTO invoiceDTO = trackLogConverter.toDTO(trackLog);
            trackLogDTOList.add(invoiceDTO);
        }

        return new PageResult<>(trackLogDTOList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
        //不准更新
        throw new TpaBizException(BizErrorCode.DATA_UNCHANGEABLE);
    }

    @Override
    public void delete(DeleteRequest request) {
        throw new TpaBizException(BizErrorCode.DATA_UNCHANGEABLE);
    }

}
