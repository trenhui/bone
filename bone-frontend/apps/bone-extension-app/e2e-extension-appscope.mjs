/**
 * 扩展管理端到端串联（5a 姊妹篇 流程 A/B + G1/G2/G4/G5）。
 *
 * 场景：营销中台（应用 #1001）为租户 T1001 定制促销计价，标准实现兜底，行业插件从市场安装。
 *
 * 覆盖用例：
 *   UC-EX-P1 平台登记扩展点 → UC-EX-D1/D3 开发实现+部署绑定 → UC-EX-T1 生效（publish-runtime）
 *   UC-EX-G1 观测（simulate + 执行日志） → UC-EX-G3 制品上传与回滚
 *   G2 租户码校验（DEFAULT/格式负例） → G5 市场安装免上传实例化 → G4 应用扩展视图
 *
 * 前置：extension-studio（新代码）已启动，默认 18088：
 *
 *   node e2e-extension-appscope.mjs
 *   BONE_EXTENSION_API_BASE=http://localhost:18088/api/v1/extension node e2e-extension-appscope.mjs
 */

const API_BASE =
  process.env.BONE_EXTENSION_API_BASE ?? 'http://localhost:18088/api/v1/extension';

const RUN_ID = Date.now().toString(36);
const APP_ID = 1001; // 营销中台
const TENANT_CODE = 'T1001'; // 租户编码（真实部署时由 iam_tenant 校验）

const result = { ok: false, scenario: `营销中台#${APP_ID} × 租户${TENANT_CODE} 促销计价`, steps: [] };

