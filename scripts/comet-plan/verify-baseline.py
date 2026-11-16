#!/usr/bin/env python3
"""Fail-fast provenance check: staged plan frontmatter vs MAIN worktree HEAD.

Consumes EVERY baseline fingerprint declared in plan frontmatter, so no field is
"configured but never read" (project rule: two config sources for one meaning = fake green).

  baseline-head            machine-checked  == main dev HEAD short sha
  baseline-manifest-order  machine-checked  == main dev HEAD manifest max(order)
  plan-revision            machine-checked  > HEAD revision when plan content changed
  baseline-gate            ADVISORY ONLY, deliberately NOT machine-checked:
                           re-running ./scripts/check.sh costs ~13 min and has side
                           effects. It is a human-review snapshot, never a gate result.
                           Printed explicitly so nobody mistakes it for a live verdict.

IMPORTANT: pre-commit injects GIT_DIR / GIT_WORK_TREE / GIT_INDEX_FILE. Those win over
`git -C <main>` and would silently read the LINKED worktree HEAD (stale manifest).
All repo-resolving GIT_* vars are stripped for every subprocess; reads are anchored
with explicit -C paths instead (main tree for provenance, cwd for the plan's HEAD copy).
"""
import json, os, re, subprocess, sys

UNSAFE_GIT_ENV = (
    'GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_COMMON_DIR',
    'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES',
    'GIT_PREFIX', 'GIT_NAMESPACE', 'GIT_CEILING_DIRECTORIES', 'GIT_DISCOVERY_ACROSS_FILESYSTEM',
)


def fail(msg):
    print('[plan-verify]   FAIL ' + msg)
    sys.exit(1)


def clean_env():
    env = dict(os.environ)
    for key in UNSAFE_GIT_ENV:
        env.pop(key, None)
    return env


def field(text, name, pattern=r'(\S+)'):
    m = re.search(r'^%s:\s*%s\s*$' % (re.escape(name), pattern), text, re.M)
    if not m:
        fail('plan 缺 %s frontmatter（指纹字段必须有消费方）' % name)
    return m.group(1)


def git(args, cwd):
    return subprocess.check_output(['git', '-C', cwd] + args, text=True, env=env,
                                   stderr=subprocess.DEVNULL)


env = clean_env()
plan_path = sys.argv[1] if len(sys.argv) > 1 else None
if not plan_path:
    fail('用法：verify-baseline.py <staged-plan-path>  (pre-commit 必须传 $f)')
text = sys.stdin.read()  # staged snapshot, NEVER the working tree

declared_head = field(text, 'baseline-head', r'([0-9a-f]{7,40})')
declared_order = int(field(text, 'baseline-manifest-order', r'(\d+)'))
declared_revision = int(field(text, 'plan-revision', r'(\d+)'))
declared_gate = field(text, 'baseline-gate')

cwd = os.getcwd()
try:
    listing = git(['worktree', 'list', '--porcelain'], cwd).splitlines()
    main = next(ln[len('worktree '):] for ln in listing if ln.startswith('worktree '))
    head = git(['rev-parse', 'HEAD'], main).strip()
    raw = git(['show', 'HEAD:scripts/migration/migration-manifest.json'], main)
    actual_order = max(e['order'] for e in json.loads(raw)['entries']
                       if isinstance(e.get('order'), int))
except Exception as exc:
    fail('无法读取主树清单: %s' % exc)

# 1/3 main HEAD sha provenance
if not head.startswith(declared_head):
    fail('baseline-head=%s 与主树 dev HEAD=%s 不符；禁止照抄旧基线' %
         (declared_head, head[:9]))

# 2/3 manifest order provenance
if declared_order != actual_order:
    fail('基线漂移：plan 声明 baseline-manifest-order=%d，但主树 HEAD 清单 max(order)=%d；'
         '请更新 plan 后再提交，禁止照抄旧基线' % (declared_order, actual_order))

# 3/3 revision monotonicity: a changed plan MUST bump its revision
try:
    previous = git(['show', 'HEAD:' + plan_path], cwd)
    prev_rev = int(field(previous, 'plan-revision', r'(\d+)'))
    content_changed = previous != text
except Exception:
    prev_rev, content_changed = None, True  # new file
if prev_rev is not None:
    if declared_revision < prev_rev:
        fail('plan-revision 回退：HEAD=%d → 暂存=%d；已提交口径不得静默回退' %
             (prev_rev, declared_revision))
    if content_changed and declared_revision <= prev_rev:
        fail('plan 内容已改但 plan-revision 仍为 %d（HEAD=%d）；改口径必须 bump revision' %
             (declared_revision, prev_rev))

print('[plan-verify]   baseline OK 4/4 指纹均有消费方：'
      'baseline-head=%s MATCH / manifest-order=%d MATCH / plan-revision=%d (HEAD=%s, changed=%s)'
      % (declared_head, declared_order, declared_revision,
         prev_rev if prev_rev is not None else 'new', content_changed))
print('[plan-verify]   ADVISORY baseline-gate=%s 仅为人工复核快照，**未机检**；'
      '开工前须重跑 ./scripts/check.sh（exit 0 + 末行「✅ 本地自检通过！」）' % declared_gate)
print('[plan-verify]   provenance: main=%s HEAD=%s, GIT_* stripped' % (main, head[:9]))
