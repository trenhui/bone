package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RrfFuserTest {

  @Test
  void fusion_ranks_by_combined_reciprocal_rank() {
    // a 在两路均排第 1，b 均排第 2 ⇒ a 融合分更高且排第一
    Map<String, Double> v = new LinkedHashMap<>();
    v.put("a", 0.9);
    v.put("b", 0.1);
    Map<String, Double> bm = new LinkedHashMap<>();
    bm.put("a", 0.9);
    bm.put("b", 0.1);

    Map<String, Double> fused = RrfFuser.fuse(v, bm, 60, 0.5, 0.5);

    assertThat(fused.keySet()).containsExactly("a", "b");
    assertThat(fused.get("a")).isGreaterThan(fused.get("b"));
  }

  @Test
  void dual_path_beats_single_path() {
    Map<String, Double> v = new LinkedHashMap<>();
    v.put("x", 0.5);
    Map<String, Double> bm = new LinkedHashMap<>();
    bm.put("x", 0.5);

    double both = RrfFuser.fuse(v, bm, 60, 0.5, 0.5).get("x");
    double single = RrfFuser.fuse(v, Map.of(), 60, 0.5, 0.5).get("x");
    assertThat(both).isGreaterThan(single);
  }
}
