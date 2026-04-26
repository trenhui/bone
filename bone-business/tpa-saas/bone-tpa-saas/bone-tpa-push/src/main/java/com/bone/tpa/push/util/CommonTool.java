package com.bone.tpa.push.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.BizContextUtils;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import com.bone.tpa.sdk.claim.model.CommonLog;
import com.bone.tpa.sdk.dao.ClaimTrackLogRepository;
import com.bone.tpa.sdk.dao.CommonLogRespository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.util.Date;
import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/10/22 19:30
 */
@Slf4j
public class CommonTool {
    public static List<String> getRegionCn(String region, String keyName){
        if(StringUtils.isBlank(region)){
            region = "{}";
        }
        List<String> rs = PkListUtil.newArrayList();

        try {
            JSONObject jsonObject = JSONObject.parseObject(region);
            JSONArray jsonArray =  jsonObject.getJSONArray(keyName);

            try {
                String provinceName = jsonArray.getString(0);
                rs.add(provinceName);
            } catch (Exception e) {
                rs.add("");
            }
            try {
                String city = jsonArray.getString(1);
                rs.add(city);
            } catch (Exception e) {
                rs.add("");
            }

            try {
                String area = jsonArray.getString(2);
                rs.add(area);
            } catch (Exception e) {
                rs.add("");
            }
        } catch (Exception e) {
            rs.add("");
            rs.add("");
            rs.add("");
        }
        return rs;

    }


    public static void insertTrackLog(ClaimTrackLogRepository claimTrackLogRepository, Claim claim, Claim curClaim, String message, OperationTypeEnum operationTypeEnum, String remark) {
        ClaimTrackLog claimTrackLog= new ClaimTrackLog();
        claimTrackLog.setTenantId(claim.getTenantId());
        claimTrackLog.setRelatedClaimId(claim.getId());
        claimTrackLog.setRelatedClaimNo(claim.getClaimNo());
        claimTrackLog.setStage(claim.getStage());
        claimTrackLog.setType(operationTypeEnum.getCode());
        claimTrackLog.setBeforeValue(JSON.toJSONString(claim));
        claimTrackLog.setAfterValue(JSON.toJSONString(curClaim));
        claimTrackLog.setMessage(message);
        claimTrackLog.setOperator(BizContextUtils.getUser());
        claimTrackLog.setRemark(remark);
        claimTrackLogRepository.insert(claimTrackLog);
    }

    public static void addClaimLog(CommonLogRespository logRespository, Long claimId, String logType, String msg){
        String traceId = MDC.get("traceId");
        log.info(msg);
        try {
            CommonLog dto = new CommonLog();
            dto.setObjectId(claimId.toString());
            dto.setRemark(msg);
            dto.setBizType(logType);
            dto.setCreateTime(new Date());
            dto.setTraceId(traceId);
            logRespository.insert(dto);
        } catch (Exception e) {
            log.error("addClaimLog error ",e);
        }
    }
}
