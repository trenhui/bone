// @vitest-environment node
import { describe, it, expect } from 'vitest';
import {
  CALIBRATION_VERDICTS,
  ROLLOUT_STAGES,
  calibrationEvidenceMeta,
  capabilityStateMeta,
  drillMeta,
  gatesMeta,
  governanceWindow,
  metricText,
  participationMeta,
  probeMeta,
  rolloutVerdictMeta,
  stageMeta,
  verdictMeta,
  type AiCalibrationReport,
  type ProviderProbeReport,
} from './aiGovernance';

const probeReport = (over: Partial<ProviderProbeReport> = {}): ProviderProbeReport => ({
  verdict: 'NOT_CONFIGURED', configured: false, reachable: false,
  model: null, provenance: null, latencyMs: 0, reason: null,
  action: '设置 BONE_AI_GATEWAY_BASE_URL 后重启', note: '只证明可达，不证明准确',
  ...over,
});

const calibrationReport = (
  verdict: string,
  buckets: unknown[] = [],
  slices: unknown[] = [],
): AiCalibrationReport => ({
  requestedLimit: 5000, fetchedTasks: 0, sampleCount: 0, unparseableSamples: 0,
  calibration: {
    sampleCount: 0, bucketCount: 0, expectedCalibrationError: null, brierScore: null,
    currentThreshold: null, recommendedThreshold: null, coveredByRecommended: 0,
    verdict, basis: '', buckets, slices,
  } as AiCalibrationReport['calibration'],
  effectiveMinSamples: 300, effectiveMinSamplesPerSlice: 30,
});

describe('gatesMeta 三态', () => {
  it('false 显示未达标，不得出现达标字样', () => {
    const meta = gatesMeta(false);
    expect(meta.label).toBe('未达标');
    expect(meta.tone).toBe('error');
    expect(meta.label).not.toContain('已达标');
  });

  it('负向探针：null/undefined 显示未测过，不得压平成 false 或 0', () => {
    for (const v of [null, undefined]) {
      const meta = gatesMeta(v);
      expect(meta.label).toBe('未测过');
      expect(meta.tone).toBe('warning');
      expect(meta.detail).toContain('不得按达标或 0 处理');
    }
    expect(gatesMeta(null).label).not.toBe(gatesMeta(false).label);
  });

  it('true 才是已达标', () => {
    expect(gatesMeta(true).label).toBe('已达标');
  });
});

describe('metricText 与 participationMeta 不吞 0、不造 0', () => {
  it('0 必须显示为 0', () => {
    expect(metricText(0)).toBe('0');
    expect(metricText('0')).toBe('0');
  });

  it('null/undefined/空串显示无数据占位符，不显示为 0', () => {
    for (const v of [null, undefined, '']) {
      const text = metricText(v);
      expect(text).toContain('无数据');
      expect(text).not.toContain('0');
    }
  });

  it('负向探针：不可解析值显示原始值，不静默变成 0', () => {
    expect(metricText('not-a-number')).toContain('not-a-number');
    expect(metricText('not-a-number')).not.toBe('0');
  });

  it('participationRate=null 显示无可计算样本，不得写成 0%', () => {
    const meta = participationMeta(null);
    expect(meta.label).toBe('无可计算样本');
    expect(meta.detail).toContain('不得写成 0%');
    expect(meta.label).not.toContain('0%');
  });

  it('负向探针：participationRate=0 显示模型未参与，不写成已上场', () => {
    const meta = participationMeta(0);
    expect(meta.label).toBe('模型未参与');
    expect(meta.tone).toBe('error');
    expect(meta.detail).toContain('不得据此判定 AI 已参与决策');
    expect(meta.label).not.toBe(participationMeta(null).label);
  });

  it('正常参与率按百分比展示', () => {
    expect(participationMeta(0.125).label).toBe('12.50%');
    expect(participationMeta('0.5').label).toBe('50.00%');
  });

  it('负向探针：越界值不被乐观映射', () => {
    expect(participationMeta(1.5).tone).toBe('error');
    expect(participationMeta(-0.1).label).toContain('越界值');
  });
});

