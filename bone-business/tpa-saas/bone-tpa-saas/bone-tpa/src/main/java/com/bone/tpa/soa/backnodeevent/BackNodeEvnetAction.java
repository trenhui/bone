package com.bone.tpa.soa.backnodeevent;

import com.bone.tpa.api.enums.BackClaimEventType;
import com.bone.tpa.api.request.BackToStatusRequest;

import java.util.Map;

public interface BackNodeEvnetAction {
    BackClaimEventType getEvent();

    Map<String,Object> fire(BackToStatusRequest request);
}
