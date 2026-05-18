/**
 * Generator API 冒烟（经 Gateway 或直连 studio-generator）。
 *
 *   npm run e2e:api
 *   BONE_GENERATOR_API_BASE=http://localhost:8085/api/v1/generator npm run e2e:api
 */

const API_BASE =
  process.env.BONE_GENERATOR_API_BASE ?? 'http://localhost:8888/api/v1/generator';

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
  const listRes = await fetch(`${API_BASE}/data-sources?page=1&size=5`);
  const list = await readJson(listRes);
  step(
    'GET /data-sources',
    listRes.ok && list.success === true && Array.isArray(list.data?.records ?? list.data?.list),
    `status=${listRes.status}`,
  );

  const capRes = await fetch(`${API_BASE}/capabilities`);
  const cap = await readJson(capRes);
  step('GET /capabilities', capRes.ok && cap.success === true, `status=${capRes.status}`);

  result.ok = true;
  console.log(JSON.stringify(result, null, 2));
}

main().catch((e) => {
  result.error = String(e.message ?? e);
  console.log(JSON.stringify(result, null, 2));
  process.exit(1);
});