describe('verdictMeta 六值受控字典', () => {
  it('与后端 E2E 使用的六值清单完全一致', () => {
    expect([...CALIBRATION_VERDICTS]).toEqual([
      'NO_EVIDENCE',
      'UNTRUSTED_EVIDENCE',
      'INSUFFICIENT_SAMPLES',
      'SLICE_INCOMPLETE',
      'NO_SAFE_THRESHOLD',
      'THRESHOLD_PROPOSED',
    ]);
  });

  it('每个受控码都有映射且 THRESHOLD_PROPOSED 不宣称已生效', () => {
    for (const code of CALIBRATION_VERDICTS) {
      expect(verdictMeta(code).label).not.toContain('未登记');
    }
    expect(verdictMeta('THRESHOLD_PROPOSED').detail).toContain('批准后才生效');
  });

  it('负向探针：未知结论码显示原始码并告警，绝不映射成已校准', () => {
    const meta = verdictMeta('SOME_NEW_VERDICT');
    expect(meta.label).toContain('SOME_NEW_VERDICT');
    expect(meta.tone).toBe('warning');
    expect(meta.detail).toContain('不得按已校准处理');
    expect(meta.label).not.toBe(verdictMeta('THRESHOLD_PROPOSED').label);
  });

  it('NO_EVIDENCE 与 UNTRUSTED_EVIDENCE 不得写成已校准', () => {
    expect(verdictMeta('NO_EVIDENCE').detail).toContain('不得宣称模型已校准');
    expect(verdictMeta('UNTRUSTED_EVIDENCE').detail).toContain('整批拒绝');
  });
});

describe('stageMeta 档位语义', () => {
  it('与后端 AiRolloutStage 四值一致', () => {
    expect([...ROLLOUT_STAGES]).toEqual(['SHADOW', 'ASSISTED', 'CANARY', 'AUTOMATED']);
  });

  it('负向探针：SHADOW 不得写成已自动放款', () => {
    const meta = stageMeta('SHADOW');
    expect(meta.label).toBe('影子模式');
    expect(meta.detail).toContain('不自动放款');
    expect(meta.label).not.toContain('自动放款');
    expect(stageMeta('ASSISTED').detail).toContain('不自动放款');
  });

  it('负向探针：未知档位按最保守口径，不假定可自动出金', () => {
    const meta = stageMeta('TURBO');
    expect(meta.label).toContain('TURBO');
    expect(meta.detail).toContain('不得假定可自动出金');
    expect(meta.tone).toBe('warning');
  });
});

describe('drillMeta 与其余字典的未知码降级', () => {
  it('false 明确回滚后不自动出金', () => {
    expect(drillMeta(false).label).toBe('回滚后不自动出金');
    expect(drillMeta(false).tone).toBe('success');
  });

  it('负向探针：true 判红，null 不得压平成 false', () => {
    expect(drillMeta(true).tone).toBe('error');
    expect(drillMeta(null).label).toBe('未测过');
    expect(drillMeta(undefined).label).not.toBe(drillMeta(false).label);
  });

  it('能力状态与放量预算结论的未知码均降级显示原始码', () => {
    expect(capabilityStateMeta('WAT').label).toContain('WAT');
    expect(capabilityStateMeta('LOCAL_STUB').tone).toBe('error');
    expect(rolloutVerdictMeta('WAT').label).toContain('WAT');
    expect(rolloutVerdictMeta('OVER_TARGET').tone).toBe('error');
    expect(capabilityStateMeta(null).label).toContain('(空)');
  });
});

describe('probeMeta 探活判词', () => {
  it('未点击显示未测过，不得乐观映射成连通', () => {
    const meta = probeMeta(null);
    expect(meta.label).toBe('未测过');
    expect(meta.tone).toBe('warning');
    expect(meta.detail).toContain('不做自动轮询');
    expect(meta.label).not.toContain('连通');
  });

  it('负向探针：NOT_CONFIGURED 不得显示为连通', () => {
    const meta = probeMeta(probeReport());
    expect(meta.label).toBe('未配置真实 provider');
    expect(meta.label).not.toContain('连通');
    expect(meta.detail).toContain('BONE_AI_GATEWAY_BASE_URL');
  });

  it('REACHABLE 只表示可达，不得写成判断准确', () => {
    const meta = probeMeta(probeReport({ verdict: 'REACHABLE', configured: true, reachable: true }));
    expect(meta.label).toBe('链路可达');
    expect(meta.detail).toContain('不证明模型判断准确');
    expect(meta.label).not.toContain('准确');
  });

  it('负向探针：未知判词显示原始码并告警', () => {
    const meta = probeMeta(probeReport({ verdict: 'SOME_NEW_VERDICT' }));
    expect(meta.label).toContain('SOME_NEW_VERDICT');
    expect(meta.tone).toBe('warning');
    expect(meta.detail).toContain('不得按连通处理');
  });

  it('请求失败显示未测过，不吞掉错误', () => {
    const meta = probeMeta(null, 'gateway 502');
    expect(meta.label).toBe('未测过');
    expect(meta.detail).toContain('gateway 502');
  });
});

