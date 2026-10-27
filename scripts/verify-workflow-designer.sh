#!/usr/bin/env bash
#
# 工作流设计器「前后端联调」一键验证。
#
# 三层各管一段，缺一层就会漏一类缺陷：
#   1) 后端真实 HTTP 联调（内嵌 Tomcat + H2，不用 MockMvc）
#        → 传输层：Content-Type / 路径 / 审计头 / UTF-8 往返 / 各状态码
#        → 语义层：草稿可脏、发布 fail-closed、revision 乐观并发、上下线、试运行分支
#   2) 前端接口契约测试（真实 axios + 假 adapter）
#        → 客户端发出的方法/路径/请求体/头，与后端签名逐项对齐
#        → 非 2xx 被拦截器 reject 后，错误信封仍能解出 status/message/violations
#   3) 前端类型检查（页面消费这些类型，签名不符不会被 IDE 自动发现）
#
# 用法：bash scripts/verify-workflow-designer.sh
# 退出码：任一环失败即非 0。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Users/renhui.trh/tools/jdk/jdk-21.0.5.jdk/Contents/Home}"
MVN="${MVN:-$HOME/.m2/wrapper/dists/apache-maven-3.6.3-bin/1iopthnavndlasol9gbrbg6bf2/apache-maven-3.6.3/bin/mvn}"
STARTER_POM="bone-engine/bone-workflow-engine/bone-workflow-spring-boot-starter/pom.xml"

green() { printf '\033[32m%s\033[0m\n' "$1"; }
red() { printf '\033[31m%s\033[0m\n' "$1"; }
step() { printf '\n\033[36m[%s]\033[0m %s\n' "$1" "$2"; }

command -v node >/dev/null || { red "缺少 node"; exit 1; }

step 1/3 "后端真实 HTTP 联调（内嵌 Tomcat + H2）"
# -Dspotless.apply.skip：验证不应顺手改写工作区文件（否则 diff 会被格式化污染）
if ! (cd "$REPO_ROOT" && "$MVN" -f "$STARTER_POM" -o \
      -Dspotless.apply.skip=true -Dspotless.check.skip=true \
      test -Dtest=WorkflowDesignerHttpIntegrationTest); then
  red "后端联调测试失败"
  exit 1
fi
green "后端联调通过"

step 2/3 "前端接口契约测试（真实 axios + 假 adapter）"
if ! (cd "$REPO_ROOT/bone-frontend" && ./node_modules/.bin/vitest run \
      apps/bone-system-app/src/services/workflowApi.test.ts); then
  red "前端契约测试失败"
  exit 1
fi
green "前端契约测试通过"

step 3/3 "前端类型检查（bone-system-app）"
if ! (cd "$REPO_ROOT/bone-frontend" && ./node_modules/.bin/tsc --noEmit -p apps/bone-system-app/tsconfig.json); then
  red "类型检查失败"
  exit 1
fi
green "类型检查通过"

printf '\n'
green "工作流设计器联调验证全部通过"
