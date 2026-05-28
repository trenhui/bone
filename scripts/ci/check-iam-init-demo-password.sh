#!/usr/bin/env bash
# 校验 bone-init.sql 中演示管理员弱口令哈希须带显式 ACK 标记（详设 IAM-14 / backlog default-password-ci-gate）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
INIT_SQL="${ROOT}/bone-init.sql"

KNOWN_DEMO_BCRYPT='$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG'
ACK_MARKER='BONE_IAM_DEMO_PASSWORD_ACK'

if grep -Fq "$KNOWN_DEMO_BCRYPT" "$INIT_SQL"; then
  if ! grep -Fq "$ACK_MARKER" "$INIT_SQL"; then
    echo "[check-iam-init] FAIL: bone-init.sql contains known demo admin password hash" >&2
    echo "  but missing comment marker: $ACK_MARKER" >&2
    echo "  Add: -- $ACK_MARKER: admin default password is 123456 (dev only)" >&2
    exit 1
  fi
fi

if grep -E "INSERT INTO iam_account[^;]*'123456'" "$INIT_SQL"; then
  echo "[check-iam-init] FAIL: plaintext password 123456 found in iam_account INSERT" >&2
  exit 1
fi

echo "[check-iam-init] OK: demo password policy satisfied"
