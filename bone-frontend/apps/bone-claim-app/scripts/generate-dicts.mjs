// @ts-nocheck
/**
 * 受控码字典 codegen（单一真源：后端 GET /api/v1/claim-config/dicts）。
 *
 * 用法：
 *   node scripts/generate-dicts.mjs            # 用 dicts.snapshot.json 生成（默认，无需后端）
 *   DICT_API_BASE=http://localhost:8080 node scripts/generate-dicts.mjs   # 拉取实时端点并重新生成快照
 *
 * 输出：src/constants/dicts.generated.ts（code→label 映射，自动生成，禁止手改）。
 * 后端新增/改名受控码后，改后端 ClaimConfigApplicationService + ClaimDictLabels，再跑本脚本即可全量同步前端。
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const SNAPSHOT = join(ROOT, 'dicts.snapshot.json');
const OUT = join(ROOT, 'src/constants/dicts.generated.ts');

/** 分类键 → 生成常量名 */
const CONST_NAME = {
  claimStatus: 'CLAIM_STATUS_DICT',
  aiInvolvement: 'AI_INVOLVEMENT_DICT',
  aiRights: 'AI_RIGHTS_DICT',
  consumptionReason: 'CONSUMPTION_REASON_DICT',
  zeroReason: 'ZERO_REASON_DICT',
  rejectReason: 'REJECT_REASON_DICT',
  aiRejectReason: 'AI_REJECT_REASON_DICT',
  fraudGateReason: 'FRAUD_GATE_REASON_DICT',
  permission: 'PERMISSION_DICT',
};

/** 把「端点返回的分类（code/label 数组）」规整成 code→label 映射。 */
function normalizeCategories(categories) {
  const out = {};
  for (const [key, value] of Object.entries(categories)) {
    if (Array.isArray(value)) {
      const map = {};
      for (const item of value) map[item.code] = item.label;
      out[key] = map;
    } else {
      out[key] = value; // 快照已是 code→label 形态
    }
  }
  return out;
}

async function loadCategories() {
  const apiBase = process.env.DICT_API_BASE;
  if (apiBase) {
    const url = `${apiBase.replace(/\/$/, '')}/api/v1/claim-config/dicts`;
    const res = await fetch(url, { headers: { Accept: 'application/json' } });
    if (!res.ok) throw new Error(`GET ${url} -> ${res.status}`);
    const body = await res.json();
    const categories = body?.data?.categories ?? body?.categories;
    if (!categories) throw new Error('响应缺少 categories 字段');
    // 顺手刷新快照，便于提交
    writeFileSync(SNAPSHOT, JSON.stringify(categories, null, 2) + '\n', 'utf8');
    return normalizeCategories(categories);
  }
  const raw = JSON.parse(readFileSync(SNAPSHOT, 'utf8'));
  return normalizeCategories(raw);
}

function q(s) {
  return `'${String(s).replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;
}

function toLiteral(map) {
  const entries = Object.entries(map)
    .map(([k, v]) => `  ${q(k)}: ${q(v)}`)
    .join(',\n');
  return `{\n${entries}\n}`;
}

async function main() {
  const categories = await loadCategories();
  const lines = [
    '/* eslint-disable */',
    '/* AUTO-GENERATED from GET /api/v1/claim-config/dicts — do not edit by hand.',
    ' * Regenerate: `npm run gen:dicts`. Single source of truth = backend ClaimConfigApplicationService.',
    ' */',
    '',
  ];
  for (const [key, constName] of Object.entries(CONST_NAME)) {
    const map = categories[key] ?? {};
    lines.push(`export const ${constName}: Record<string, string> = ${toLiteral(map)};`);
    lines.push('');
  }
  writeFileSync(OUT, lines.join('\n'), 'utf8');
  console.log(`[generate-dicts] wrote ${OUT} (${Object.keys(categories).length} categories)`);
}

main().catch((err) => {
  console.error('[generate-dicts] failed:', err);
  process.exit(1);
});
