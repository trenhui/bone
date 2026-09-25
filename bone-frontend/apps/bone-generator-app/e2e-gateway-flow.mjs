// 网关级 UI 流程 E2E：模拟前端经 bone-gateway(:8888) 走 generator 全链路。
// 与 e2e-generate-project.mjs 的区别：本脚本不直连 8086，而是走网关，
// 验证 ① 登录拿 JWT ② 网关鉴权(401) ③ 客户端伪造 X-Tenant-Id 被剥离 ④ 全链路生成可用。
//
// 演示账号来自 bone-init.sql (BONE_IAM_DEMO_PASSWORD_ACK)：admin / 123456 (platform, tenant 0)
//
// 运行: node e2e-gateway-flow.mjs

const GW = process.env.BONE_GATEWAY_URL || 'http://localhost:8888';
const results = [];
const step = (name, pass, detail = '') => {
  results.push({ name, pass, detail });
  console.log(`${pass ? '✅' : '❌'} ${name}${detail ? ' — ' + detail : ''}`);
};

const jget = (path, headers = {}) =>
  fetch(GW + path, { headers: { Accept: 'application/json', ...headers } });
const jpost = (path, body, headers = {}) =>
  fetch(GW + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers },
    body: JSON.stringify(body),
  });

async function jsonOrThrow(res) {
  const text = await res.text();
  try {
    return { status: res.status, json: text ? JSON.parse(text) : null };
  } catch {
    return { status: res.status, json: null, raw: text.slice(0, 300) };
  }
}

async function main() {
  // 1) 登录拿 JWT（网关白名单 /api/v1/iam/login）
  const loginRes = await jpost('/api/v1/iam/login', { username: 'admin', password: '123456' });
  const login = await jsonOrThrow(loginRes);
  const token = login.json?.data?.token;
  step('登录拿 JWT (经网关 /iam/login)', login.status === 200 && !!token, `status=${login.status} tokenLen=${token?.length || 0}`);
  if (!token) {
    console.log('\n汇总:', JSON.stringify(results));
    process.exit(1);
  }
  const AUTH = { Authorization: `Bearer ${token}` };

  // 2) 无 token 访问受保护接口 → 期望 401（网关拦截）
  const noAuth = await jget('/api/v1/generator/data-sources');
  step('无 token 访问被网关拦截 (401)', noAuth.status === 401, `status=${noAuth.status}`);

  // 3) 安全校验：带 Bearer + 伪造客户端 X-Tenant-Id:999 → 网关必须剥离并以 JWT 的 tenantId(0) 为准
  const forged = await jget('/api/v1/generator/data-sources?page=1&size=10', {
    ...AUTH,
    'X-Tenant-Id': '999',
  });
  const forgedJ = await jsonOrThrow(forged);
  const recs = forgedJ.json?.data?.records ?? [];
  const hasLocalBone = recs.some((r) => r.name === 'local-bone' || String(r.id) === '758291694243282944');
  step(
    '伪造 X-Tenant-Id:999 被剥离（返回 tenant0 的 local-bone）',
    forged.status === 200 && hasLocalBone,
    `status=${forged.status} 命中local-bone=${hasLocalBone} (若网关未剥离会查 tenant999 而空)`
  );

  // 4) capabilities（经网关）
  const cap = await jsonOrThrow(await jget('/api/v1/generator/capabilities', AUTH));
  const names = cap.json?.data?.map?.((c) => c.name) ?? [];
  step('网关 capabilities（收敛入口）', cap.status === 200 && names.includes('createCodeGeneration') && !names.includes('generateCode'), `status=${cap.status} names=${names.join(',')}`);

  // 5) 选数据源（tenant0 的 local-bone）
  const dsRes = await jsonOrThrow(await jget('/api/v1/generator/data-sources?page=1&size=10', AUTH));
  const ds = (dsRes.json?.data?.records ?? []).find((r) => String(r.id) === '758291694243282944') || (dsRes.json?.data?.records ?? [])[0];
  step('网关列出数据源并选中 local-bone', dsRes.status === 200 && !!ds, `status=${dsRes.status} dsId=${ds?.id}`);

  // 6) 选模板（5 个内置）
  const tplRes = await jsonOrThrow(await jget('/api/v1/generator/templates?page=1&size=50', AUTH));
  const tpls = tplRes.json?.data?.records ?? [];
  step('网关列出模板（>=5）', tplRes.status === 200 && tpls.length >= 5, `status=${tplRes.status} cnt=${tpls.length}`);
  const templateIds = tpls.slice(0, 5).map((t) => Number(t.id));

  // 7) 生成（sync，经网关，dataSourceId 以字符串传递避免 JS 精度丢失）
  const genBody = {
    projectName: 'BoneBoardDemo-GW',
    basePackage: 'com.bone.demo',
    moduleName: 'board',
    dataSourceId: String(ds.id),
    tableNames: ['bone_module'],
    templateIds,
    metadataSource: 'PHYSICAL_DB',
  };
  const genRes = await jpost('/api/v1/generator/code-generation?sync=true', genBody, AUTH);
  const gen = await jsonOrThrow(genRes);
  const taskId = gen.json?.data; // 网关/直连均返回 data 为 taskId 字符串
  step('网关创建生成任务 (PHYSICAL_DB)', gen.status === 200 && typeof taskId === 'string' && !!taskId, `status=${gen.status} taskId=${taskId || gen.json?.message}`);

  // 8) 轮询状态
  let status = '';
  for (let i = 0; i < 20 && taskId; i++) {
    const s = await jsonOrThrow(await jget(`/api/v1/generator/code-generation/tasks/${taskId}/status`, AUTH));
    status = s.json?.data ?? '';
    if (status === 'SUCCESS' || status === 'COMPLETED' || status === 'FAILED') break;
    await new Promise((r) => setTimeout(r, 1000));
  }
  step('网关生成完成', status === 'SUCCESS' || status === 'COMPLETED', `status=${status}`);

  // 9) 下载产物（经网关）
  if (taskId) {
    const dl = await fetch(GW + `/api/v1/generator/code-generation/tasks/${taskId}/download`, { headers: AUTH });
    const buf = Buffer.from(await dl.arrayBuffer());
    const ok = dl.status === 200 && buf.length > 0;
    const javaCount = (buf.toString('latin1').match(/\.java/g) || []).length;
    step('网关下载板板工程 zip', ok, `http=${dl.status} bytes=${buf.length} .java出现次数=${javaCount}`);
  }

  const passed = results.filter((r) => r.pass).length;
  console.log(`\n=== 网关级 E2E: ${passed}/${results.length} 通过 ===`);
  console.log(JSON.stringify(results));
  process.exit(passed === results.length ? 0 : 2);
}

main().catch((e) => {
  console.error('E2E 异常:', e);
  process.exit(3);
});