describe('calibrationEvidenceMeta 明细诚实渲染', () => {
  it('NO_EVIDENCE 且无明细时不可渲染，不画曲线', () => {
    const meta = calibrationEvidenceMeta(calibrationReport('NO_EVIDENCE'));
    expect(meta.renderable).toBe(false);
    expect(meta.label).toBe('无盲标证据');
    expect(meta.detail).toContain('不渲染');
  });

  it('负向探针：即便偷偷塞了明细，禁止态仍不得渲染', () => {
    const meta = calibrationEvidenceMeta(calibrationReport(
      'UNTRUSTED_EVIDENCE',
      [{ bucketFrom: 0, bucketTo: 1, sampleCount: 9, meanConfidence: 0.9, accuracy: 1, gap: 0.1 }],
    ));
    expect(meta.renderable).toBe(false);
    expect(meta.label).toBe('证据不可信');
  });

  it('负向探针：允许态但明细为空不得编造曲线', () => {
    const meta = calibrationEvidenceMeta(calibrationReport('THRESHOLD_PROPOSED'));
    expect(meta.renderable).toBe(false);
    expect(meta.detail).toContain('不得编造');
  });

  it('有明细且非禁止态才允许渲染，原样返回数据', () => {
    const buckets = [{ bucketFrom: 0, bucketTo: 0.5, sampleCount: 3, meanConfidence: 0.4, accuracy: 0.33, gap: 0.07 }];
    const slices = [{ sliceId: 'OUTPATIENT', sampleCount: 3, brierScore: 0.2, accuracy: 0.33 }];
    const meta = calibrationEvidenceMeta(calibrationReport('THRESHOLD_PROPOSED', buckets, slices));
    expect(meta.renderable).toBe(true);
    expect(meta.buckets).toEqual(buckets);
    expect(meta.slices).toEqual(slices);
  });

  it('端点未返回数据时 fail-closed', () => {
    const meta = calibrationEvidenceMeta(null);
    expect(meta.renderable).toBe(false);
    expect(meta.label).toBe('无校准证据');
  });
});

describe('governanceWindow 是 UTC 日界单一真源', () => {
  it('窗口长度恰 30 天且严格小于 31 天端点上限', () => {
    const { from, to } = governanceWindow();
    expect(Number.isNaN(Date.parse(from))).toBe(false);
    expect(Number.isNaN(Date.parse(to))).toBe(false);
    const days = (Date.parse(to) - Date.parse(from)) / 86400000;
    expect(days).toBe(30);
    expect(days).toBeLessThan(31);
  });

  it('from/to 都落在 UTC 00:00 日界上', () => {
    const { from, to } = governanceWindow();
    expect(new Date(from).toISOString().slice(11)).toBe('00:00:00.000Z');
    expect(new Date(to).toISOString().slice(11)).toBe('00:00:00.000Z');
    expect(Date.parse(to)).toBeGreaterThan(Date.now());
  });

  it('负向探针：同一 UTC 日内多次调用必须逐字恒定，不得用滚动毫秒窗口', () => {
    const a = governanceWindow();
    const b = governanceWindow();
    expect(a).toEqual(b);
    expect(a.from).toBe(b.from);
    expect(a.to).toBe(b.to);
  });

  it('负向探针：窗口不覆盖 31 天以上（端点会拒绝）', () => {
    const { from, to } = governanceWindow();
    expect((Date.parse(to) - Date.parse(from)) / 86400000).toBeLessThanOrEqual(30);
  });

  it('负向探针：窗口为秒级 ISO，带毫秒会与后端 Instant 回显不再逐字一致', () => {
    const { from, to } = governanceWindow();
    expect(from).toMatch(/^\d{4}-\d{2}-\d{2}T00:00:00Z$/);
    expect(to).toMatch(/^\d{4}-\d{2}-\d{2}T00:00:00Z$/);
  });
});
