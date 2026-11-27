#!/usr/bin/env bash
# 新 change 的 build check：真实编译 synap-claim 模块（复用已交付代码，无新增实现）。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
MVN="${MVN:-/Users/renhui.trh/java/apache-maven-3.9.6/bin/mvn}"
[ -x "$MVN" ] || MVN=mvn
echo "[build] 编译 synap-claim-app 及依赖模块（离线）"
JAVA_HOME="$JAVA_HOME" "$MVN" -o -q -pl bone-apps/synap-claim/synap-claim-app -am -DskipTests compile
echo "BUILD_CHECK_ALL_OK"
