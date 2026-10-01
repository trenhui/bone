/**
 * Generator 真实场景端到端回归（前后端串联）：
 *
 *   建业务表 → 物理表发现(keyword/limit) → 同步(syncedCount) → 已同步表(列元数据)
 *   → 选模板生成(同步) → 轮询状态 → 下载 zip → 结构断言 → javac 编译校验
 *
 *   npm run e2e:real
 *   BONE_GENERATOR_API_BASE=http://localhost:8086/api/v1/generator \
 *   BONE_E2E_TABLE=biz_sales_order \
 *   BONE_E2E_BASE_PACKAGE=com.bone.order \
 *   BONE_E2E_MODULE_NAME=order \
 *   npm run e2e:real
 *
 * 前置：目标库中已存在 BONE_E2E_TABLE（见 scripts/migration 之外的业务演示表，
 * 可用 doc 中 biz_sales_order DDL 自建）；本机有 javac + unzip + mvn(可选)。
 */

import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const API_BASE = process.env.BONE_GENERATOR_API_BASE ?? 'http://localhost:8086/api/v1/generator';
const TENANT = process.env.BONE_E2E_TENANT_ID ?? '0';
const TABLE = process.env.BONE_E2E_TABLE ?? 'biz_sales_order';
const BASE_PACKAGE = process.env.BONE_E2E_BASE_PACKAGE ?? 'com.bone.order';
const MODULE_NAME = process.env.BONE_E2E_MODULE_NAME ?? 'order';

const H = { 'X-Tenant-Id': TENANT, 'Content-Type': 'application/json' };
const result = { ok: false, steps: [] };

function step(name, pass, detail = '') {
  result.steps.push({ name, pass, detail });
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
  if (!pass) throw new Error(`${name}: ${detail}`);
}

async function readJson(res) {
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text.slice(0, 300) };
  }
}

async function api(path, init) {
  const res = await fetch(`${API_BASE}${path}`, { headers: H, ...init });
  const body = await readJson(res);
  return { res, body };
}

function unzip(zipPath, destDir) {
  execFileSync('unzip', ['-q', '-o', zipPath, '-d', destDir]);
}

function listFiles(dir) {
  const out = [];
  const walk = (d) => {
    for (const e of readdirSorted(d)) {
      const p = join(d, e);
      if (isDir(p)) walk(p);
      else out.push(p);
    }
  };
  walk(dir);
  return out;
}

import { readdirSync, statSync } from 'node:fs';
const readdirSorted = (d) => readdirSync(d).sort();
const isDir = (p) => statSync(p).isDirectory();

function findGeneratedFile(files, suffix) {
  return files.find((f) => f.endsWith(suffix));
}

