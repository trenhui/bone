#!/bin/bash

# BONE 前端应用重启脚本
# 兼容bash 3.2

echo "=========================================="
echo "  BONE 前端应用重启脚本"
echo "=========================================="
echo ""

# 创建日志目录
mkdir -p logs

# 应用列表
apps=("bone-shell" "bone-iam-app" "bone-metadata-app" "bone-masterdata-app" "bone-integration-app" "bone-system-app" "bone-extension-app")
ports=("3000" "3003" "3004" "3005" "3006" "3007" "3008")

# 停止所有应用
echo "正在停止所有应用..."
echo "=========================================="

for i in 0 1 2 3 4 5 6; do
  app=${apps[$i]}
  port=${ports[$i]}
  pid_file="logs/$app.pid"
  
  # 1. 停止通过脚本启动的进程
  if [ -f "$pid_file" ]; then
    pid=$(cat "$pid_file")
    echo "停止 $app (PID: $pid)..."
    kill $pid 2>/dev/null || true
    sleep 1
    # 强制终止
    if ps -p $pid > /dev/null 2>&1; then
      kill -9 $pid 2>/dev/null || true
    fi
    rm -f "$pid_file"
    echo "$app 已停止"
  else
    echo "$app 未通过脚本运行"
  fi
  
  # 2. 检查端口是否被占用
  echo "检查端口 $port 是否被占用..."
  lsof -i :$port > /dev/null 2>&1
  if [ $? -eq 0 ]; then
    # 找到占用端口的进程
    occupied_pid=$(lsof -i :$port | grep LISTEN | awk '{print $2}')
    if [ -n "$occupied_pid" ]; then
      echo "端口 $port 被进程 $occupied_pid 占用，正在停止..."
      kill $occupied_pid 2>/dev/null || true
      sleep 1
      # 强制终止
      if ps -p $occupied_pid > /dev/null 2>&1; then
        kill -9 $occupied_pid 2>/dev/null || true
      fi
      echo "端口 $port 已释放"
    fi
  else
    echo "端口 $port 未被占用"
  fi
done

echo "=========================================="
echo "所有应用已停止"
echo ""

# 启动所有应用
echo "正在启动所有应用..."
echo "=========================================="

for i in 0 1 2 3 4 5 6; do
  app=${apps[$i]}
  port=${ports[$i]}
  
  echo "启动 $app (端口: $port)..."
  (cd apps/$app && npm run dev > ../../logs/$app.log 2>&1 &)
  echo "$!" > logs/$app.pid
  sleep 2
done

echo "=========================================="
echo "所有应用已启动！"
echo "访问地址: http://localhost:3000"
echo "查看日志: tail -f logs/*.log"
echo "=========================================="
