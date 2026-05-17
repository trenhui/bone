/**
 * 扩展 API 冒烟（经 Gateway 或直连 Studio）。
 *
 * 前置：bone-gateway:8888 + extension-studio:8088，或仅 Studio（设置 BONE_EXTENSION_API_BASE）。
 *
 *   node e2e-extension-api.mjs
 *   BONE_EXTENSION_API_BASE=http://localhost:8088/api/v1/extension node e2e-extension-api.mjs
 */

const API_BASE =
  process.env.BONE_EXTENSION_API_BASE ?? 'http://localhost:8888/api/v1/extension';

const result = { ok: false, steps: [] };

function step(name, pass, detail) {
  result.steps.push({ name, pass, detail });
  if (!pass) {
    throw new Error(`${name}: ${detail}`);
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

async function main() {
  const overviewRes = await fetch(`${API_BASE}/overview`);
  const overview = await readJson(overviewRes);
  step('GET /overview', overviewRes.ok && overview.success === true, `status=${overviewRes.status}`);

  const syncRes = await fetch(`${API_BASE}/plugins/1:deploy?sync=true`, { method: 'POST' });
  const syncBody = await readJson(syncRes);
  step(
    'POST :deploy?sync=true',
    syncRes.status === 200 && syncBody.success === true,
    `status=${syncRes.status}`,
  );

  const asyncRes = await fetch(`${API_BASE}/plugins/1:deploy`, { method: 'POST' });
  const asyncBody = await readJson(asyncRes);
  const location = asyncRes.headers.get('location') ?? '';
  const operationId =
    asyncBody?.data?.operationId ??
    (location ? location.replace(/.*\//, '') : null);
  step(
    'POST :deploy LRO 202',
    asyncRes.status === 202 && !!operationId,
    `status=${asyncRes.status} operationId=${operationId}`,
  );

  let done = false;
  for (let i = 0; i < 40 && operationId; i++) {
    const pollRes = await fetch(`${API_BASE}/operations/${operationId}`);
    const poll = await readJson(pollRes);
    if (poll?.data?.done) {
      done = poll.data.error == null;
      step('GET /operations poll', done, `progress=${poll.data.progress}`);
      break;
    }
    await new Promise((r) => setTimeout(r, 100));
  }
  if (!done) {
    step('GET /operations poll', false, 'timeout');
  }

  result.ok = true;
  console.log(JSON.stringify(result, null, 2));
}

main().catch((e) => {
  result.error = String(e.message ?? e);
  console.log(JSON.stringify(result, null, 2));
  process.exit(1);
});
