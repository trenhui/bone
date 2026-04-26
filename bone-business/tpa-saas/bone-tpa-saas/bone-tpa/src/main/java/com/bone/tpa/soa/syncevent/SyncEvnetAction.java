package com.bone.tpa.soa.syncevent;

import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.request.SyncClaimWithEventRequest;

import java.util.Map;

public interface SyncEvnetAction {
    EventType getEvent();

    Map<String,Object> fire(SyncClaimWithEventRequest request);
}
