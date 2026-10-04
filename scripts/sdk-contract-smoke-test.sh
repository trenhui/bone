#!/usr/bin/env bash
# 对外 Java SDK 的契约烟测（ADR-0040 第 14 项）。
#
# 为什么需要它：ADR-0040 的核心教训是「声明存在 ≠ 能力到位」——曾经两个 SDK 模块
# 空转约一年无人发现（只有 IDE 模板 Main.java）。生成物同理："生成成功"不等于"能调用"。
# 本脚本对**每个 (domain, tag)** 至少真发一次 HTTP 并断言 200 + Bearer 认证头。
#
# 用独立 javac 而非 mvn：仓库日常构建走 `mvn -o` 离线，而生成物是联网产物；
# 把烟测挂进 reactor 会让离线构建依赖联网产物。classpath 从 ~/.m2 取
# okhttp / okio / gson / kotlin-stdlib / jsr305（生成物 pom 声明的运行时依赖）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="$ROOT/target/sdk"
BUILD="$ROOT/target/sdk-smoke"
SRC="$ROOT/scripts/sdk/SdkContractSmoke.java"
M2="${M2_REPO:-$HOME/.m2/repository}"

log() { printf '\033[36m[smoke]\033[0m %s\n' "$*"; }
die() { printf '\033[31m[smoke] FAIL:\033[0m %s\n' "$*" >&2; exit 1; }

[ -d "$SDK_ROOT" ] || die "生成物不存在，先跑 scripts/generate-sdk.sh"
command -v javac >/dev/null || die "需要 JDK（javac）"

# 清单从规范真源推导（不是从生成物推导），故每次都重新生成，避免清单陈旧
log "生成清单（源：doc/architecture/openapi/*.yaml）"
python3 "$ROOT/scripts/sdk-contract-smoke-manifest.py"

# ── 组classpath ────────────────────────────────────────────────────────────
pick() { # pick <groupPath> <artifactId> <version>
  local f="$M2/$1/$2/$3/$2-$3.jar"
  [ -f "$f" ] || f="$M2/$1/$2/$3/$2-$3-jvm.jar"
  [ -f "$f" ] || f=""
  printf '%s' "$f"
}
# gson-fire 是 okhttp-gson 生成器的硬依赖（JSON.java 直接 import io.gsonfire），
# 但它**不在任何 Bone 模块的依赖树里** ⇒ `mvn -o` 的本地仓库常常没有它。
# 缺了就联网取一次（与本脚本"联网产物"的定位一致）。
# 必须在一个与本仓库无关的空目录里跑 mvn：否则 mvn 会先读当前目录的 pom.xml，
# 被bone-parent 里那个无法解析的 spring-cloud-dependenciesimport POM 直接卡死，
# 报出来的是"[ERROR] Non-resolvable import POM"——看着像网络问题，实际是 cwd 陷阱。
FETCH_DIR="$BUILD/.m2fetch"
ensure() { # ensure <groupPath> <artifactId> <version>
  [ -n "$(pick "$1" "$2" "$3")" ] && return 0
  log "本地仓库缺 $2:$3，联网取一次…"
  mkdir -p "$FETCH_DIR"
  (cd "$FETCH_DIR" && mvn -q -B dependency:get \
      -Dartifact="$(echo "$1" | tr '/' '.'):$2:$3") \
    || die "无法取得 $2:$3（网络或镜像不可达）"
  [ -n "$(pick "$1" "$2" "$3")" ] || die "mvn 取到了但本地仓库里仍找不到 $2:$3"
}
mkdir -p "$BUILD"
ensure com/squareup/okhttp3 okhttp 4.12.0
ensure com/squareup/okhttp3 logging-interceptor 4.12.0
ensure com/squareup/okio okio-jvm 3.6.0
ensure com/google/code/gson gson 2.10.1
ensure io/gsonfire gson-fire 1.9.0
ensure org/jetbrains/kotlin kotlin-stdlib 1.9.23
ensure jakarta/annotation jakarta.annotation-api 2.1.1
ensure com/google/code/findbugs jsr305 3.0.2

CP="$(pick com/squareup/okhttp3 okhttp 4.12.0)"
CP="$CP:$(pick com/squareup/okhttp3 logging-interceptor 4.12.0)"
CP="$CP:$(pick com/squareup/okio okio-jvm 3.6.0)"
CP="$CP:$(pick com/google/code/gson gson 2.10.1)"
CP="$CP:$(pick io/gsonfire gson-fire 1.9.0)"
CP="$CP:$(pick org/jetbrains/kotlin kotlin-stdlib 1.9.23)"
CP="$CP:$(pick jakarta/annotation jakarta.annotation-api 2.1.1)"
CP="$CP:$(pick com/google/code/findbugs jsr305 3.0.2)"

# ── 编译生成物 ──────────────────────────────────────────────────────────────
rm -rf "$BUILD/classes"
mkdir -p "$BUILD/classes"
find "$SDK_ROOT" -path '*/src/main/java/*.java' >"$BUILD/sources.txt"
src_count=$(wc -l <"$BUILD/sources.txt" | tr -d ' ')
[ "$src_count" -gt 0 ] || die "没找到任何生成源码"
log "编译 $src_count 个生成源文件…"
javac -nowarn -encoding UTF-8 -proc:none -cp "$CP" -d "$BUILD/classes" \
  @"$BUILD/sources.txt" 2>"$BUILD/javac-sdk.log" || {
  tail -40 "$BUILD/javac-sdk.log" >&2
  die "生成物编译失败（日志：target/sdk-smoke/javac-sdk.log）"
}

log "编译烟测驱动器…"
javac -nowarn -encoding UTF-8 -proc:none -cp "$CP:$BUILD/classes" -d "$BUILD/classes" "$SRC"

# ── 运行 ────────────────────────────────────────────────────────────────────
log "起本地 stub server，逐 tag 真调用…"
set +e
java -cp "$CP:$BUILD/classes" com.bone.smoke.SdkContractSmoke "$SDK_ROOT/smoke-manifest.tsv" 2>&1 | tee "$BUILD/smoke.log"
exit_code=${PIPESTATUS[0]}
set -e
[ "$exit_code" -eq 0 ] || die "契约烟测未通过（详见 target/sdk-smoke/smoke.log）"
log "完成"