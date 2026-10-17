package com.bone.aiapp.eval.application;

import com.bone.aiapp.eval.domain.model.EvalRun;
import com.bone.aiapp.eval.domain.repository.EvalRunRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 评测运行查询门面（应用层）。
 *
 * <p>存在理由：web 层不得直注 domain repository（AGENTS §一.3）——运行查询、gate 过滤与指标计算 统一收口在应用层，Controller 只依赖本服务与
 * {@code JudgePort}，租户/分页等横切关注点后续也只改这一处。
 */
@Component
public class EvalRunMetricsApplicationService {

  private final EvalRunRepository runRepository;
  private final EvalMetricsApplicationService metricsService;

  public EvalRunMetricsApplicationService(
      EvalRunRepository runRepository, EvalMetricsApplicationService metricsService) {
    this.runRepository = runRepository;
    this.metricsService = metricsService;
  }

  /** 按评测门禁聚合指标；{@code gate} 为空时统计全部运行。 */
  public EvalMetrics metricsForGate(String gate) {
    List<EvalRun> runs =
        (gate == null || gate.isBlank()) ? runRepository.findAll() : runRepository.findByGate(gate);
    return metricsService.computeFromRuns(runs);
  }
}
