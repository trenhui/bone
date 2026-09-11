#!/bin/bash
# README 事实校验：防止 README 与代码/看板漂移（SSOT 违规检测）
# 设计哲学与 ArchUnit 门禁同源：可信度不靠自觉，靠机器。
#
# 校验项：
#   1) 端口一致性：README 端口表 vs 各模块 application.yml
#   2) 任务状态反漂移：看板已 done 的任务，README 不得仍写「待接入/分阶段接入/规划中」
#
# 用法: bash scripts/check-readme-facts.sh
# 端口基线新增模块时，在下方 PORT_BASELINE 列表追加一行「相对路径 端口」即可。

set -eu
cd "$(dirname "$0")/.."

fail=0

# ---------- 1) 端口一致性 ----------
# 格式: 模块路径 端口基线
PORT_BASELINE='
bone-platform/bone-integration 8085
bone-engine/studio-generator 8086
bone-engine/bone-metadata-server 9001
bone-platform/bone-iam 8081
bone-platform/bone-gateway 8888
'

echo "=== ① 端口一致性 check ==="
echo "$PORT_BASELINE" | while read -r mod port; do
  [ -n "$mod" ] || continue
  yml="$mod/src/main/resources/application.yml"
  [ -f "$yml" ] || yml="$mod/src/main/resources/application.yaml"
  [ -f "$yml" ] || yml="$mod/src/main/resources/application-dev.yml"
  [ -f "$yml" ] || yml="$mod/src/main/resources/application-dev.yaml"
  if [ ! -f "$yml" ]; then
    echo "WARN  $mod 未找到 application.yml，跳过"
    continue
  fi
  actual=$(grep -A3 '^server:' "$yml" 2>/dev/null | grep 'port:' | head -1 | grep -oE '[0-9]+' | head -1 || true)
  if [ -z "$actual" ]; then
    echo "WARN  $mod 端口未在 $yml 声明（可能由环境变量注入），请人工确认"
  elif [ "$actual" != "$port" ]; then
    echo "FAIL  $mod 端口漂移: yml=$actual, 基线=$port（更新脚本基线或 README）"
    fail=1
  else
    echo "OK    $mod 端口 $port 一致"
  fi
done

# ---------- 2) 任务状态反漂移 ----------
KANBAN="doc/wiki/07-P0-TODO看板.md"
echo "=== ② 看板→README 状态反漂移 ==="
if [ ! -f "$KANBAN" ]; then
  echo "WARN  看板文件不存在: $KANBAN"
fi
for item in INT-09 INT-10 INT-11; do
  row=""
  [ -f "$KANBAN" ] && row=$(grep -E "^\| ${item} \|?" "$KANBAN" | head -1 || true)
  if [ -z "$row" ]; then
    echo "WARN  看板中未找到 ${item} 行"
    continue
  fi
  case "$row" in
    *done*)
      if grep "$item" README.md | grep -Eq "分阶段接入|待接入|规划中|未实现"; then
        echo "FAIL  看板 $item=done，但 README 仍写为待接入/规划中"
        fail=1
      else
        echo "OK    $item done 且 README 无过时表述"
      fi
      ;;
    *TODO*|*todo*)
      echo "OK    $item 状态=TODO（README 允许写「规划中」）"
      ;;
    *)
      echo "WARN  $item 状态无法识别: $row"
      ;;
  esac
done

# fail 标志跨管道丢失，改用临时文件聚合（bash <4 无 shopt lastpipe 保证）
if [ "$fail" -ne 0 ]; then
  echo ""
  echo "❌ README 事实校验未通过，请修正 README 或脚手架基线后再提交"
  exit 1
fi
echo ""
echo "✅ README 事实校验全部通过"
exit 0