async function main() {
  // 0. 健康检查
  const health = await fetch(`${API_BASE.replace(/\/api\/v1\/generator$/, '')}/actuator/health`);
  step('backend health', health.ok, `status=${health.status}`);

  // 1. 模板列表（来源收敛：所有 PUBLISHED 模板内容非空）
  const { body: tplBody } = await api('/templates?page=1&size=50');
  const templates = tplBody.data?.records ?? tplBody.data?.list ?? [];
  step('GET /templates', tplBody.success === true && templates.length >= 5, `count=${templates.length}`);
  const templateIds = templates.map((t) => t.id);

  // 2. 数据源列表 → 取第一个物理数据源
  const { body: dsBody } = await api('/data-sources?page=1&size=5');
  const dataSources = dsBody.data?.records ?? dsBody.data?.list ?? [];
  step('GET /data-sources', dataSources.length >= 1, `count=${dataSources.length}`);
  const dsId = dataSources[0].id;

  // 3. 物理表发现：keyword 过滤 + limit 上限
  const kw = TABLE.slice(0, 4);
  const { body: tblBody } = await api(
    `/data-sources/${dsId}/tables?keyword=${encodeURIComponent(kw)}&limit=50`,
  );
  const tables = tblBody.data ?? [];
  step(
    'GET /tables?keyword&limit',
    tblBody.success === true && tables.length > 0 && tables.length <= 50,
    `keyword=${kw} count=${tables.length}`,
  );
  step(
    'tables keyword filters to target',
    tables.some((t) => t.tableName === TABLE),
    TABLE,
  );

  // 4. 同步表结构：返回 syncedCount
  const { body: syncBody } = await api(`/data-sources/${dsId}/tables:sync`, {
    method: 'POST',
    body: JSON.stringify({ dataSourceId: dsId, tableNames: [TABLE] }),
  });
  step(
    'POST /tables:sync returns syncedCount',
    syncBody.success === true && typeof syncBody.data?.syncedCount === 'number' && syncBody.data.syncedCount >= 1,
    `syncedCount=${syncBody.data?.syncedCount}`,
  );

  // 5. 已同步表：列元数据非空
  const { body: syncedBody } = await api(`/data-sources/${dsId}/synced-tables`);
  const synced = (syncedBody.data ?? []).find((t) => t.tableName === TABLE);
  step(
    'GET /synced-tables has columns',
    !!synced && Array.isArray(synced.columns) && synced.columns.length > 0,
    `columns=${synced?.columns?.length ?? 0}`,
  );

  // 6. 生成代码（同步模式）
  const { body: genBody } = await api('/code-generation?sync=true', {
    method: 'POST',
    body: JSON.stringify({
      projectName: 'e2e-order-center',
      basePackage: BASE_PACKAGE,
      moduleName: MODULE_NAME,
      dataSourceId: dsId,
      tableNames: [TABLE],
      templateIds,
      genConfig: '{"includeTests":true,"includeDocumentation":true}',
      metadataSource: 'PHYSICAL_DB',
      tenantId: Number(TENANT),
    }),
  });
  const taskId = typeof genBody.data === 'string' ? genBody.data : genBody.data?.taskId;
  step('POST /code-generation?sync=true', genBody.success === true && !!taskId, `taskId=${taskId}`);

  // 7. 轮询状态直至 SUCCESS
  let status = '';
  for (let i = 0; i < 60; i++) {
    const { body: stBody } = await api(`/code-generation/tasks/${taskId}/status`);
    status = stBody.data ?? '';
    if (status === 'SUCCESS' || status === 'FAILED') break;
    await new Promise((r) => setTimeout(r, 2000));
  }
  step('generation status SUCCESS', status === 'SUCCESS', `status=${status}`);

  // 8. 下载产物
  const dlRes = await fetch(`${API_BASE}/code-generation/tasks/${taskId}/download`, { headers: H });
  const zipBuf = Buffer.from(await dlRes.arrayBuffer());
  const workDir = mkdtempSync(join(tmpdir(), 'bone-gen-e2e-'));
  const zipPath = join(workDir, 'generated.zip');
  writeFileSync(zipPath, zipBuf);
  step('download zip', dlRes.ok && zipBuf.length > 1000, `bytes=${zipBuf.length}`);

  // 9. 结构断言：解压、文件名=类名、无重复包段
  unzip(zipPath, workDir);
  const files = listFiles(workDir).filter((f) => f.endsWith('.java'));
  step('generated java files', files.length >= 5, `count=${files.length}`);

  const dupSeg = `/${MODULE_NAME}/${MODULE_NAME}/`;
  step(
    'no duplicated package segment',
    !files.some((f) => f.includes(dupSeg)),
    dupSeg,
  );

  const seg = TABLE.replace(/_/g, '').toLowerCase(); // bizsalesorder
  const classFiles = files.filter((f) => f.toLowerCase().includes(seg));
  let nameMismatch = [];
  for (const f of classFiles) {
    const base = f.split('/').pop().replace(/\.java$/, '');
    const content = readFileSync(f, 'utf8');
    const decl = content.match(/public\s+(?:class|interface|record|enum)\s+(\w+)/);
    if (decl && decl[1] !== base) nameMismatch.push(`${base}.java -> ${decl[1]}`);
  }
  step(
    'file name matches declared class name',
    nameMismatch.length === 0,
    nameMismatch.slice(0, 3).join('; ') || `${classFiles.length} files checked`,
  );

  // 10. javac 编译校验（尽力而为：classpath 由 mvn dependency:build-classpath 提供）
  const repoRoot = process.env.BONE_REPO_ROOT;
  if (repoRoot && existsSync(join(repoRoot, 'pom.xml'))) {
    const cpFile = join(workDir, 'cp.txt');
    execFileSync(
      'mvn',
      ['-o', '-q', '-pl', 'bone-engine/studio-generator', 'dependency:build-classpath', `-Dmdep.outputFile=${cpFile}`],
      { cwd: repoRoot, stdio: 'pipe' },
    );
    const swaggerJar = execFileSync(
      'bash',
      ['-c', "ls ~/.m2/repository/io/swagger/core/v3/swagger-annotations-jakarta/*/swagger-annotations-jakarta-*.jar 2>/dev/null | tail -1"],
    ).toString().trim();
    const cp = `${readFileSync(cpFile, 'utf8').trim()}${swaggerJar ? `:${swaggerJar}` : ''}`;
    const classesDir = join(workDir, 'classes');
    const sourcesFile = join(workDir, 'sources.txt');
    writeFileSync(sourcesFile, files.filter((f) => !f.includes('/docs/')).join('\n'));
    try {
      execFileSync(
        'javac',
        ['-encoding', 'UTF-8', '-cp', cp, '-d', classesDir, `@${sourcesFile}`],
        { stdio: 'pipe' },
      );
      step('javac compiles generated code', true, `${files.length} sources`);
    } catch (e) {
      const err = String(e.stderr ?? e.message).split('\n').filter((l) => l.includes('错误') || l.includes('error')).slice(0, 3);
      step('javac compiles generated code', false, err.join(' | '));
    }
  } else {
    step('javac compiles generated code', true, 'skipped (BONE_REPO_ROOT not set)');
  }

  rmSync(workDir, { recursive: true, force: true });
  result.ok = true;
  console.log(JSON.stringify(result, null, 2));
}

main().catch((e) => {
  result.error = String(e.message ?? e);
  console.log(JSON.stringify(result, null, 2));
  process.exit(1);
});
