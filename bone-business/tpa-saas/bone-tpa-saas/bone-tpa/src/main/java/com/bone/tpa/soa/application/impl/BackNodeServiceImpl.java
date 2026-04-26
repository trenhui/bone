package com.bone.tpa.soa.application.impl;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.api.enums.BackClaimEventType;
import com.bone.tpa.api.request.BackToStatusRequest;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.soa.application.BackNodeService;
import com.bone.tpa.soa.backnodeevent.BackNodeEvnetAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BackNodeServiceImpl  implements BackNodeService {
    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private CommonLogService logService;

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public Map<String, Object> backToNode(BackToStatusRequest request) {
        BackClaimEventType eventType = BackClaimEventType.getEnum( request.getStatusEvent());
        if( eventType == null){
            throw new IllegalArgumentException("statusEvent is null");

        }
        ClaimDetailSyncVO syncVO = request.getClaimInfo();
        if( syncVO == null){
            throw new IllegalArgumentException("claimInfo is null");
        }
        Long claimNumber =  syncVO.getClaimNumber();
        if( claimNumber == null){
            throw new IllegalArgumentException("claimNumber is null");
        }
        logService.addClaimLogAsync(request.getClaimInfo().getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                "backToNode request:{}", JSONObject.toJSONString(request));

        Claim claimCheck  = claimRepository.findById(claimNumber);
        if( claimCheck == null){
            throw new IllegalArgumentException("claimNumber is not exist");
        }
        Map<String, BackNodeEvnetAction> actionMap=    SpringContextUtils.getApplicationContext().getBeansOfType(BackNodeEvnetAction.class);
        List<BackNodeEvnetAction> beanList =  actionMap.entrySet().stream().map(t->t.getValue()).collect(Collectors.toList());
        for(BackNodeEvnetAction action : beanList){
            if(action.getEvent() == eventType){
                Map<String,Object> rsMap =  action.fire(request);
                return rsMap;
            }
        }
        throw new RuntimeException("not support action");
    }
}
