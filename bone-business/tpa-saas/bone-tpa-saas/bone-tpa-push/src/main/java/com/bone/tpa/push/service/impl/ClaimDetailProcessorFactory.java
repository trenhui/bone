package com.bone.tpa.push.service.impl;

import com.bone.tpa.push.service.ClaimDetailProcessor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClaimDetailProcessorFactory {

    private final List<ClaimDetailProcessor> processors;

    public ClaimDetailProcessorFactory(List<ClaimDetailProcessor> processors) {
        this.processors = processors;
    }

    public ClaimDetailProcessor getProcessor(String insuranceName, String branchName) {
        return processors.stream()
                .filter(p -> p.supports(insuranceName, branchName))
                .findFirst()
                .orElseGet(() -> processors.stream()
                        .filter(p -> p instanceof DefaultClaimDetailProcessor)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No default ClaimDetailProcessor found")));
    }
}
