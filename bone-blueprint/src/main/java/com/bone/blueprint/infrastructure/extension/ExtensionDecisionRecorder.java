package com.bone.blueprint.infrastructure.extension;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ExtensionDecisionRecorder {
    
    private final List<ExtensionDecision> decisions = new ArrayList<>();
    private final ConcurrentHashMap<String, List<ExtensionDecision>> decisionsByPoint = new ConcurrentHashMap<>();
    
    public ExtensionDecision record(String extensionPoint, Object input, 
                                    List<ExtensionCandidate> candidates,
                                    ExtensionCandidate selected) {
        ExtensionDecision decision = new ExtensionDecision(
            extensionPoint,
            Instant.now(),
            input,
            candidates.stream()
                .map(c -> "ExtensionClass")
                .collect(Collectors.toList()),
            "SelectedExtension",
            "MatchReason"
        );
        
        decisions.add(decision);
        decisionsByPoint.computeIfAbsent(extensionPoint, k -> new ArrayList<>()).add(decision);
        
        return decision;
    }
    
    public List<ExtensionDecision> getDecisions(String extensionPoint) {
        return decisionsByPoint.getOrDefault(extensionPoint, new ArrayList<>());
    }
    
    @Value
    public static class ExtensionDecision {
        String extensionPoint;
        Instant timestamp;
        Object input;
        List<String> candidates;
        String selected;
        String reason;

        public ExtensionDecision(String extensionPoint, Instant timestamp, Object input, List<String> candidates, String selected, String reason) {
            this.extensionPoint = extensionPoint;
            this.timestamp = timestamp;
            this.input = input;
            this.candidates = candidates;
            this.selected = selected;
            this.reason = reason;
        }
    }
    
    @Value
    public static class ExtensionCandidate {
        Class<?> extensionClass;
        String matchReason;

        public ExtensionCandidate(Class<?> extensionClass, String matchReason) {
            this.extensionClass = extensionClass;
            this.matchReason = matchReason;
        }
    }
}