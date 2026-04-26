package com.bone.tpa.soa.application;

import com.bone.tpa.api.request.BackToStatusRequest;

import java.util.Map;

public interface BackNodeService {

    Map<String,Object> backToNode(BackToStatusRequest request);
}
