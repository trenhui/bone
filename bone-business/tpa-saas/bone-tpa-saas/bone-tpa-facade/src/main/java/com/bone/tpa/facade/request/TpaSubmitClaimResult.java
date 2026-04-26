package com.bone.tpa.facade.request;

import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import lombok.Data;

import java.util.List;

@Data
public class TpaSubmitClaimResult {

    private Long claimNumber;

    private Long oldClaimNumber;

    private String mockTag ;

    private Integer eventType ;
    /**
     * 操作时间
     */
    private Long eventTime = System.currentTimeMillis();

    private ClaimDetailSyncVO claimInfo;


    private EventOtherRequest eventOtherRequest;




}
