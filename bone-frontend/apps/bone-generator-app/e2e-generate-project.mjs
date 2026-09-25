/**
 * Generator 端到端冒烟 + 生成「板板工程」验证脚本。
 *
 * 模拟用户操作：选数据源 → 选已同步表 → 选模板 → 生成(PHYSICAL_DB 同步) → 轮询状态 → 下载 zip → 校验结构。
 * 并额外验证新收敛的 CATALOG_SNAPSHOT 入口（统一 /code-generation）。
 *
 * 用法：
 *   node e2e-generate-project.mjs
 *   BONE_GENERATOR_API_BASE=http://localhost:8086/api/v1/generator BONE_TENANT=0 node e2e-generate-project.mjs
 */
import { execSync } from 'node:child_process';
import { writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const API_BASE = process.env.BONE_GENERATOR_API_BASE ?? 'http://localhost:8086/api/v1/generator';
const TENANT = process.env.BONE_TENANT ?? '0';
const DS_ID = process.env.BONE_DS_ID ?? '758291694243282944';
const OUT_DIR = process.env.BONE_E2E_OUT ?? tmpdir();

const HEADERS = { 'X-Tenant-Id': TENANT, 'Content-Type': 'application/json' };

const result = { ok: false, steps: [] };
function step(name, pass, detail) {
  result.steps.push({ name, pass, detail });
  console.log(`${pass ? '✅' : '❌'} ${name} — ${detail}`);
  if (!pass) throw new Error(`${name}: ${detail}`);
}

async function api(method, path, body) {
  const res = await fetch(API_BASE + path, {
    method,
    headers: HEADERS,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text };
  }
  return { status: res.status, json };
}

function unzipEntries(zipPath) {
  const out = execSync(`unzip -l "${zipPath}"`, { encoding: 'utf8' });
  return out
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('Archive') && !l.startsWith('Length') && !/^-+\s*$/.test(l) && !l.includes('files') && !l.startsWith('----'))
    .map((l) => l.replace(/^\d+\s+\d+\s+\d+\s+/, '').trim())
    .filter((l) => l && l !== '' && !/^\d+\s+files?$/.test(l));
}

async function downloadAndVerify(taskId, projectName, label) {
  const dl = await fetch(`${API_BASE}/code-generation/tasks/${taskId}/download`, { headers: HEADERS });
  step(`下载 ${label} 产物`, dl.status === 200 || dl.status === 206, `status=${dl.status}`);
  const buf = Buffer.from(await dl.arrayBuffer());
  step(`产物非空 (${label})`, buf.length > 0, `${buf.length} bytes`);
  const zipPath = join(OUT_DIR, `${projectName}-${label}.zip`);
  writeFileSync(zipPath, buf);
  const entries = unzipEntries(zipPath);
  const javaFiles = entries.filter((e) => e.endsWith('.java'));
  step(`zip 结构合理 (${label})`, entries.length > 0 && javaFiles.length > 0,
    `entries=${entries.length}, .java=${javaFiles.length}`);
  console.log(`   📦 ${zipPath}`);
  console.log('   样例: ' + javaFiles.slice(0, 5).join(', '));
  return { zipPath, entries, javaFiles };
}

async function generate(taskIdLabel, body) {
  const create = await api('POST', '/code-generation?sync=true', body);
  step(`创建生成任务 (${taskIdLabel})`, create.status === 200 && typeof create.json.data === 'string',
    `status=${create.status}, taskId=${create.json.data}`);
  const taskId = create.json.data;
  const statusRes = await api('GET', `/code-generation/tasks/${taskId}/status`);
  const status = statusRes.json.data;
  step(`生成完成 (${taskIdLabel})`, status === 'SUCCESS' || status === 'COMPLETED', `status=${status}`);
  return taskId;
}

async function main() {
  // 1) 数据源 & 模板
  const dsRes = await api('GET', `/data-sources?page=1&size=10`);
  step('GET /data-sources', dsRes.status === 200 && dsRes.json.success === true, `status=${dsRes.status}`);
  const dsRecords = dsRes.json.data.records;
  const ds = dsRecords.find((d) => d.id === DS_ID) ?? dsRecords[0];
  step('定位数据源', !!ds, `id=${ds?.id}, name=${ds?.name}`);

  const tplRes = await api('GET', `/templates?page=1&size=50`);
  step('GET /templates', tplRes.status === 200 && tplRes.json.success === true, `status=${tplRes.status}`);
  const byType = {};
  for (const t of tplRes.json.data.records) byType[t.type] = t.id;
  const needed = ['entity', 'repository', 'response', 'applicationService', 'controller'];
  const missing = needed.filter((t) => !byType[t]);
  step('模板齐全', missing.length === 0, missing.length ? `缺: ${missing}` : `entity=${byType.entity}`);
  const templateIds = needed.map((t) => Number(byType[t]));

  // 2) 已同步表
  const synced = await api('GET', `/data-sources/${ds.id}/synced-tables`);
  step('GET /synced-tables', synced.status === 200 && Array.isArray(synced.json.data), `status=${synced.status}`);
  const tableName = synced.json.data.find((t) => t.tableName === 'bone_module')?.tableName
    ?? synced.json.data[0]?.tableName;
  step('选定同步表', !!tableName, `table=${tableName}`);

  // 3) PHYSICAL_DB 生成「板板工程」
  const physicalTask = await generate('PHYSICAL_DB', {
    projectName: 'BoneBoardDemo',
    basePackage: 'com.bone.demo',
    moduleName: 'board',
    dataSourceId: ds.id,
    tableNames: [tableName],
    templateIds,
    metadataSource: 'PHYSICAL_DB',
  });
  await downloadAndVerify(physicalTask, 'BoneBoardDemo', 'physical');

  // 4) CATALOG_SNAPSHOT 统一入口（新收敛特性）
  const snapRes = await api('GET', `/metadata-entity-snapshots?page=1&size=5`);
  step('GET /metadata-entity-snapshots', snapRes.status === 200 && snapRes.json.success === true, `status=${snapRes.status}`);
  const entityCode = snapRes.json.data.records[0]?.tableName;
  if (entityCode) {
    const catalogTask = await generate('CATALOG_SNAPSHOT', {
      projectName: 'BoneCatalogDemo',
      basePackage: 'com.bone.catalog',
      moduleName: 'catalog',
      dataSourceId: ds.id,
      templateIds,
      metadataSource: 'CATALOG_SNAPSHOT',
      entityCodes: [entityCode],
    });
    await downloadAndVerify(catalogTask, 'BoneCatalogDemo', 'catalog');
  } else {
    console.log('⚠️  无已发布快照，跳过 CATALOG_SNAPSHOT 验证');
  }

  result.ok = true;
  console.log('\n==== E2E 结果: 全部通过 ====');
  console.log(JSON.stringify(result, null, 2));
}

main().catch((e) => {
  result.error = String(e.message ?? e);
  console.log('\n==== E2E 结果: 失败 ====');
  console.log(JSON.stringify(result, null, 2));
  process.exit(1);
});
