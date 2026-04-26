package com.bone.tpa.claim.engine.strategy;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.api.enums.HangUpStatus;
import com.bone.tpa.claim.application.converter.ClaimConverter;
import com.bone.tpa.claim.application.dto.ClaimDTO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.claim.domain.service.ClaimStakeholderService;
import com.bone.tpa.sdk.claim.enums.*;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.claim.model.ClaimTimeLog;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.service.TimeLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.bone.tpa.sdk.constants.BizConstant.MAGIC_CLAIM_STATUS_NAME;


/**
 * 赔案信息通用处理逻辑
 *
 */
@Service
public class ClaimDetailStrategy extends DefaultStrategy {

    private final String TABLE = "ss_claim";

    @Autowired
    private ClaimConverter claimConverter;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimTrackLogService claimTrackLogService;

    @Autowired
    private ClaimStakeholderService claimStakeholderService;

    @Autowired
    private TimeLogService timeLogService;

    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.CLAIM_DETAIL;
    }

    @Override
    public List<BizModelEnum> subBizModel() {
        return List.of(BizModelEnum.OUT_INSURE_PERSON, BizModelEnum.OUT_INSURE_INFO, BizModelEnum.MAIN_INSURE_PERSON);
    }

    @Override
    public List<BizModelEnum> childBizModel() {
        return List.of(BizModelEnum.BENEFIT_PERSON, BizModelEnum.COLLECT_PERSON, BizModelEnum.COLLECT_BUSINESS, BizModelEnum.CLAIM_INVOICE);
    }

    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        Claim claim = claimRepository.findById(request.getId());

        ClaimDTO claimDTO = claimConverter.toDTO(claim);

        if (claim == null) {
            return new GenericQueryResponse(claimDTO);
        }

        //处理状态字段给前端
        StringBuilder sb = new StringBuilder();
        ClaimStatusEnum statusEnum = ClaimStatusEnum.getByCode(claim.getStatus(), claim.getStatusSub());
        if (statusEnum != null) {
            sb.append(statusEnum.getValue());
        } else {
            sb.append("未知状态");
        }
        if (claim.getHangUpStatus() != null && claim.getHangUpStatus().equals(HangUpStatus.HANG_UP.getCode())) {
            sb.append("(问题件)");
        }
        claimDTO.setStatus(sb.toString());

        claimDTO.setStage(ClaimStageEnum.getByCode(claim.getStage()).getValue());

        /**
         * 添加新展示的内容 DLXMDD-3855
         */
        ClaimTrackLog rejectLog = claimTrackLogService.queryLatestClaimRecord(claim.getId(), OperationTypeEnum.REJECT);
        ClaimTrackLog returnLog = claimTrackLogService.queryLatestClaimRecord(claim.getId(), OperationTypeEnum.RETURN_MANUAL, OperationTypeEnum.BACK_NODE);
        claimDTO.setLoginAccount(BizContextUtils.getUser());
        claimDTO.setRejectReason(Objects.isNull(rejectLog) ? null : rejectLog.getRemark());
        claimDTO.setReturnReason(Objects.isNull(returnLog) ? null : returnLog.getRemark());
        if (org.apache.commons.lang3.StringUtils.equals(claim.getStatus(), ClaimStatusEnum.COMPLETE_AUDIT.getCode()) || org.apache.commons.lang3.StringUtils.equals(claim.getStatus(), ClaimStatusEnum.Cancel.getCode())) {
            claimDTO.setCurrentOperatorName(MAGIC_CLAIM_STATUS_NAME);
        } else {
            claimDTO.setCurrentOperatorName(claim.getOperatorUserName());
        }
        claimDTO.setOperatorNames(Arrays.asList(claim.getPreExamOperatorName(), claim.getSubmittingOperatorName(),
                claim.getInspectionOperatorName(), claim.getAuditingOperatorName(), claim.getReviewingOperatorName()));
        claimDTO.setLimitHour(claim.getLimitHour());
        claimDTO.setHangUpTotalHour(timeLogService.timePeriodCalculator(claim, OperationTypeEnum.HANGUP, OperationTypeEnum.RELEASE_HANGUP));

        return new GenericQueryResponse(claimDTO);
    }

    @Override
    public PageResult<ClaimDTO> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        PageResult<Claim> result = claimRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

        List<Claim> claimList = result.getData();

        List<Long> claimIdList = claimList.stream().map(Claim::getId).toList();

        //如果没查到这里直接返回吧
        if (claimIdList.isEmpty()) {
            return new PageResult<>(new ArrayList<>(), result.getCurrPage(), result.getPageSize(), result.getTotalCount());
        }

        List<ClaimStakeholder> stakeholderList = claimStakeholderService.getByClaimIdList(claimIdList, List.of(PersonTypeEnum.OUT_INSURE.getCode(), PersonTypeEnum.MAIN_INSURE.getCode()));

        Map<Long, ClaimStakeholder> outInsureMap = stakeholderList.stream().filter(t -> t.getPersonType().equals(PersonTypeEnum.OUT_INSURE.getCode())).collect(Collectors.toMap(ClaimStakeholder::getRelatedId, t -> t));
        Map<Long, ClaimStakeholder> mainInsureMap = stakeholderList.stream().filter(t -> t.getPersonType().equals(PersonTypeEnum.MAIN_INSURE.getCode())).collect(Collectors.toMap(ClaimStakeholder::getRelatedId, t -> t));

        List<ClaimDTO> claimDTOList = new ArrayList<>();
        for (Claim claim : claimList) {
            ClaimDTO claimDTO = claimConverter.toDTO(claim);

            /**
             * 针对特殊字段做出处理
             */
            if (!HangUpStatus.HANG_UP.getCode().equals(claim.getHangUpStatus())) {
                claimDTO.setClaimStatus(ClaimStatusEnum.getByCode(claim.getStatus(), claim.getStatusSub()).getValue());
            } else {
                claimDTO.setClaimStatus(ClaimStatusEnum.getByCode(claim.getStatus(), claim.getStatusSub()).getValue() + "-问题件");
            }

            if (claim.getStage().equals(ClaimStageEnum.PRE_EXAM.getCode()) || claim.getPreExamPassTime() != null) {
                claimDTO.setPreExamStartTime(claim.getSignPassTime());
            }
            claimDTO.setOutInsureName(outInsureMap.containsKey(claim.getId()) ? outInsureMap.get(claim.getId()).getName() : null);
            claimDTO.setMainInsureName(mainInsureMap.containsKey(claim.getId()) ? mainInsureMap.get(claim.getId()).getName() : null);

            claimDTOList.add(claimDTO);
        }

        return new PageResult<>(claimDTOList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
        //禁止从这里更新
        throw new TpaBizException(BizErrorCode.DATA_UNCHANGEABLE);
    }

    @Override
    public void delete(DeleteRequest request) {
        claimRepository.deleteByIds(request.getIdList());

        for (Long id : request.getIdList()) {
            claimTrackLogService.deleteRecord(id, id, BizModelEnum.CLAIM_DETAIL);
        }
    }

}
