#!/usr/bin/env bash
# 生成对外 Java SDK（ADR-0040 第 11 项）。
#
# 契约唯一真源 = doc/architecture/openapi/*-v1.yaml（8 份、142 个操作）。
# 生成物是**构建产物**，输出到 target/sdk/（已被 .gitignore 忽略），不进版本库。
#
# 关键约束（均为踩坑所得，改动前先读）：
#   1. 本脚本**必须联网**（首次要下载 openapi-generator-cli + jar）。仓库日常构建走
#      `mvn -o`离线，故不可塞进 check.sh / ci-check.sh，只能显式调用。
#   2. 生成器 jar 版本必须钉死（JAR_VERSION）。实测 7.14.0 可生成成功；
#      不钉死则"昨天能生成今天不能"无法归因。
#      注意 npm 包 `openapi-generator-cli` 与它下载的 jar 是**两套版本号**：
#      npm 包只到 2.x，jar 是 7.x，写混会 ETARGET（已踩）。
#   3. 每份规范单独生成到 target/sdk/<artifactId>/，8 个独立 artifact + 各自 pom，
#      不合并成一个 jar —— 各域鉴权与生命周期不同，混一起调用方无法只取所需依赖。
#   4. 包名规范：com.bone.sdk.<domain>.api / .model，invoker 在 com.bone.sdk.<domain>。
#      <domain> = 规范名去掉 -v1（blueprint-orders → blueprintorders）。
#   5. servers.url 全是相对路径（/api/v1/xxx），无 host ⇒ **调用方必须在构造
#      ApiClient 时显式给 basePath**，否则运行时才炸。这是规范侧待办，不在本脚本修。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC_DIR="$ROOT/doc/architecture/openapi"
OUT_ROOT="$ROOT/target/sdk"
TOOL_DIR="$ROOT/target/sdk/.tool"

# 两个版本必须分别钉死：npm 包 @openapitools/openapi-generator-cli 的版本号与它下载的
# **jar（真正的生成器）版本号是两套编号**，写混会得到 ETARGET（实测踩过：
# `openapi-generator-cli@7.14.0` 不存在，该 npm 包只到 2.x）。
CLI_NPM_VERSION="${CLI_NPM_VERSION:-2.15.0}"   # npm 包装包版本
JAR_VERSION="${JAR_VERSION:-7.14.0}"           # openapi-generator jar 版本
CLI_SPEC="@openapitools/openapi-generator-cli@${CLI_NPM_VERSION}"
GROUP_ID="com.bone.sdk"

# 规范文件 → artifactId / java 包名片段。
# **包名片段必须等于规范文件名去掉 -v1 再把 '-' 去掉**：
# sdk-contract-smoke-manifest.py 按这条规则从规范名推导包名，两边不一致时
# 烟测会以 ClassNotFoundException 报错（实测踩过：这里多写了一个 t）。
# 顺序即生成顺序；新增规范必须同时在此登记。
SPECS=(
  "iam-v1:iam"
  "blueprint-orders-v1:blueprintorders"
  "masterdata-v1:masterdata"
  "metadata-runtime-v1:metadataruntime"
  "integration-v1:integration"
  "extension-v1:extension"
  "generator-v1:generator"
  "console-v1:console"
)

log() { printf '\033[36m[sdk]\033[0m %s\n' "$*"; }
die() { printf '\033[31m[sdk] FAIL:\033[0m %s\n' "$*" >&2; exit 1; }

command -v node >/dev/null || die "需要 node（npm 生态的 openapi-generator-cli 是官方分发的 jar 包装）"
[ -x "$(command -v java)" ] || die "需要 JRE/JDK 17（openapi-generator 要求 11+）"

mkdir -p "$OUT_ROOT" "$TOOL_DIR"
# jar 缓存目录固定在仓库内，避免每次重新下载 ~90MB；并把 jar 版本钉进配置，
# 否则 npm 包升级会静默换掉生成器版本（"昨天能生成今天不能"无法归因）。
cat >"$TOOL_DIR/openapitools.json" <<JSON
{ "generator-cli": { "storageDir": "$TOOL_DIR", "version": "$JAR_VERSION" } }
JSON

cd "$TOOL_DIR"
if [ ! -d node_modules/@openapitools/openapi-generator-cli ]; then
  log "安装 openapi-generator-cli@${CLI_NPM_VERSION}（首次较慢）…"
  npm install --silent --no-audit --no-fund --loglevel=error "$CLI_SPEC"
fi

actual="$(npx --no-install openapi-generator-cli version 2>/dev/null | tail -1)"
[ "$actual" = "$JAR_VERSION" ] \
  || die "生成器 jar 版本不符：期望 ${JAR_VERSION}，实际 ${actual}"
log "生成器 jar ${actual}（npm 包 ${CLI_NPM_VERSION}）就绪"

generated=0
for entry in "${SPECS[@]}"; do
  spec="${entry%%:*}"
  domain="${entry##*:}"
  file="$SPEC_DIR/${spec}.yaml"
  [ -f "$file" ] || die "规范缺失：$file"
  artifact="bone-sdk-${domain}"
  out="$OUT_ROOT/$artifact"

  log "生成 $artifact ← ${spec}.yaml"
  rm -rf "$out"
  if ! npx --no-install openapi-generator-cli generate \
      --input-spec "$file" \
      --generator-name java \
      --library okhttp-gson \
      --output "$out" \
      --api-package "com.bone.sdk.${domain}.api" \
      --model-package "com.bone.sdk.${domain}.model" \
      --invoker-package "com.bone.sdk.${domain}" \
      --group-id "$GROUP_ID" \
      --artifact-id "$artifact" \
      --artifact-version 1.0.0 \
      --additional-properties="hideGenerationTimestamp=true,openApiNullable=false,serializationLibrary=gson,disallowAdditionalPropertiesIfNotPresent=false,useJakartaEe=true" \
      >"$TOOL_DIR/${domain}.log" 2>&1; then
    tail -30 "$TOOL_DIR/${domain}.log" >&2
    die "$artifact 生成失败，日志：$TOOL_DIR/${domain}.log"
  fi

  # 生成器自检：spec 校验失败时它会非 0 退出，但 warning 级问题只落日志。
  # 这里额外断言「至少产出了 1 个 Api 类」，防"生成成功但产物为空"假绿。
  api_count=$(find "$out/src/main/java" -path "*/api/*Api.java" 2>/dev/null | wc -l | tr -d ' ')
  model_count=$(find "$out/src/main/java" -path "*/model/*.java" ! -name 'AbstractOpenApiSchema.java' 2>/dev/null | wc -l | tr -d ' ')
  [ "$api_count" -ge 1 ] || die "$artifact 未产出任何 Api 类（假绿），日志：$TOOL_DIR/${domain}.log"
  log "  → Api 类 $api_count 个 / model 类 $model_count 个"
  generated=$((generated + 1))
done

[ "$generated" -eq "${#SPECS[@]}" ] || die "只生成了 $generated/${#SPECS[@]} 个"
log "完成：$generated 个 artifact 位于 ${OUT_ROOT#"$ROOT"/}"
log "下一步：scripts/sdk-contract-smoke-test.sh（每个 tag 至少一次真实调用断言 HTTP 码）"