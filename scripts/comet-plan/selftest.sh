#!/bin/sh
# Self-contained discrimination test for the Comet Build Plan baseline gate.
# NO hardcoded baseline values: order / head / revision are read from the plan on
# the fly and mutations are derived from them, so a bumped revision or a new dev
# HEAD cannot silently turn a mutation into a no-op (a real "stale baseline" trap).
# Each change is tested FROM ITS OWN WORKTREE; inputs come from git, temp dir is
# created and cleaned up here -> no dependency on leftover /tmp artifacts.
# Usage: sh <repo>/scripts/comet-plan/selftest.sh
set -u
HOOKS=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CHANGES="synapclaim-task-assignment-workbench synapclaim-ai-config-governance-api synapclaim-independent-re-review synapclaim-inspection-handoff-timeout"
MAIN=$(git worktree list --porcelain | sed -n 's/^worktree //p' | head -1)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT INT TERM
PASS=0; FAIL=0

expect() { # expect <want_rc> <label> <input_file> <plan_path>
  want=$1; label=$2; input=$3; planpath=$4
  python3 "$HOOKS/verify-baseline.py" "$planpath" < "$input" >/dev/null 2>&1
  got=$?
  if [ "$got" -eq "$want" ]; then
    echo "  PASS  $label (rc=$got)"; PASS=$((PASS+1))
  else
    echo "  FAIL  $label (want=$want got=$got)"; FAIL=$((FAIL+1))
  fi
}

for c in $CHANGES; do
  f="docs/superpowers/plans/2026-11-15-$c.md"
  wt="$MAIN/.worktrees/$c"
  [ -d "$wt" ] || { echo "FAIL missing worktree $wt"; exit 1; }
  cd "$wt" || exit 1
  base="$TMP/$c.base.md"
  git show "HEAD:$f" > "$base" || { echo "FAIL cannot read HEAD:$f in $wt"; exit 1; }

  order=$(sed -n 's/^baseline-manifest-order: //p' "$base")
  headv=$(sed -n 's/^baseline-head: //p' "$base")
  rev=$(sed -n 's/^plan-revision: //p' "$base")
  [ -n "$order" ] && [ -n "$headv" ] && [ -n "$rev" ] || {
    echo "FAIL cannot parse frontmatter of $c"; exit 1; }
  zeros=$(printf '%s' "$headv" | sed 's/./0/g')
  echo "=== $c (order=$order head=$headv rev=$rev) ==="

  expect 0 "正向：已提交 plan 原样" "$base" "$f"

  sed "s/^baseline-manifest-order: $order\$/baseline-manifest-order: $((order+1))/" "$base" > "$TMP/m1.md"
  expect 1 "变异A：清单 order +1" "$TMP/m1.md" "$f"

  sed "s/^baseline-head: $headv\$/baseline-head: $zeros/" "$base" > "$TMP/m2.md"
  expect 1 "变异B：main HEAD sha 改全 0" "$TMP/m2.md" "$f"

  sed "s/^plan-revision: $rev\$/plan-revision: $((rev-1))/" "$base" > "$TMP/m3.md"
  expect 1 "变异C：revision 回退" "$TMP/m3.md" "$f"

  grep -v '^baseline-manifest-order:' "$base" > "$TMP/m4.md"
  expect 1 "变异D：删除 order 指纹" "$TMP/m4.md" "$f"

  # Complementary pair: identical body edit, with / without revision bump.
  # Proves the gate is not a degenerate "any change fails" rule.
  sed 's/^# Build Plan /# Build Plan (edited) /' "$base" > "$TMP/m5.md"
  expect 1 "变异E：改口径但未 bump revision" "$TMP/m5.md" "$f"

  sed -e 's/^# Build Plan /# Build Plan (edited) /' \
      -e "s/^plan-revision: $rev\$/plan-revision: $((rev+1))/" "$base" > "$TMP/m6.md"
  expect 0 "互补变异F：改口径且 bump revision" "$TMP/m6.md" "$f"

  expect 0 "还原：原文再次通过" "$base" "$f"
done

echo "=== SELFTEST SUMMARY PASS=$PASS FAIL=$FAIL ==="
[ "$FAIL" -eq 0 ] || exit 1