function step(name, pass, detail) {
  result.steps.push({ name, pass, detail });
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${name}  ${detail ?? ''}`);
  if (!pass) {
    throw new Error(`${name}: ${detail ?? ''}`);
  }
}

async function readJson(res) {
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

async function api(path, init) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    ...init,
  });
  return { res, body: await readJson(res) };
}

/** 最小 JAR（ZIP 本地文件头 PK\x03\x04 + 填充），通过 magic-number 校验 */
function fakeJar() {
  const head = Buffer.from([0x50, 0x4b, 0x03, 0x04]);
  const pad = Buffer.alloc(64, 0x61);
  return Buffer.concat([head, pad]);
}

async function main() {
  // ── 1. UC-EX-P1 平台登记扩展点（契约唯一入口） ──
  const iface = `com.bone.e2e.PricingExt_${RUN_ID}`;
  const { res: pointRes, body: pointBody } = await api('/points', {
    method: 'POST',
    body: JSON.stringify({ name: `促销计价扩展点(E2E_${RUN_ID})`, interfaceName: iface, enabled: true }),
  });
  const pointId = pointBody?.data?.id;
  step(
    'UC-EX-P1 登记扩展点',
    pointRes.status === 201 && !!pointId,
    `pointId=${pointId} iface=${iface}`,
  );

  // ── 2. UC-EX-D1/D3 标准实现（平台通用，兜底） ──
  const { res: stdRes, body: stdBody } = await api('/plugins', {
    method: 'POST',
    body: JSON.stringify({
      extPointId: pointId,
      name: `标准计价-兜底(E2E_${RUN_ID})`,
      className: `com.bone.e2e.impl.StandardPricing_${RUN_ID}`,
      tenantCode: '*',
      priority: 100,
      config: '{"defaultImpl":true}',
    }),
  });
  const stdId = stdBody?.data?.id;
  step('UC-EX-D1 标准实现（tenant=*，平台通用）', stdRes.status === 201 && !!stdId, `id=${stdId}`);

  const { res: stdDeploy } = await api(`/plugins/${stdId}:deploy?sync=true`, { method: 'POST' });
  step('UC-EX-D3 标准实现部署（sync 200）', stdDeploy.status === 200, `status=${stdDeploy.status}`);

  const { res: stdPub } = await api(`/plugins/${stdId}:publish-runtime`, { method: 'POST' });
  step('UC-EX-T1 标准实现生效（publish-runtime）', stdPub.status === 200, `status=${stdPub.status}`);

  // ── 3. 租户定制实现（应用归属 + 租户维度，LRO 部署） ──
  const { res: tRes, body: tBody } = await api('/plugins', {
    method: 'POST',
    body: JSON.stringify({
      extPointId: pointId,
      name: `T1001 促销计价(E2E_${RUN_ID})`,
      className: `com.bone.e2e.impl.TenantPricing_${RUN_ID}`,
      tenantCode: TENANT_CODE,
      appId: APP_ID,
      priority: 10,
      config: '{"traffic":100}',
    }),
  });
  const tenantId = tBody?.data?.id;
  step(
    'UC-EX-D1 租户定制实现（tenant=T1001，appId=1001）',
    tRes.status === 201 && tBody?.data?.appId === APP_ID,
    `id=${tenantId} appId=${tBody?.data?.appId}`,
  );

  const { res: tDeploy, body: tDeployBody } = await api(`/plugins/${tenantId}:deploy`, {
    method: 'POST',
  });
  // 202 = LRO（lro.deploy-enabled 且非 sync-by-default）；200 = 同步部署（in-memory 默认）
  const operationId = tDeployBody?.data?.operationId;
  step(
    'UC-EX-D3 租户实现部署（202 LRO 或 200 同步）',
    (tDeploy.status === 202 && !!operationId) || (tDeploy.status === 200 && tDeployBody?.success === true),
    `status=${tDeploy.status} op=${operationId ?? '-'}`,
  );
  if (operationId) {
    let done = false;
    for (let i = 0; i < 40 && !done; i++) {
      const poll = await api(`/operations/${operationId}`);
      done = poll.body?.data?.done;
      if (!done) await new Promise((r) => setTimeout(r, 100));
    }
    step('UC-EX-D3 LRO 轮询完成', done, 'operations done');
  }
  const { res: tPub } = await api(`/plugins/${tenantId}:publish-runtime`, { method: 'POST' });
  step('UC-EX-T1 租户实现生效', tPub.status === 200, `status=${tPub.status}`);

  // ── 4. G2 租户码校验（负例） ──
  const { res: badLegacy } = await api('/plugins', {
    method: 'POST',
    body: JSON.stringify({
      extPointId: pointId,
      name: '遗留占位',
      className: `com.bone.e2e.impl.Bad_${RUN_ID}a`,
      tenantCode: 'DEFAULT',
    }),
  });
  step('G2 遗留 DEFAULT 被拒（400）', badLegacy.status === 400, `status=${badLegacy.status}`);

  const { res: badFormat } = await api('/plugins', {
    method: 'POST',
    body: JSON.stringify({
      extPointId: pointId,
      name: '非法格式',
      className: `com.bone.e2e.impl.Bad_${RUN_ID}b`,
      tenantCode: 'bad code!',
    }),
  });
  step('G2 非法格式被拒（400）', badFormat.status === 400, `status=${badFormat.status}`);

  // ── 5. G5 市场安装（免上传实例化 + 应用归属） ──
  const { res: instRes, body: instBody } = await api('/marketplace/discount.fixed-amount:install', {
    method: 'POST',
    body: JSON.stringify({ extPointId: pointId, appId: APP_ID }),
  });
  const installedId = instBody?.data?.pluginId;
  step(
    'G5 市场安装写归属（appId=1001）',
    instRes.status === 200 && instBody?.data?.appId === APP_ID && !!installedId,
    `pluginId=${installedId}`,
  );

  // ── 6. G4 应用扩展视图 ──
  const view = await api(`/plugins?appId=${APP_ID}`);
  const viewRows = Array.isArray(view.body?.data) ? view.body.data : [];
  const viewIds = viewRows.map((r) => Number(r.id));
  step(
    'G4 应用扩展视图（租户实现+市场插件在列，标准实现不在）',
    viewIds.includes(Number(tenantId)) && viewIds.includes(Number(installedId)) && !viewIds.includes(Number(stdId)),
    `rows=[${viewIds.join(',')}]`,
  );

  // ── 7. UC-EX-G1 观测：模拟调用 + 执行日志 ──
  const sim = await api(`/plugins/${stdId}:simulate`, { method: 'POST' });
  step('UC-EX-G1 模拟调用', sim.res.status === 200, `status=${sim.res.status}`);
  const logs = await api('/execution-logs?limit=50');
  const logRecords = logs.body?.data?.records ?? [];
  const hasInvoke = logRecords.some(
    (r) => Number(r.pluginId) === Number(stdId) && ['INVOKE', 'SUCCESS'].includes(r.status ?? r.action ?? ''),
  );
  step('UC-EX-G1 执行日志可观测', logRecords.length > 0 && hasInvoke, `records=${logRecords.length}`);

  // ── 8. UC-EX-D2/G3 制品上传 + 版本回滚 ──
  const jar = fakeJar();
  async function upload(version) {
    const form = new FormData();
    form.append('file', new Blob([jar], { type: 'application/java-archive' }), `pricing-${version}.jar`);
    form.append('pluginId', String(stdId));
    form.append('name', stdBody?.data?.name ?? `标准计价(E2E_${RUN_ID})`);
    form.append('className', stdBody?.data?.className ?? `com.bone.e2e.impl.StandardPricing_${RUN_ID}`);
    form.append('version', version);
    return fetch(`${API_BASE}/plugins:upload`, { method: 'POST', body: form });
  }
  const up1 = await upload('1.0.0');
  const up2 = await upload('1.0.1');
  step(
    'UC-EX-D2 制品上传 v1.0.0 / v1.0.1（201）',
    up1.status === 201 && up2.status === 201,
    `v1=${up1.status} v2=${up2.status}`,
  );

  const rb = await api(`/plugins/${stdId}:rollback`, { method: 'POST' });
  const versions = await api(`/plugins/${stdId}/versions`);
  const activeVersion = (versions.body?.data ?? []).find((v) => v.active)?.version;
  step(
    'UC-EX-G3 回滚到 v1.0.0',
    rb.res.status === 200 && activeVersion === '1.0.0',
    `active=${activeVersion}`,
  );

  // ── 9. 归属治理：PATCH 清除/恢复归属 ──
  const clear = await api(`/plugins/${tenantId}`, {
    method: 'PATCH',
    body: JSON.stringify({ appId: null }),
  });
  step('G1 PATCH 清除归属（appId→null=平台通用）', clear.res.status === 200 && clear.body?.data?.appId == null, `appId=${clear.body?.data?.appId ?? 'null'}`);
  const restore = await api(`/plugins/${tenantId}`, {
    method: 'PATCH',
    body: JSON.stringify({ appId: APP_ID }),
  });
  step('G1 PATCH 恢复归属', restore.res.status === 200 && restore.body?.data?.appId === APP_ID, `appId=${restore.body?.data?.appId}`);

  result.ok = true;
  console.log('\n=== E2E 通过 ===');
  console.log(JSON.stringify(result, null, 2));
}

main().catch((e) => {
  result.error = String(e.message ?? e);
  console.log('\n=== E2E 失败 ===');
  console.log(JSON.stringify(result, null, 2));
  process.exit(1);
});
