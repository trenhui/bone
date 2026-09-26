// 跨模块业务级冒烟：经 bone-gateway(:8888) + admin JWT，验证 6 大模块核心接口可用。
// 与 generator 专项 E2E 互补：这里覆盖 iam/system/masterdata/integration/metadata/extension
// 的读接口（证明各模块经网关契约对齐且能返回数据），并对 IAM 角色做「创建→查询→删除」写闭环。
//
// 演示账号: admin / 123456 (platform super admin, tenant 0)
// 运行: node e2e-modules-smoke.mjs

const GW = process.env.BONE_GATEWAY_URL || 'http://localhost:8888';
const results = [];
const step = (name, pass, detail = '') => {
  results.push({ name, pass, detail });
  console.log(`${pass ? '✅' : '❌'} ${name}${detail ? ' — ' + detail : ''}`);
};

async function jget(path, headers = {}) {
  const r = await fetch(GW + path, { headers: { Accept: 'application/json', ...headers } });
  const text = await r.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* ignore */ }
  return { status: r.status, json };
}
const jpost = async (path, body, headers = {}) => {
  const r = await fetch(GW + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers },
    body: JSON.stringify(body),
  });
  const text = await r.text();
  let json = null; try { json = text ? JSON.parse(text) : null; } catch {}
  return { status: r.status, json };
};
const jdel = async (path, headers = {}) => {
  const r = await fetch(GW + path, { method: 'DELETE', headers: { Accept: 'application/json', ...headers } });
  return { status: r.status, json: null };
};

const dataLen = (j) => {
  if (!j?.data) return '-';
  if (Array.isArray(j.data)) return j.data.length;
  if (j.data.records) return Array.isArray(j.data.records) ? j.data.records.length : '-';
  return Object.keys(j.data).length;
};
const checkRead = (label, res) => {
  const ok = res.status === 200 && (res.json?.success !== false);
  step(label, ok, `http=${res.status} success=${res.json?.success ?? '-'} code=${res.json?.code ?? '-'} dataLen=${dataLen(res.json)}`);
  return ok;
};

async function main() {
  // 登录
  const login = await jpost('/api/v1/iam/login', { username: 'admin', password: '123456' });
  const token = login.json?.data?.token;
  step('登录拿 JWT (网关)', login.status === 200 && !!token, `http=${login.status} tokenLen=${token?.length || 0}`);
  if (!token) { console.log(JSON.stringify(results)); process.exit(1); }
  const A = { Authorization: `Bearer ${token}` };
  const P = (p) => p + (p.includes('?') ? '&' : '?') + 'page=1&size=10';

  // ---- IAM ----
  checkRead('IAM 账号列表', await jget(P('/api/v1/iam/accounts'), A));
  checkRead('IAM 角色列表', await jget(P('/api/v1/iam/roles'), A));
  checkRead('IAM 权限列表', await jget(P('/api/v1/iam/permissions'), A));
  checkRead('IAM 菜单树', await jget('/api/v1/iam/menus/tree', A));
  checkRead('IAM 当前用户菜单', await jget('/api/v1/iam/menus/current', A));
  checkRead('IAM 租户列表', await jget(P('/api/v1/iam/tenants'), A));
  checkRead('IAM 审计日志', await jget(P('/api/v1/iam/audit/logs'), A));
  checkRead('IAM 当前用户(me)', await jget('/api/v1/iam/me', A));

  // ---- SYSTEM ----
  checkRead('SYS 配置分页', await jget(P('/api/v1/system/config/page'), A));
  checkRead('SYS 字典类型分页', await jget(P('/api/v1/system/dict/types/page'), A));
  checkRead('SYS 调度任务', await jget(P('/api/v1/system/schedule-tasks/page'), A));
  checkRead('SYS 操作日志', await jget(P('/api/v1/system/logs/page'), A));
  checkRead('SYS 告警规则', await jget(P('/api/v1/system/alert/rules/page'), A));
  checkRead('SYS 健康检查', await jget('/api/v1/system/health', A));

  // ---- MASTERDATA ----
  checkRead('MD 实体列表', await jget(P('/api/v1/masterdata/entities'), A));
  checkRead('MD 记录列表', await jget(P('/api/v1/masterdata/records'), A));
  checkRead('MD 质量规则', await jget(P('/api/v1/masterdata/quality/rules'), A));
  checkRead('MD 数据标准', await jget(P('/api/v1/masterdata/data-standards/page'), A));

  // ---- INTEGRATION ----
  checkRead('INT 连接器列表', await jget(P('/api/v1/integration/connectors'), A));
  checkRead('INT 流程列表', await jget(P('/api/v1/integration/flows'), A));
  checkRead('INT 统计', await jget('/api/v1/integration/statistics', A));

  // ---- METADATA ----
  const ent = await jget(P('/api/v1/metadata/entities'), A);
  checkRead('META 实体列表', ent);
  checkRead('META 关系列表', await jget(P('/api/v1/metadata/relationships'), A));
  checkRead('META 健康检查', await jget('/api/v1/metadata/health', A));
  // 链式：优先选已发布 RUNTIME 的实体读运行时记录（验证 runtime 路由 /api/v1/runtime/**）
  const entRecs = ent.json?.data?.records ?? ent.json?.data ?? [];
  const rtEnt = entRecs.find((e) => e.deliveryMode === 1 || e.deliveryMode === '1') || entRecs[0];
  const firstCode = rtEnt?.code;
  if (firstCode) {
    const rt = await jget(P(`/api/v1/runtime/entities/${firstCode}/records`), A);
    step('META 运行时记录(链式)', rt.status === 200, `http=${rt.status} entity=${firstCode} dataLen=${dataLen(rt.json)}`);
  } else {
    step('META 运行时记录(链式)', false, '无可用 entityCode，跳过');
  }

  // ---- EXTENSION ----
  checkRead('EXT 扩展点列表', await jget(P('/api/v1/extension/points'), A));
  checkRead('EXT 插件列表', await jget(P('/api/v1/extension/plugins'), A));
  checkRead('EXT 概览', await jget('/api/v1/extension/overview', A));
  checkRead('EXT 市场', await jget(P('/api/v1/extension/marketplace'), A));

  // ---- IAM 角色写闭环（创建→查询→删除）----
  const code = 'SMOKE_ROLE_' + Date.now().toString(36).toUpperCase();
  const create = await jpost('/api/v1/iam/roles', { name: '冒烟临时角色', code, description: 'e2e smoke', type: 1 }, A);
  const roleId = create.json?.data; // data 为新建 id 字符串（与 generator 同款约定）
  step('IAM 角色创建(POST)', create.status === 200 && !!roleId, `http=${create.status} id=${roleId}`);
  if (roleId) {
    const getOne = await jget(`/api/v1/iam/roles/${roleId}`, A);
    step('IAM 角色查询(GET /{id})', getOne.status === 200, `http=${getOne.status}`);
    const del = await jdel(`/api/v1/iam/roles/${roleId}`, A);
    step('IAM 角色删除(DELETE)', del.status === 200 || del.status === 204, `http=${del.status}`);
  }

  const passed = results.filter((r) => r.pass).length;
  console.log(`\n=== 跨模块冒烟: ${passed}/${results.length} 通过 ===`);
  if (passed !== results.length) console.log(JSON.stringify(results.filter((r) => !r.pass)));
  process.exit(passed === results.length ? 0 : 2);
}
main().catch((e) => { console.error('冒烟异常:', e); process.exit(3); });
