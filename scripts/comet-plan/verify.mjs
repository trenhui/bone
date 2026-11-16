import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

// Resolve the Comet classic-tasks API from the INSTALLED CLI, never from a
// machine-specific absolute path (that path made the gate vanish on a new host).
// Explicit override: COMET_CLASSIC_TASKS_JS=/path/to/classic-tasks.js
function resolveClassicTasks() {
  const rel = '@rpamis/comet/dist/domains/comet-classic/classic-tasks.js';
  const candidates = [];
  if (process.env.COMET_CLASSIC_TASKS_JS) candidates.push(process.env.COMET_CLASSIC_TASKS_JS);
  try {
    const bin = execFileSync('sh', ['-c', 'command -v comet'], { encoding: 'utf8' }).trim();
    const real = execFileSync('python3',
      ['-c', 'import os,sys;print(os.path.realpath(sys.argv[1]))', bin],
      { encoding: 'utf8' }).trim();
    const marker = '/lib/node_modules/';
    const i = real.indexOf(marker);
    if (i >= 0) candidates.push(path.join(real.slice(0, i), marker, rel));
  } catch (_) { /* fall through to well-known locations */ }
  candidates.push('/opt/homebrew/lib/node_modules/' + rel);
  candidates.push('/usr/local/lib/node_modules/' + rel);
  for (const c of candidates) {
    if (c && fs.existsSync(c)) return c;
  }
  console.error('[plan-verify]   FAIL 无法定位 Comet classic-tasks.js；'
    + '请设置 COMET_CLASSIC_TASKS_JS 指向该文件（不依赖机器固定路径）');
  process.exit(1);
}
const { parseClassicTasks, validateClassicTaskPlan } =
  await import('file://' + resolveClassicTasks());

// Both inputs MUST be staged snapshots. Previously tasks.md came from the working
// tree via fs.readFileSync: an unstaged tasks.md edit could make a stale plan pass.
// Deliberate asymmetry with verify-baseline.py: HERE we KEEP the hook's GIT_DIR,
// because `git show :<path>` must read THIS worktree's index (stripping GIT_DIR
// would silently fall back to the main worktree index).
const planPath = process.argv[2];
// Scoped hook (default) reads staged snapshots; the always-on audit passes HEAD
// so it can verify committed plans while other files are staged.
const refPrefix = process.argv[3] === 'HEAD' ? 'HEAD:' : ':';
const change = path.basename(planPath, '.md').replace(/^\d{4}-\d{2}-\d{2}-/, '');
const tasksRef = `openspec/changes/${change}/tasks.md`;

function staged(blob) {
  try {
    return execFileSync('git', ['show', `${refPrefix}${blob}`], { encoding: 'utf8' });
  } catch (err) {
    console.error(`[plan-verify]   FAIL 无法读取暂存快照 ${blob}（未入库或路径错误）`);
    process.exit(1);
  }
}

const tasksText = staged(tasksRef);
const plan = fs.readFileSync(0, 'utf8');
const parsed = parseClassicTasks(tasksText);
const result = validateClassicTaskPlan(plan, tasksRef, parsed);
if (result !== 'canonical') {
  console.error(`[plan-verify]   FAIL ${change}: plan mapping = ${result}`);
  process.exit(1);
}
console.log(`[plan-verify]   canonical OK ${change} (${parsed.length} tasks referenced; plan=${process.argv[3] === 'HEAD' ? 'HEAD' : 'staged'} tasks.md=${refPrefix} snapshot)`);
