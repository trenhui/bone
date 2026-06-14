package com.bone.engine.extension.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;

@Component
public class ExtensionDecisionRecorder {

  private final List<ExtensionDecision> decisions = new ArrayList<>();
  private final ConcurrentHashMap<String, List<ExtensionDecision>> decisionsByPoint =
      new ConcurrentHashMap<>();

  public ExtensionDecision record(
      String extensionPoint,
      Object input,
      List<ExtensionCandidate> candidates,
      ExtensionCandidate selected) {
    ExtensionDecision decision =
        ExtensionDecision.builder()
            .extensionPoint(extensionPoint)
            .timestamp(Instant.now())
            .input(input)
            .candidates(
                candidates.stream()
                    .map(c -> c.getExtensionClass().getSimpleName())
                    .collect(Collectors.toList()))
            .selected(selected.getExtensionClass().getSimpleName())
            .reason(selected.getMatchReason())
            .build();
    decisions.add(decision);
    decisionsByPoint.computeIfAbsent(extensionPoint, k -> new ArrayList<>()).add(decision);
    return decision;
  }

  public List<ExtensionDecision> getDecisions(String extensionPoint) {
    return decisionsByPoint.getOrDefault(extensionPoint, new ArrayList<>());
  }

  @Value
  @Builder
  public static class ExtensionDecision {
    String extensionPoint;
    Instant timestamp;
    Object input;
    List<String> candidates;
    String selected;
    String reason;
  }

  @Value
  @Builder
  public static class ExtensionCandidate {
    Class<?> extensionClass;
    String matchReason;
  }
}
