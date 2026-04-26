package com.bone.tpa.intelligent.adjustment.model;

import com.bone.tpa.sdk.adjustment.request.ClaimAdjustmentRequest;
import com.bone.tpa.sdk.adjustment.response.ClaimAdjustmentResponse;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ClaimProcessedEvent extends ApplicationEvent {
    // Getters and Setters
    private final ClaimAdjustmentRequest request;
    private final ClaimAdjustmentResponse response;

    public ClaimProcessedEvent(ClaimAdjustmentRequest request, ClaimAdjustmentResponse response) {
        super(request);
        this.request = request;
        this.response = response;
    }
}
