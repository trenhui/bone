package com.bone.tpa.soa.application;

import com.bone.tpa.api.request.ReleaseHandUpRequest;

import java.util.Map;

public interface HangUpService {

    /**
     * 解挂
     * @param request
     * @return
     */
    Map<String,Object> releastHangUp(ReleaseHandUpRequest request);


}
