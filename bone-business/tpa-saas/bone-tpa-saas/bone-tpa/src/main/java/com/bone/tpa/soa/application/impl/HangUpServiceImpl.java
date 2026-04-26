package com.bone.tpa.soa.application.impl;

import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.enums.HangUpStatus;
import com.bone.tpa.api.request.ReleaseHandUpRequest;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.claim.sync.ClaimSyncFromTpaService;
import com.bone.tpa.sdk.claim.enums.TpaHangupReason;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.service.TimeLogService;
import com.bone.tpa.soa.application.HangUpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

@Service
public class HangUpServiceImpl implements HangUpService {
    @Autowired
    private  ClaimRepository claimRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private  ClaimSyncFromTpaService claimSyncFromTpaService;

    @Autowired
    private TimeLogService timeLogService;

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public Map<String, Object> releastHangUp(ReleaseHandUpRequest request) {
        Assert.notNull(request.getClaimNumber(),"claimNumber is null");
        Assert.notNull(request.getHandUpType(),"handUpType is null");
        Assert.notNull(request.getDealedStatus(),"dealedStatus is null");
        HangUpStatus hangUpStatus = HangUpStatus.getByDesc(request.getDealedStatus());
        if( hangUpStatus == null){
            throw new IllegalArgumentException("dealedStatus is error" + request.getDealedStatus());
        }
        Claim claim =  claimRepository.findById(request.getClaimNumber());
        if( claim == null){
            throw new IllegalArgumentException("赔案不存在");
        }
        Map<String, Object> rs = new HashMap<>();
        String hangupStatus =  claim.getHangUpStatus();
        if(!HangUpStatus.HANG_UP.getCode().equals(hangupStatus)){
            //不是挂起状态，不用解除挂起
            return  rs;
        }
        TpaHangupReason reasonType =   TpaHangupReason.getByCode(request.getHandUpType());
        if(hangUpStatus ==HangUpStatus.DEALT &&TpaHangupReason.资料不齐挂给客户 ==reasonType){
            // 如果挂起类型是： 客户上传图片，则需要更新影像件
            //todo: 更新影像件
            ClaimDetailSyncVO claimInfo =  request.getClaimInfo();
            if( claimInfo == null){
                throw new IllegalArgumentException("赔案信息不能为空");
            }
             if(claimInfo.getImageList()== null){
                throw new IllegalArgumentException("影像件不能为空");
            }
             //清空影像件绑定
            claimInfo.setImageBindList(PkListUtil.newArrayList());
           /* if(claimInfo.getImageBindList() == null){
                throw new IllegalArgumentException("影像件绑定不能为空");
            }*/
            claimSyncFromTpaService.saveSyncImage(claim.getBizIdentityCode(),request.getClaimInfo());
        }
        claim.setHangUpType("");
        claim.setBizIdentityCode(claim.getBizIdentityCode());
        claim.setHangUpStatus(hangUpStatus.getCode());
        claimService.updateClaim(claim, false, "解挂");

        timeLogService.addTimeLog(claim, OperationTypeEnum.RELEASE_HANGUP, "TPA");
        return rs;
    }
}
